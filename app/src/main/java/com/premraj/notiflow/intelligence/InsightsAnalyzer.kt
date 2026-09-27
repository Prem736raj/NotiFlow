package com.premraj.notiflow.intelligence

import com.premraj.notiflow.data.InsightsTimeRange
import com.premraj.notiflow.data.NotificationInsights
import com.premraj.notiflow.data.NotificationItem
import java.util.Calendar

object InsightsAnalyzer {
    fun computeInsights(
        notifications: List<NotificationItem>,
        timeRange: InsightsTimeRange,
        nowMillis: Long = System.currentTimeMillis()
    ): NotificationInsights {
        val startMillis = startOfRange(timeRange, nowMillis)
        val filtered = notifications.filter { it.postedAt in startMillis..nowMillis }

        val hourly = (0..23).associateWithTo(linkedMapOf()) { 0 }
        filtered.forEach { item ->
            val hour = Calendar.getInstance().apply { timeInMillis = item.postedAt }
                .get(Calendar.HOUR_OF_DAY)
            hourly[hour] = hourly.getValue(hour) + 1
        }

        val appDistributions = filtered
            .groupingBy { it.appName }
            .eachCount()
            .entries
            .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key.lowercase() })
            .map { it.key to it.value }

        val categoryDistributions = filtered
            .groupingBy { it.category }
            .eachCount()

        val peak = if (filtered.isEmpty()) null else hourly.entries.maxByOrNull { it.value }

        return NotificationInsights(
            totalCount = filtered.size,
            hourlyDistribution = hourly,
            appDistributions = appDistributions,
            categoryDistributions = categoryDistributions,
            peakHour = peak?.key,
            peakHourCount = peak?.value ?: 0,
            timeRange = timeRange
        )
    }

    private fun startOfRange(
        timeRange: InsightsTimeRange,
        nowMillis: Long
    ): Long {
        return Calendar.getInstance().apply {
            timeInMillis = nowMillis
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)

            when (timeRange) {
                InsightsTimeRange.TODAY -> Unit
                InsightsTimeRange.LAST_7_DAYS -> add(Calendar.DAY_OF_YEAR, -6)
                InsightsTimeRange.LAST_30_DAYS -> add(Calendar.DAY_OF_YEAR, -29)
            }
        }.timeInMillis
    }
}
