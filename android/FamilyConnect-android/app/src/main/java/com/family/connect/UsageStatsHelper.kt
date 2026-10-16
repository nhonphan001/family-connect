package com.family.connect

import android.app.usage.UsageStatsManager
import android.content.Context
import java.util.Calendar

/**
 * Tính thời gian sử dụng từng ứng dụng trong ngày, dựa trên UsageStatsManager.
 * Cần quyền "Usage Access" được bật thủ công trong Cài đặt hệ thống.
 */
object UsageStatsHelper {

    data class AppUsage(val packageName: String, val minutesToday: Long)

    fun getTodayUsage(context: Context): List<AppUsage> {
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
        }
        val startTime = calendar.timeInMillis
        val endTime = System.currentTimeMillis()

        val stats = usm.queryUsageStats(
            UsageStatsManager.INTERVAL_DAILY, startTime, endTime
        )

        return stats
            .filter { it.totalTimeInForeground > 0 }
            .map { AppUsage(it.packageName, it.totalTimeInForeground / 60000) }
            .sortedByDescending { it.minutesToday }
    }

    fun getTotalScreenTimeMinutes(context: Context): Long =
        getTodayUsage(context).sumOf { it.minutesToday }
}
