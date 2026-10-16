package com.family.connect

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.StatFs

/**
 * Đọc thông tin cơ bản của thiết bị: tên máy, phiên bản Android, % pin,
 * và dung lượng bộ nhớ trong đã dùng/tổng. Không cần xin thêm quyền gì
 * ngoài những quyền app đã có sẵn.
 */
object DeviceInfoHelper {

    data class DeviceInfo(
        val model: String,
        val androidVersion: String,
        val batteryPercent: Int,
        val storageUsedGB: Double,
        val storageTotalGB: Double
    )

    fun getDeviceInfo(context: Context): DeviceInfo {
        val batteryStatus: Intent? = context.registerReceiver(
            null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        )
        val level = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val batteryPercent = if (level >= 0 && scale > 0) (level * 100 / scale) else -1

        val statFs = StatFs(Environment.getDataDirectory().path)
        val totalBytes = statFs.totalBytes
        val availableBytes = statFs.availableBytes
        val usedBytes = totalBytes - availableBytes
        val gb = 1024.0 * 1024.0 * 1024.0

        return DeviceInfo(
            model = "${Build.MANUFACTURER} ${Build.MODEL}",
            androidVersion = "Android ${Build.VERSION.RELEASE}",
            batteryPercent = batteryPercent,
            storageUsedGB = usedBytes / gb,
            storageTotalGB = totalBytes / gb
        )
    }
}
