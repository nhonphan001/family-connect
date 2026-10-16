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

    fun isPaired(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return !prefs.getString(KEY_FAMILY_ID, null).isNullOrBlank() &&
            !prefs.getString(KEY_CHILD_ID, null).isNullOrBlank()
    }

    fun getFamilyId(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_FAMILY_ID, "") ?: ""

    fun getChildId(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_CHILD_ID, "") ?: ""

    fun savePairing(context: Context, familyId: String, childId: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_FAMILY_ID, familyId)
            .putString(KEY_CHILD_ID, childId)
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
