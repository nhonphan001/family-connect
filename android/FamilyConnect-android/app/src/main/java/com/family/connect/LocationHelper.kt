package com.family.connect

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.location.LocationServices

/**
 * Lấy vị trí gần nhất đã biết của máy (không chủ động bật GPS liên tục để
 * đỡ hao pin) và gửi lên Firebase. Chỉ hoạt động nếu người dùng đã cấp
 * quyền vị trí khi mở app lần đầu, VÀ máy có Google Play Services (một số
 * máy Huawei đời mới không có — trên các máy đó tính năng định vị sẽ tự bỏ
 * qua một cách an toàn, không làm crash phần còn lại của app).
 */
object LocationHelper {

    private var lastReportTime = 0L
    private const val MIN_INTERVAL_MS = 5 * 60 * 1000L // tối đa 1 lần mỗi 5 phút

    fun reportLastLocation(context: Context, enabled: Boolean) {
        if (!enabled) return

        try {
            val now = System.currentTimeMillis()
            if (now - lastReportTime < MIN_INTERVAL_MS) return

            val hasFine = ActivityCompat.checkSelfPermission(
                context, Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
            val hasCoarse = ActivityCompat.checkSelfPermission(
                context, Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED

            if (!hasFine && !hasCoarse) return

            // Máy không có Google Play Services (vd nhiều máy Huawei) -> bỏ qua
            // định vị một cách an toàn, không đụng tới các lớp GMS bên dưới nữa.
            val playServicesStatus = GoogleApiAvailability.getInstance()
                .isGooglePlayServicesAvailable(context)
            if (playServicesStatus != ConnectionResult.SUCCESS) return

            lastReportTime = now // đánh dấu ngay để tránh gọi trùng khi có nhiều sự kiện dồn dập

            val client = LocationServices.getFusedLocationProviderClient(context)
            client.lastLocation.addOnSuccessListener { location ->
                if (location != null) {
                    FirebaseSyncManager.reportLocation(context, location.latitude, location.longitude)
                }
            }
        } catch (t: Throwable) {
            // Bắt MỌI loại lỗi (kể cả NoClassDefFoundError khi thiếu Google Play
            // Services) để tính năng định vị không bao giờ làm crash cả app.
        }
    }
}
