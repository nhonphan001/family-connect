package com.family.connect

import com.google.firebase.auth.FirebaseAuth

/**
 * Mỗi máy con tự đăng nhập "ẩn danh" (không cần email/mật khẩu) để có 1 UID
 * riêng — UID này được dùng để phụ huynh "duyệt" thiết bị trên dashboard,
 * thay vì chỉ cần biết Mã gia đình là ghi được dữ liệu ngay.
 */
object AuthHelper {

    fun signInAnonymously(onResult: (uid: String?) -> Unit) {
        val auth = FirebaseAuth.getInstance()
        val current = auth.currentUser
        if (current != null) {
            onResult(current.uid)
            return
        }
        auth.signInAnonymously()
            .addOnSuccessListener { result -> onResult(result.user?.uid) }
            .addOnFailureListener { onResult(null) }
    }

    fun currentUid(): String? = FirebaseAuth.getInstance().currentUser?.uid
}
