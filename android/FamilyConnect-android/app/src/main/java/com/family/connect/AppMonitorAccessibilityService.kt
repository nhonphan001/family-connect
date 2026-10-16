package com.family.connect

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import android.util.Base64
import android.view.accessibility.AccessibilityEvent
import java.io.ByteArrayOutputStream

/**
 * Lắng nghe sự kiện chuyển ứng dụng (typeWindowStateChanged) để:
 *  1) Ghi nhận app nào đang mở (đối chiếu với danh sách chặn từ dashboard).
 *  2) Nếu app nằm trong danh sách chặn HOẶC đã hết giờ trong ngày -> đưa con
 *     về màn hình chính (Home) thay vì mở được app đó, kèm thông báo giải thích.
 *  3) Cảnh báo trước khi sắp hết giờ (còn ~5 phút), chỉ báo 1 lần/ngày.
 *  4) Chụp ảnh màn hình khi ba mẹ bấm yêu cầu từ dashboard.
 *  5) Hiện thông báo chúc mừng khi ba mẹ thưởng thêm thời gian.
 *
 * Lưu ý quan trọng: dịch vụ này CHỈ đọc tên gói ứng dụng đang mở
 * (packageName) trong lúc hoạt động bình thường — KHÔNG tự ý đọc nội dung
 * màn hình, tin nhắn hay hình ảnh; việc chụp màn hình chỉ diễn ra khi phụ
 * huynh chủ động yêu cầu.
 */
class AppMonitorAccessibilityService : AccessibilityService() {

    private var blockedPackages: Set<String> = emptySet()
    private var dailyLimitMinutes: Int = Int.MAX_VALUE
    private var bonusMinutesToday: Int = 0
    private var appLimits: Map<String, Int> = emptyMap()
    private var locationEnabled: Boolean = false
    private var lastHandledScreenshotRequest = 0L
    private var lastOwnAppForegroundTime = 0L
    private var lastGoHomeTime = 0L
    private var lastOverLimitNoticeTime = 0L
    private var lastBlockedAppNoticeTime = 0L
    private var lastAppLimitNoticeTime = 0L

    /**
     * Package(s) của launcher/màn hình chính trên máy. Trước đây launcher KHÔNG
     * được coi là ngoại lệ như "chính app của mình", nên mỗi sự kiện cửa sổ từ
     * launcher (kể cả khi bé đang mở ngăn kéo ứng dụng để tìm icon Sổ Bé Ngoan)
     * đều bị tính là "app lạ" và bị ép goHome() ngay khi đã hết giờ — làm ngăn
     * kéo ứng dụng liên tục bị đóng lại, khiến bé KHÔNG THỂ bấm kịp vào icon
     * Sổ Bé Ngoan để xin thêm giờ/nhắn tin, dù bản thân app này không hề bị chặn.
     */
    private val homePackages: Set<String> by lazy {
        val homeIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        try {
            packageManager.queryIntentActivities(homeIntent, 0)
                .mapNotNull { it.activityInfo?.packageName }
                .toSet()
        } catch (e: Exception) {
            emptySet()
        }
    }

    companion object {
        private const val WARNING_THRESHOLD_MINUTES = 5
        private const val OWN_APP_GRACE_MS = 1500L
        private const val GO_HOME_DEBOUNCE_MS = 1000L
        private const val NOTICE_COOLDOWN_MS = 3 * 60 * 1000L // chỉ nhắc lại mỗi 3 phút, không spam
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        FirebaseSyncManager.listenForRules(this) { rules ->
            blockedPackages = rules.blockedApps
            dailyLimitMinutes = rules.dailyLimitMinutes
            appLimits = rules.appLimits
            locationEnabled = rules.locationEnabled
            bonusMinutesToday = rules.bonusMinutesToday
        }
        FirebaseSyncManager.listenForNewMessagesAndNotify(this)
        FirebaseSyncManager.listenForScreenshotRequest(this) { requestTs ->
            if (requestTs > lastHandledScreenshotRequest) {
                lastHandledScreenshotRequest = requestTs
                captureAndUploadScreenshot()
            }
        }
        FirebaseSyncManager.listenForBonusReward(this) { minutes ->
            NotificationHelper.showCelebration(this, minutes)
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val packageName = event?.packageName?.toString() ?: return
        if (packageName == this.packageName) {
            lastOwnAppForegroundTime = System.currentTimeMillis() // để bé luôn mở được Sổ Bé Ngoan
            return // đừng tự chặn chính mình
        }
        if (packageName in homePackages) {
            return // đang ở Home/ngăn kéo ứng dụng — không ép về Home, để bé còn bấm được icon
        }
        // Ngay sau khi vừa mở đúng Sổ Bé Ngoan, bỏ qua các sự kiện "gói tạm"
        // (hiệu ứng chuyển màn hình, launcher...) trong ít giây — tránh vô
        // tình đẩy bé về Home giữa lúc app vừa mở lên.
        if (System.currentTimeMillis() - lastOwnAppForegroundTime < OWN_APP_GRACE_MS) return

        val todayUsage = UsageStatsHelper.getTodayUsage(this)
        val usedMinutes = todayUsage.sumOf { it.minutesToday }
        val today = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
            .format(java.util.Date())
        FirebaseSyncManager.syncUsage(this, today, todayUsage)
        FirebaseSyncManager.reportHeartbeat(this)
        FirebaseSyncManager.reportDeviceInfo(this)
        FirebaseSyncManager.reportCurrentApp(this, packageName)
        LocationHelper.reportLastLocation(this, locationEnabled)

        val effectiveDailyLimit = dailyLimitMinutes + bonusMinutesToday
        val overLimit = usedMinutes >= effectiveDailyLimit
        val isBlockedApp = packageName in blockedPackages

        val thisAppMinutes = todayUsage.firstOrNull { it.packageName == packageName }?.minutesToday ?: 0
        val appLimit = appLimits[packageName]
        val overAppLimit = appLimit != null && thisAppMinutes >= appLimit

        if (overLimit || isBlockedApp || overAppLimit) {
            val now = System.currentTimeMillis()
            if (now - lastGoHomeTime > GO_HOME_DEBOUNCE_MS) {
                lastGoHomeTime = now
                goHome()
            }
            if (overAppLimit && !overLimit && !isBlockedApp) {
                if (now - lastAppLimitNoticeTime > NOTICE_COOLDOWN_MS) {
                    lastAppLimitNoticeTime = now
                    FirebaseSyncManager.reportAppLimitReached(this, packageName)
                    NotificationHelper.showBlockedNotice(this, "Con đã hết giờ dành riêng cho ứng dụng này hôm nay rồi.")
                }
            } else if (isBlockedApp && !overLimit) {
                if (now - lastBlockedAppNoticeTime > NOTICE_COOLDOWN_MS) {
                    lastBlockedAppNoticeTime = now
                    FirebaseSyncManager.reportBlockedAttempt(this, packageName, false)
                    NotificationHelper.showBlockedNotice(this, "Ứng dụng này đang bị ba mẹ chặn.")
                }
            } else {
                if (now - lastOverLimitNoticeTime > NOTICE_COOLDOWN_MS) {
                    lastOverLimitNoticeTime = now
                    FirebaseSyncManager.reportBlockedAttempt(this, packageName, overLimit)
                    NotificationHelper.showBlockedNotice(this, "Con đã dùng hết giờ máy hôm nay rồi. Xin thêm giờ trong app nhé!")
                }
            }
        } else {
            checkNearLimitWarnings(effectiveDailyLimit, usedMinutes.toInt(), packageName, appLimit, thisAppMinutes.toInt())
        }
    }

    private fun checkNearLimitWarnings(
        effectiveDailyLimit: Int, usedMinutes: Int,
        packageName: String, appLimit: Int?, thisAppMinutes: Int
    ) {
        val today = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date())
        val prefs = getSharedPreferences("family_connect_pairing", MODE_PRIVATE)

        val deviceRemaining = effectiveDailyLimit - usedMinutes
        if (effectiveDailyLimit != Int.MAX_VALUE && deviceRemaining in 1..WARNING_THRESHOLD_MINUTES) {
            val key = "warned_device_$today"
            if (!prefs.getBoolean(key, false)) {
                prefs.edit().putBoolean(key, true).apply()
                NotificationHelper.showTimeWarning(this, "Con còn khoảng $deviceRemaining phút dùng máy hôm nay.")
            }
        }

        if (appLimit != null) {
            val appRemaining = appLimit - thisAppMinutes
            if (appRemaining in 1..WARNING_THRESHOLD_MINUTES) {
                val key = "warned_app_${today}_$packageName"
                if (!prefs.getBoolean(key, false)) {
                    prefs.edit().putBoolean(key, true).apply()
                    NotificationHelper.showTimeWarning(this, "Con còn khoảng $appRemaining phút cho ứng dụng này hôm nay.")
                }
            }
        }
    }

    private fun captureAndUploadScreenshot() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return // cần Android 11 (API 30) trở lên

        takeScreenshot(
            android.view.Display.DEFAULT_DISPLAY,
            mainExecutor,
            object : TakeScreenshotCallback {
                override fun onSuccess(result: ScreenshotResult) {
                    try {
                        val hardwareBitmap = Bitmap.wrapHardwareBuffer(
                            result.hardwareBuffer, result.colorSpace
                        )
                        val softwareBitmap = hardwareBitmap?.copy(Bitmap.Config.ARGB_8888, false)
                        result.hardwareBuffer.close()
                        if (softwareBitmap == null) return

                        // 480px/chất lượng 45 trước đây làm chữ trong ảnh (vd: tin nhắn, tên
                        // liên hệ) bị vỡ/mờ không đọc được. Nâng lên 960px/chất lượng 65 —
                        // vẫn nhỏ gọn để không lặp lại sự cố vượt hạn mức Firebase trước đây
                        // (ảnh chỉ tải khi phụ huynh bấm chụp, không polling lại mỗi 5 giây —
                        // xem needScreenshot trong dashboard), nhưng đủ nét để đọc được chữ.
                        val targetWidth = minOf(960, softwareBitmap.width)
                        val scale = targetWidth.toFloat() / softwareBitmap.width
                        val targetHeight = (softwareBitmap.height * scale).toInt()
                        val scaled = Bitmap.createScaledBitmap(softwareBitmap, targetWidth, targetHeight, true)

                        val stream = ByteArrayOutputStream()
                        scaled.compress(Bitmap.CompressFormat.JPEG, 65, stream)
                        val base64 = Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP)
                        FirebaseSyncManager.uploadScreenshot(this@AppMonitorAccessibilityService, base64)
                    } catch (e: Exception) {
                        // Bỏ qua nếu chụp/xử lý lỗi — phụ huynh có thể bấm chụp lại.
                    }
                }

                override fun onFailure(errorCode: Int) {
                    // Bỏ qua — phụ huynh có thể bấm chụp lại từ dashboard.
                }
            }
        )
    }

    private fun goHome() {
        val homeIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        startActivity(homeIntent)
    }

    override fun onInterrupt() {}
}
