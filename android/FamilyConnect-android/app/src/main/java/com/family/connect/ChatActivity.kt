package com.family.connect

import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.family.connect.databinding.ActivityChatBinding

/**
 * Cửa sổ chat riêng biệt (không còn nhét chung vào màn hình chính) — hiện
 * toàn bộ lịch sử tin nhắn 2 chiều, cuộn được, tự cuộn xuống cuối khi có tin
 * mới. Đây cũng là màn hình mở ra khi bấm vào bong bóng chat nổi.
 */
class ChatActivity : AppCompatActivity() {

    private lateinit var binding: ActivityChatBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityChatBinding.inflate(layoutInflater)
        setContentView(binding.root)

        FirebaseSyncManager.listenForAllMessages(this) { messages ->
            renderMessages(messages)
        }

        binding.chatSendButton.setOnClickListener {
            val text = binding.chatInput.text.toString().trim()
            if (text.isNotEmpty()) {
                FirebaseSyncManager.sendMessageToParent(this, text)
                binding.chatInput.text?.clear()
            }
        }
    }

    private fun renderMessages(messages: List<FirebaseSyncManager.ChatMessage>) {
        binding.messagesContainer.removeAllViews()
        for (m in messages) {
            val isChild = m.from == "child"
            val bubble = TextView(this).apply {
                text = m.text
                setPadding(28, 20, 28, 20)
                setTextColor(if (isChild) Color.WHITE else Color.parseColor("#1C3D4E"))
                background = ContextCompat.getDrawable(
                    context, if (isChild) R.drawable.bubble_child else R.drawable.bubble_parent
                )
            }
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = if (isChild) Gravity.END else Gravity.START
                val params = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
                )
                params.setMargins(0, 8, 0, 8)
                layoutParams = params
                addView(bubble)
            }
            binding.messagesContainer.addView(row)
        }
        binding.chatScroll.post { binding.chatScroll.fullScroll(ScrollView.FOCUS_DOWN) }
    }
}
