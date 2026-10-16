package com.family.connect

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent

/**
 * Cho phép app khoá màn hình khi con dùng hết thời gian được phép trong ngày.
 * Quyền này bắt buộc phải được con/phụ huynh xác nhận thủ công lúc cài đặt —
 * đây là cơ chế bảo vệ của Android, không thể bật ngầm.
 */
class FamilyDeviceAdminReceiver : DeviceAdminReceiver() {
    override fun onEnabled(context: Context, intent: Intent) {
        super.onEnabled(context, intent)
    }

    override fun onDisabled(context: Context, intent: Intent) {
        super.onDisabled(context, intent)
        // Thông báo cho phụ huynh qua Firebase nếu quyền admin bị tắt bất thường
        if (PairingPrefs.isPaired(context)) FirebaseSyncManager.reportAdminDisabled(context)
    }
}
