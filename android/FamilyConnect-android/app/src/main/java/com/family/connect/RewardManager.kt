package com.family.connect

import android.content.Context
import com.google.firebase.database.FirebaseDatabase

/**
 * Quản lý điểm thưởng đơn giản — ví dụ: hoàn thành bài tập, đọc sách, hoặc
 * dùng máy đúng giờ trong 1 tuần sẽ được ba mẹ cộng điểm từ dashboard.
 * Điểm có thể quy đổi thành thời gian chơi thêm hoặc phần thưởng thực tế do
 * gia đình tự thoả thuận — mục đích là khuyến khích thay vì chỉ trừng phạt.
 */
object RewardManager {
    private fun rewardRef(context: Context) = FirebaseDatabase.getInstance()
        .getReference(
            "families/${PairingPrefs.getFamilyId(context)}/children/${PairingPrefs.getChildId(context)}/rewards"
        )

    fun getPoints(context: Context, onResult: (Int) -> Unit) {
        rewardRef(context).child("points").get().addOnSuccessListener {
            onResult(it.getValue(Int::class.java) ?: 0)
        }
    }
}
