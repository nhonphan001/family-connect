package com.family.connect

import android.content.Context
import com.google.firebase.database.ChildEventListener
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import com.google.firebase.database.ValueEventListener

/**
 * Cầu nối 2 chiều giữa app trên máy con và dashboard web của phụ huynh, qua
 * Firebase Realtime Database. Family ID và Child ID không còn hard-code nữa —
 * được lấy từ PairingPrefs (đã nhập lúc ghép nối bằng Mã gia đình).
 */
object FirebaseSyncManager {

    data class Rules(
        val dailyLimitMinutes: Int,
        val blockedApps: Set<String>,
        val appLimits: Map<String, Int>,
        val locationEnabled: Boolean,
        val bonusMinutesToday: Int
    )

    data class ChatMessage(val from: String, val text: String, val timestamp: Long)

    data class ScheduleItem(val day: String, val time: String, val title: String)

    private val db = FirebaseDatabase.getInstance()

    private fun childRef(context: Context) = db.getReference(
        "families/${PairingPrefs.getFamilyId(context)}/children/${PairingPrefs.getChildId(context)}"
    )

    /** Ghi tên + lớp của bé một lần lúc ghép nối, để dashboard tự hiện tên đẹp thay vì ID thô. */
    fun saveProfile(context: Context, name: String, grade: String) {
        val profile = mapOf("name" to name, "grade" to grade)
        childRef(context).child("profile").setValue(profile)
    }

    /**
     * Gửi yêu cầu ghép nối tới 1 Mã gia đình cụ thể — CHƯA ghi được vào
     * children/... cho tới khi phụ huynh bấm Duyệt trên dashboard.
     * deviceUid lấy từ AuthHelper.signInAnonymously().
     */
    fun requestPairing(
        familyId: String, deviceUid: String, childName: String, childGrade: String,
        onResult: (Boolean) -> Unit
    ) {
        val data = mapOf(
            "childName" to childName,
            "childGrade" to childGrade,
            "requestedAt" to ServerValue.TIMESTAMP
        )
        FirebaseDatabase.getInstance()
            .getReference("families/$familyId/pendingDevices/$deviceUid")
            .setValue(data)
            .addOnSuccessListener { onResult(true) }
            .addOnFailureListener { onResult(false) }
    }

    /**
     * Lắng nghe xem phụ huynh đã Duyệt thiết bị này chưa. Khi đã duyệt,
     * Firebase sẽ có node approvedDevices/{deviceUid} chứa childId thật.
     */
    fun listenForPairingApproval(
        familyId: String, deviceUid: String, onApproved: (childId: String) -> Unit
    ) {
        FirebaseDatabase.getInstance()
            .getReference("families/$familyId/approvedDevices/$deviceUid")
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val childId = snapshot.child("childId").getValue(String::class.java)
                    if (childId != null) onApproved(childId)
                }
                override fun onCancelled(error: DatabaseError) {}
            })
    }

    private fun todayDateStr(): String =
        java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date())

    fun listenForRules(context: Context, onRulesChanged: (Rules) -> Unit) {
        childRef(context).child("rules").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val limit = snapshot.child("dailyLimitMinutes").getValue(Int::class.java) ?: 120
                val blocked = snapshot.child("blockedApps").children
                    .mapNotNull { it.getValue(String::class.java) }
                    .toSet()
                val appLimits = snapshot.child("appLimits").children.mapNotNull { entry ->
                    val pkg = entry.child("pkg").getValue(String::class.java)
                    val minutes = entry.child("limitMinutes").getValue(Int::class.java)
                    if (pkg != null && minutes != null) pkg to minutes else null
                }.toMap()
                val locationEnabled = snapshot.child("locationEnabled").getValue(Boolean::class.java) ?: false
                val bonus = snapshot.child("bonusMinutes").child(todayDateStr()).getValue(Int::class.java) ?: 0
                onRulesChanged(Rules(limit, blocked, appLimits, locationEnabled, bonus))
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    fun syncUsage(context: Context, date: String, usage: List<UsageStatsHelper.AppUsage>) {
        val updates = usage.associate { it.packageName.replace(".", "_") to it.minutesToday }
        childRef(context).child("usage").child(date).updateChildren(updates)
    }

    fun reportBlockedAttempt(context: Context, packageName: String, overDailyLimit: Boolean) {
        val alert = mapOf(
            "type" to if (overDailyLimit) "OVER_LIMIT" else "BLOCKED_APP",
            "packageName" to packageName,
            "timestamp" to System.currentTimeMillis()
        )
        childRef(context).child("alerts").push().setValue(alert)
    }

    fun reportAppLimitReached(context: Context, packageName: String) {
        val alert = mapOf(
            "type" to "APP_LIMIT",
            "packageName" to packageName,
            "timestamp" to System.currentTimeMillis()
        )
        childRef(context).child("alerts").push().setValue(alert)
    }

    fun reportLocation(context: Context, lat: Double, lng: Double) {
        val data = mapOf(
            "lat" to lat,
            "lng" to lng,
            "timestamp" to System.currentTimeMillis()
        )
        childRef(context).child("location").setValue(data)
    }

    /** Lịch học/sinh hoạt do phụ huynh đặt trên dashboard, lọc theo "hôm nay" ở phía app. */
    fun listenForSchedule(context: Context, onSchedule: (List<ScheduleItem>) -> Unit) {
        childRef(context).child("schedule").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val list = snapshot.children.mapNotNull { child ->
                    val day = child.child("day").getValue(String::class.java)
                    val time = child.child("time").getValue(String::class.java)
                    val title = child.child("title").getValue(String::class.java)
                    if (day != null && time != null && title != null) ScheduleItem(day, time, title) else null
                }
                onSchedule(list)
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    fun reportAdminDisabled(context: Context) {
        childRef(context).child("alerts").push().setValue(
            mapOf("type" to "ADMIN_DISABLED", "timestamp" to System.currentTimeMillis())
        )
    }

    fun sendMessageToParent(context: Context, text: String) {
        val msg = mapOf("from" to "child", "text" to text, "timestamp" to System.currentTimeMillis())
        childRef(context).child("messages").push().setValue(msg)
    }

    fun reportHeartbeat(context: Context) {
        childRef(context).child("status").child("lastSeen").setValue(System.currentTimeMillis())
    }

    fun reportDeviceInfo(context: Context) {
        val info = DeviceInfoHelper.getDeviceInfo(context)
        val data = mapOf(
            "model" to info.model,
            "androidVersion" to info.androidVersion,
            "batteryPercent" to info.batteryPercent,
            "storageUsedGB" to info.storageUsedGB,
            "storageTotalGB" to info.storageTotalGB,
            "timestamp" to System.currentTimeMillis()
        )
        childRef(context).child("device").setValue(data)
    }

    /** Ứng dụng đang mở ngay lúc này — để dashboard hiện "Đang dùng: ...". */
    fun reportCurrentApp(context: Context, packageName: String) {
        val data = mapOf("packageName" to packageName, "timestamp" to System.currentTimeMillis())
        childRef(context).child("status").child("currentApp").setValue(data)
    }

    /** Phụ huynh bấm "Chụp màn hình" trên dashboard sẽ ghi timestamp vào đây. */
    fun listenForScreenshotRequest(context: Context, onRequest: (Long) -> Unit) {
        childRef(context).child("commands").child("requestScreenshot")
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val ts = snapshot.getValue(Long::class.java) ?: return
                    onRequest(ts)
                }
                override fun onCancelled(error: DatabaseError) {}
            })
    }

    fun uploadScreenshot(context: Context, base64Jpeg: String) {
        val data = mapOf("imageBase64" to base64Jpeg, "timestamp" to ServerValue.TIMESTAMP)
        childRef(context).child("screenshot").setValue(data)
    }

    /** Bé bấm "Xin thêm giờ" trên app — ba mẹ thấy yêu cầu này trên dashboard. */
    fun requestExtension(context: Context) {
        val data = mapOf("timestamp" to ServerValue.TIMESTAMP, "status" to "pending")
        childRef(context).child("extensionRequests").push().setValue(data)
    }

    /**
     * Lắng nghe liên tục để biết ngay khi ba mẹ thưởng thêm thời gian, nhằm
     * hiện thông báo chúc mừng/khích lệ trên máy con.
     */
    fun listenForBonusReward(context: Context, onBonus: (Int) -> Unit) {
        val prefs = context.getSharedPreferences("family_connect_pairing", Context.MODE_PRIVATE)
        if (!prefs.contains("last_bonus_ts")) {
            prefs.edit().putLong("last_bonus_ts", System.currentTimeMillis()).apply()
        }
        childRef(context).child("rewardEvents").addChildEventListener(object : ChildEventListener {
            override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) {
                val ts = snapshot.child("timestamp").getValue(Long::class.java) ?: 0L
                val minutes = snapshot.child("minutes").getValue(Int::class.java) ?: 0
                val lastTs = prefs.getLong("last_bonus_ts", 0L)
                if (ts > lastTs) {
                    prefs.edit().putLong("last_bonus_ts", ts).apply()
                    onBonus(minutes)
                }
            }
            override fun onChildChanged(snapshot: DataSnapshot, previousChildName: String?) {}
            override fun onChildRemoved(snapshot: DataSnapshot) {}
            override fun onChildMoved(snapshot: DataSnapshot, previousChildName: String?) {}
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    fun listenForMessagesFromParent(context: Context, onMessage: (String) -> Unit) {
        childRef(context).child("messages").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                snapshot.children.lastOrNull()?.let {
                    if (it.child("from").getValue(String::class.java) == "parent") {
                        it.child("text").getValue(String::class.java)?.let(onMessage)
                    }
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    /** Toàn bộ lịch sử tin nhắn, cho màn hình chat riêng (ChatActivity). */
    fun listenForAllMessages(context: Context, onMessages: (List<ChatMessage>) -> Unit) {
        childRef(context).child("messages").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val list = snapshot.children.mapNotNull { child ->
                    val from = child.child("from").getValue(String::class.java)
                    val text = child.child("text").getValue(String::class.java)
                    val ts = child.child("timestamp").getValue(Long::class.java)
                    if (from != null && text != null && ts != null) ChatMessage(from, text, ts) else null
                }.sortedBy { it.timestamp }
                onMessages(list)
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    /**
     * Lắng nghe liên tục (kể cả khi app không mở) để bắn thông báo/bóng chat
     * ngay khi có tin nhắn mới từ ba mẹ. Gọi 1 lần trong AccessibilityService
     * vì đó là thành phần chạy nền lâu dài duy nhất của app.
     */
    fun listenForNewMessagesAndNotify(context: Context) {
        val prefs = context.getSharedPreferences("family_connect_pairing", Context.MODE_PRIVATE)
        if (!prefs.contains("last_notified_ts")) {
            // Lần đầu bật: coi như mọi tin nhắn cũ đã "đọc rồi", không thông báo dồn dập.
            prefs.edit().putLong("last_notified_ts", System.currentTimeMillis()).apply()
        }
        childRef(context).child("messages").addChildEventListener(object : ChildEventListener {
            override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) {
                val from = snapshot.child("from").getValue(String::class.java)
                val text = snapshot.child("text").getValue(String::class.java)
                val ts = snapshot.child("timestamp").getValue(Long::class.java) ?: 0L
                val lastTs = prefs.getLong("last_notified_ts", 0L)
                if (from == "parent" && text != null && ts > lastTs) {
                    prefs.edit().putLong("last_notified_ts", ts).apply()
                    NotificationHelper.showChatBubble(context, text)
                }
            }
            override fun onChildChanged(snapshot: DataSnapshot, previousChildName: String?) {}
            override fun onChildRemoved(snapshot: DataSnapshot) {}
            override fun onChildMoved(snapshot: DataSnapshot, previousChildName: String?) {}
            override fun onCancelled(error: DatabaseError) {}
        })
    }
}
