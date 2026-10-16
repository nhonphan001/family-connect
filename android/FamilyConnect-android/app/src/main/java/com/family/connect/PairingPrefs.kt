package com.family.connect

import android.content.Context
import java.text.Normalizer

/**
 * Lưu Mã gia đình + ID của bé trên chính máy con (SharedPreferences), để
 * không phải hard-code trong lúc build nữa. Bé/ba mẹ chỉ cần nhập 1 lần khi
 * cài app.
 */
object PairingPrefs {
    private const val PREFS = "family_connect_pairing"
    private const val KEY_FAMILY_ID = "family_id"
    private const val KEY_CHILD_ID = "child_id"
    private const val KEY_DEVICE_UID = "device_uid"

    fun isPaired(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return !prefs.getString(KEY_FAMILY_ID, null).isNullOrBlank() &&
            !prefs.getString(KEY_CHILD_ID, null).isNullOrBlank()
    }

    fun getFamilyId(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_FAMILY_ID, "") ?: ""

    fun getChildId(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_CHILD_ID, "") ?: ""

    /**
     * UID ẩn danh của chính máy này — cũng là ID của máy trong
     * children/{childId}/devices/{deviceUid}. Máy ghép nối từ bản cũ chưa lưu
     * UID vào đây, nên lấy từ phiên đăng nhập Firebase (vẫn là UID đó).
     */
    fun getDeviceUid(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_DEVICE_UID, null)
            ?: AuthHelper.currentUid() ?: ""

    fun savePairing(context: Context, familyId: String, childId: String, deviceUid: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_FAMILY_ID, familyId)
            .putString(KEY_CHILD_ID, childId)
            .putString(KEY_DEVICE_UID, deviceUid)
            .apply()
    }

    /** Ba mẹ xoá thiết bị này trên dashboard -> quên ghép nối, quay về màn hình nhập Mã gia đình. */
    fun clearPairing(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .remove(KEY_FAMILY_ID)
            .remove(KEY_CHILD_ID)
            .remove(KEY_DEVICE_UID)
            .apply()
    }

    /** Chuyển "Bình" -> "binh", "Bé Út" -> "be_ut" để dùng làm ID gọn, không dấu. */
    fun slugify(input: String): String {
        val normalized = Normalizer.normalize(input, Normalizer.Form.NFD)
        val noAccents = normalized.replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
        return noAccents.lowercase()
            .replace("đ", "d")
            .replace(Regex("[^a-z0-9]+"), "_")
            .trim('_')
            .ifBlank { "be" }
    }
}
