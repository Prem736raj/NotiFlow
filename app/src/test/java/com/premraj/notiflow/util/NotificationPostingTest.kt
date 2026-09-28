package com.premraj.notiflow.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationPostingTest {

    @Test
    fun testSafeNotificationIdIsPositive() {
        val testIds = listOf(
            0L, 1L, -1L, Long.MAX_VALUE, Long.MIN_VALUE,
            1727500000000L, 100_000_000_000L
        )

        for (id in testIds) {
            val notifId = NotificationPosting.safeNotificationId(id, 10_000)
            assertTrue("Expected non-negative ID for $id, got $notifId", notifId >= 0)
        }
    }

    @Test
    fun testSafeNotificationIdDeterministic() {
        val id = 1727500000000L
        val notifId1 = NotificationPosting.safeNotificationId(id, 10_000)
        val notifId2 = NotificationPosting.safeNotificationId(id, 10_000)
        assertEquals(notifId1, notifId2)
    }

    @Test
    fun testDifferentOffsetsProduceDifferentIds() {
        val id = 42L
        val notifId1 = NotificationPosting.safeNotificationId(id, 10_000)
        val notifId2 = NotificationPosting.safeNotificationId(id, 20_000)
        assertNotEquals(notifId1, notifId2)
    }
}
