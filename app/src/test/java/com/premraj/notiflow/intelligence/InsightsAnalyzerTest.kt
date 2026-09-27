package com.premraj.notiflow.intelligence

import com.premraj.notiflow.data.InsightsTimeRange
import com.premraj.notiflow.data.NotificationCategory
import com.premraj.notiflow.data.NotificationItem
import com.premraj.notiflow.data.NotificationPriority
import com.premraj.notiflow.data.NotificationState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.util.Calendar

class InsightsAnalyzerTest {

    private fun createItem(id: Long, pkg: String, name: String, hour: Int, category: NotificationCategory): NotificationItem {
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, 30)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return NotificationItem(
            id = id,
            notificationKey = "key_$id",
            packageName = pkg,
            appName = name,
            title = "Test",
            body = "Body",
            sender = "Sender",
            postedAt = cal.timeInMillis,
            updatedAt = cal.timeInMillis,
            removedAt = null,
            isActiveOnSystem = true,
            category = category,
            priority = NotificationPriority.NORMAL,
            confidence = 0.9f,
            categoryOverridden = false,
            priorityOverridden = false,
            state = NotificationState.ACTIVE,
            remindAt = null,
            pinned = false,
            isVip = false,
            read = true,
            amountHint = null
        )
    }

    @Test
    fun testEmptyNotifications() {
        val insights = InsightsAnalyzer.computeInsights(emptyList(), InsightsTimeRange.TODAY)
        assertEquals(0, insights.totalCount)
        assertEquals(0, insights.appDistributions.size)
        assertEquals(24, insights.hourlyDistribution.size)
    }

    @Test
    fun testInsightsCalculation() {
        val calEnd = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 59)
            set(Calendar.SECOND, 59)
            set(Calendar.MILLISECOND, 999)
        }
        val endOfDay = calEnd.timeInMillis

        val notifications = listOf(
            createItem(1, "com.swiggy", "Swiggy", 13, NotificationCategory.PROMOTION),
            createItem(2, "com.swiggy", "Swiggy", 14, NotificationCategory.PROMOTION),
            createItem(3, "com.swiggy", "Swiggy", 14, NotificationCategory.PROMOTION),
            createItem(4, "com.whatsapp", "WhatsApp", 10, NotificationCategory.MESSAGE),
            createItem(5, "com.whatsapp", "WhatsApp", 11, NotificationCategory.MESSAGE),
            createItem(6, "com.twitter", "X", 23, NotificationCategory.SOCIAL)
        )

        val insights = InsightsAnalyzer.computeInsights(
            notifications = notifications,
            timeRange = InsightsTimeRange.TODAY,
            nowMillis = endOfDay
        )

        assertEquals(6, insights.totalCount)
        assertEquals(2, insights.hourlyDistribution[14])
        assertEquals(14, insights.peakHour)
        assertEquals(2, insights.peakHourCount)
        assertEquals("Swiggy", insights.appDistributions.first().first)
        assertEquals(3, insights.appDistributions.first().second)
    }
}
