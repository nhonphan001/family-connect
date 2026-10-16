package com.family.connect

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.Person
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat

/**
 * Hiện thông báo dạng "bong bóng chat" (Bubble) nổi trên màn hình khi ba mẹ
 * nhắn tin mới — bấm vào là mở thẳng ChatActivity. Trên Android, tính năng
 * Bubble cần người dùng chủ động bật 1 lần (Cài đặt > Ứng dụng > Sổ Bé Ngoan >
 * Thông báo > bật "Cho phép bong bóng chat") — đây là quyền do hệ điều hành
 * bắt buộc, ứng dụng nào cũng phải xin như vậy, không có cách nào bật thay.
 * Nếu chưa bật, tin nhắn vẫn hiện thành thông báo bình thường (không nổi bóng).
 */
object NotificationHelper {
    private const val CHANNEL_ID = "family_chat_channel"
    private const val ALERT_CHANNEL_ID = "family_alert_channel"
    private const val SHORTCUT_ID = "parent_chat"
    private const val NOTIFICATION_ID = 2001
    private const val WARNING_NOTIFICATION_ID = 2002
    private const val BLOCKED_NOTIFICATION_ID = 2003
    private const val CELEBRATION_NOTIFICATION_ID = 2004

    private fun ensureAlertChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(NotificationManager::class.java)
            if (manager.getNotificationChannel(ALERT_CHANNEL_ID) == null) {
                val channel = NotificationChannel(
                    ALERT_CHANNEL_ID, "Nhắc nhở giờ dùng máy", NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Cảnh báo sắp hết giờ, chặn ứng dụng, và khen thưởng"
                }
                manager.createNotificationChannel(channel)
            }
        }
    }

    private fun showSimpleNotification(
        context: Context, id: Int, title: String, text: String, icon: Int, openAppOnTap: Boolean = false
    ) {
        try {
            ensureAlertChannel(context)
            val builder = NotificationCompat.Builder(context, ALERT_CHANNEL_ID)
                .setSmallIcon(icon)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
            if (openAppOnTap) {
                // Bấm vào thông báo mở thẳng Sổ Bé Ngoan — đường vào chắc chắn để
                // bé xin thêm giờ/nhắn tin ba mẹ, không phụ thuộc việc bé có tìm
                // và bấm kịp icon trên màn hình chính hay không.
                val openIntent = Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
                val pendingIntent = PendingIntent.getActivity(
                    context, id, openIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                builder.setContentIntent(pendingIntent)
            }
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.notify(id, builder.build())
        } catch (e: Exception) {
            // Không để lỗi thông báo làm crash ứng dụng.
        }
    }

    /** Sắp hết giờ dùng máy hoặc hết giờ riêng của 1 app. */
    fun showTimeWarning(context: Context, message: String) {
        showSimpleNotification(context, WARNING_NOTIFICATION_ID, "⏰ Sắp hết giờ rồi!", message, R.drawable.ic_stat_chat)
    }

    /** Vừa bị chặn 1 app / vừa hết giờ dùng máy. Bấm vào thông báo mở thẳng app để xin thêm giờ/nhắn tin. */
    fun showBlockedNotice(context: Context, message: String) {
        showSimpleNotification(
            context, BLOCKED_NOTIFICATION_ID, "🚫 Ứng dụng bị chặn",
            "$message Bấm vào đây để mở Sổ Bé Ngoan.", R.drawable.ic_stat_chat, openAppOnTap = true
        )
    }

    /** Ba mẹ vừa thưởng thêm thời gian — thông báo chúc mừng, khích lệ. */
    fun showCelebration(context: Context, minutes: Int) {
        val messages = listOf(
            "🎉 Ba mẹ vừa thưởng cho con thêm $minutes phút chơi! Con giỏi lắm!",
            "🌟 Con được thưởng thêm $minutes phút rồi đó! Tiếp tục ngoan nha!",
            "🎁 Bất ngờ chưa! Ba mẹ tặng con thêm $minutes phút sử dụng máy!"
        )
        showSimpleNotification(
            context, CELEBRATION_NOTIFICATION_ID, "Được thưởng thời gian!",
            messages.random(), R.drawable.ic_stat_chat
        )
    }

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(NotificationManager::class.java)
            if (manager.getNotificationChannel(CHANNEL_ID) == null) {
                val channel = NotificationChannel(
                    CHANNEL_ID, "Tin nhắn từ ba mẹ", NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Thông báo khi ba mẹ nhắn tin"
                    setAllowBubbles(true)
                }
                manager.createNotificationChannel(channel)
            }
        }
    }

    private fun ensureShortcut(context: Context, person: Person) {
        val chatIntent = Intent(context, ChatActivity::class.java).setAction(Intent.ACTION_VIEW)
        val shortcut = ShortcutInfoCompat.Builder(context, SHORTCUT_ID)
            .setLongLived(true)
            .setIntent(chatIntent)
            .setShortLabel("Ba mẹ")
            .setIcon(IconCompat.createWithResource(context, R.mipmap.ic_launcher))
            .setPerson(person)
            .setCategories(setOf("android.shortcut.conversation"))
            .build()
        ShortcutManagerCompat.pushDynamicShortcut(context, shortcut)
    }

    fun showChatBubble(context: Context, messageText: String) {
        try {
            ensureChannel(context)

            val person = Person.Builder().setName("Ba mẹ").build()
            try {
                ensureShortcut(context, person)
            } catch (e: Exception) {
                // Một số máy (đặc biệt Huawei thiếu Google Play Services đầy đủ)
                // có thể lỗi khi tạo shortcut — bỏ qua, không ảnh hưởng tới việc
                // hiện thông báo bình thường bên dưới.
            }

            val chatIntent = Intent(context, ChatActivity::class.java)
            val chatPendingIntent = PendingIntent.getActivity(
                context, 0, chatIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val messagingStyle = NotificationCompat.MessagingStyle(person)
                .addMessage(messageText, System.currentTimeMillis(), person)

            val builder = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_chat)
                .setStyle(messagingStyle)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                .setShortcutId(SHORTCUT_ID)
                .setAutoCancel(true)
                .setContentIntent(chatPendingIntent)

            // Bong bóng chat (Bubble) rất hay gây lỗi trên các máy tùy biến sâu
            // (Huawei, thiếu Google Play Services...) — tách riêng, lỗi ở đây
            // tuyệt đối không được làm crash cả thông báo.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                try {
                    val bubbleIcon = IconCompat.createWithResource(context, R.mipmap.ic_launcher)
                    val bubbleData = NotificationCompat.BubbleMetadata.Builder(chatPendingIntent, bubbleIcon)
                        .setDesiredHeight(600)
                        .setAutoExpandBubble(false)
                        .setSuppressNotification(false)
                        .build()
                    builder.setBubbleMetadata(bubbleData)
                } catch (e: Exception) {
                    // Bỏ qua bong bóng, vẫn hiện thông báo thường ở dưới.
                }
            }

            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.notify(NOTIFICATION_ID, builder.build())
        } catch (e: Exception) {
            // Không để bất kỳ lỗi thông báo nào làm crash cả ứng dụng —
            // tin nhắn vẫn được lưu và hiện trong ChatActivity như bình thường.
        }
    }
}
