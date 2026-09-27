package com.premraj.notiflow.data

enum class InsightsTimeRange(val label: String) {
    TODAY("Today"),
    LAST_7_DAYS("7 days"),
    LAST_30_DAYS("30 days")
}

data class NotificationInsights(
    val totalCount: Int = 0,
    val hourlyDistribution: Map<Int, Int> = emptyMap(),
    val appDistributions: List<Pair<String, Int>> = emptyList(),
    val categoryDistributions: Map<NotificationCategory, Int> = emptyMap(),
    val peakHour: Int? = null,
    val peakHourCount: Int = 0,
    val timeRange: InsightsTimeRange = InsightsTimeRange.TODAY
)
