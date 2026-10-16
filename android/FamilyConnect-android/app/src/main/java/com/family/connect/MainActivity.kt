package com.family.connect

import android.Manifest
import android.app.AppOpsManager
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Process
import android.provider.Settings
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.family.connect.databinding.ActivityMainBinding
import java.util.Calendar

/**
 * Màn hình app trên máy của con: đơn giản, thân thiện — hiện thời gian đã
 * dùng/giới hạn hôm nay, nút xin thêm giờ, điểm thưởng, lịch hôm nay, và nút
 * mở khung chat riêng. Chặn/theo dõi diễn ra ở AccessibilityService, không
 * cần con phải mở app này.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    /** Toàn bộ lịch tuần nhận từ Firebase, giữ lại để đổi ngày xem không phải chờ tải lại. */
    private var allScheduleItems: List<FirebaseSyncManager.ScheduleItem> = emptyList()

    /** Ngày đang được chọn để xem trên thanh tab (mặc định = hôm nay). */
    private var selectedDay: String = ""

    /** Giới hạn hiệu lực (giờ mặc định + thưởng) nhận lần gần nhất, để vẽ lại khi quay lại màn hình. */
    private var lastEffectiveLimit: Int = Int.MAX_VALUE

    private val weekDays = listOf(
        "T2" to "Thứ 2", "T3" to "Thứ 3", "T4" to "Thứ 4", "T5" to "Thứ 5",
        "T6" to "Thứ 6", "T7" to "Thứ 7", "CN" to "Chủ nhật"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (!PairingPrefs.isPaired(this)) {
            startActivity(Intent(this, PairingActivity::class.java))
            finish()
            return
        }

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        selectedDay = todayDayCode()
        buildDayTabs()

        requestDeviceAdminIfNeeded()
        promptAccessibilityIfNeeded()
        requestLocationPermissionIfNeeded()
        requestNotificationPermissionIfNeeded()
        requestUsageAccessIfNeeded()

        RewardManager.getPoints(this) { points ->
            binding.rewardPointsText.text = getString(R.string.reward_points_format, points)
        }

        FirebaseSyncManager.listenForMessagesFromParent(this) { text ->
            binding.latestMessageText.text = text
        }

        FirebaseSyncManager.listenForSchedule(this) { items ->
            allScheduleItems = items
            buildDayTabs()
            renderScheduleForDay(selectedDay)
        }

        FirebaseSyncManager.listenForRules(this) { rules ->
            lastEffectiveLimit = rules.dailyLimitMinutes + rules.bonusMinutesToday
            renderUsage(lastEffectiveLimit)
        }

        binding.requestExtensionButton.setOnClickListener {
            FirebaseSyncManager.requestExtension(this)
            Toast.makeText(this, "Đã gửi yêu cầu xin thêm giờ cho ba mẹ 🙏", Toast.LENGTH_LONG).show()
        }

        binding.openChatButton.setOnClickListener {
            startActivity(Intent(this, ChatActivity::class.java))
        }
    }

    override fun onResume() {
        super.onResume()
        // Sau khi bé/ba mẹ bật quyền "Usage Access" trong Cài đặt rồi quay lại app,
        // vẽ lại ngay để không phải mở lại app mới thấy giờ được đếm.
        if (::binding.isInitialized) {
            updateUsagePermissionWarning()
            renderUsage(lastEffectiveLimit)
        }
    }

    private fun renderUsage(effectiveLimit: Int) {
        val used = UsageStatsHelper.getTotalScreenTimeMinutes(this).toInt()
        binding.usageText.text = "Đã dùng hôm nay: $used / $effectiveLimit phút"
        val pct = if (effectiveLimit > 0) (used * 100 / effectiveLimit).coerceIn(0, 100) else 0
        binding.usageProgressBar.progress = pct
    }

    /**
     * "Usage Access" là quyền đặc biệt (khác Accessibility/Device Admin) — Android
     * KHÔNG tự xin hộ qua requestPermissions, và bị reset mỗi lần gỡ/cài lại app.
     * Thiếu quyền này thì UsageStatsManager âm thầm trả về danh sách rỗng — không
     * lỗi, không crash — nên trước đây giờ dùng máy cứ đứng yên ở 0 mà không rõ vì
     * sao. Giờ báo rõ bằng banner trên màn hình thay vì im lặng.
     */
    private fun hasUsageAccessPermission(): Boolean {
        val appOps = getSystemService(APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), packageName)
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), packageName)
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    private fun requestUsageAccessIfNeeded() {
        updateUsagePermissionWarning()
        binding.usagePermissionWarning.setOnClickListener {
            startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
        }
        if (!hasUsageAccessPermission()) {
            startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
        }
    }

    private fun updateUsagePermissionWarning() {
        binding.usagePermissionWarning.visibility =
            if (hasUsageAccessPermission()) android.view.View.GONE else android.view.View.VISIBLE
    }

    private fun requestDeviceAdminIfNeeded() {
        val dpm = getSystemService(DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val adminComponent = ComponentName(this, FamilyDeviceAdminReceiver::class.java)
        if (!dpm.isAdminActive(adminComponent)) {
            val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent)
                putExtra(
                    DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                    getString(R.string.device_admin_explanation)
                )
            }
            startActivity(intent)
        }
    }

    private fun promptAccessibilityIfNeeded() {
        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    }

    private fun requestLocationPermissionIfNeeded() {
        val granted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
                1001
            )
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                this, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                ActivityCompat.requestPermissions(
                    this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1002
                )
            }
        }
    }

    private fun todayDayCode(): String {
        return when (Calendar.getInstance().get(Calendar.DAY_OF_WEEK)) {
            Calendar.MONDAY -> "T2"
            Calendar.TUESDAY -> "T3"
            Calendar.WEDNESDAY -> "T4"
            Calendar.THURSDAY -> "T5"
            Calendar.FRIDAY -> "T6"
            Calendar.SATURDAY -> "T7"
            else -> "CN"
        }
    }

    /** Tạo thanh 7 tab Thứ 2 → Chủ nhật để bé bấm xem lịch từng ngày trong tuần. */
    private fun buildDayTabs() {
        val today = todayDayCode()
        binding.dayTabsContainer.removeAllViews()
        for ((code, label) in weekDays) {
            val isSelected = code == selectedDay
            val tab = TextView(this).apply {
                text = if (code == today) "$label\n(Hôm nay)" else label
                gravity = android.view.Gravity.CENTER
                setPadding(28, 14, 28, 14)
                textSize = 13f
                setTextColor(if (isSelected) Color.WHITE else Color.parseColor("#1C3D4E"))
                setTypeface(typeface, if (isSelected) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)
                val shape = android.graphics.drawable.GradientDrawable()
                shape.setColor(if (isSelected) Color.parseColor("#2F9E8F") else Color.WHITE)
                shape.cornerRadius = 24f
                background = shape
                val params = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
                )
                params.setMargins(6, 0, 6, 0)
                layoutParams = params
                setOnClickListener {
                    selectedDay = code
                    buildDayTabs()
                    renderScheduleForDay(selectedDay)
                }
            }
            binding.dayTabsContainer.addView(tab)
        }
    }

    private fun renderScheduleForDay(day: String) {
        val dayItems = allScheduleItems
            .filter { it.day == "daily" || it.day == day }
            .sortedBy { it.time }

        binding.scheduleContainer.removeAllViews()
        if (dayItems.isEmpty()) {
            val empty = TextView(this).apply {
                text = "Chưa có lịch nào cho ngày này"
                setPadding(20, 20, 20, 20)
                setTextColor(Color.parseColor("#8B9AA5"))
            }
            binding.scheduleContainer.addView(empty)
            return
        }

        val isToday = day == todayDayCode()
        val nowStr = java.text.SimpleDateFormat("HH:mm", java.util.Locale.US).format(java.util.Date())
        var currentIndex = -1
        if (isToday) {
            dayItems.forEachIndexed { i, item -> if (item.time <= nowStr) currentIndex = i }
        }

        for ((index, item) in dayItems.withIndex()) {
            val isCurrent = index == currentIndex
            val row = TextView(this).apply {
                text = if (isCurrent) "🟢 ${item.time}  —  ${item.title}  (đang diễn ra)" else "${item.time}  —  ${item.title}"
                setPadding(20, 16, 20, 16)
                setTextColor(Color.parseColor("#1C3D4E"))
                setTypeface(typeface, if (isCurrent) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)
                if (isCurrent) {
                    val shape = android.graphics.drawable.GradientDrawable()
                    shape.setColor(Color.parseColor("#F0FBF9"))
                    shape.setStroke(4, Color.parseColor("#2F9E8F"))
                    shape.cornerRadius = 20f
                    background = shape
                    val params = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                    params.setMargins(8, 8, 8, 8)
                    layoutParams = params
                }
            }
            binding.scheduleContainer.addView(row)
        }
    }
}
