package com.premraj.notiflow.data

import android.content.Context
import android.content.ContextWrapper
import com.premraj.notiflow.testutil.FakeSharedPreferences
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class MetricsTrackerTest {

    private lateinit var fakePrefs: FakeSharedPreferences
    private lateinit var dummyContext: Context

    @Before
    fun setUp() {
        fakePrefs = FakeSharedPreferences()
        MetricsTracker.prefsProvider = { fakePrefs }
        dummyContext = object : ContextWrapper(null) {}
    }

    @After
    fun tearDown() {
        MetricsTracker.prefsProvider = null
    }

    @Test
    fun testKnownEventsContainsAllFunnelTaxonomy() {
        val events = MetricsTracker.KNOWN_EVENTS
        assertEquals(9, events.size)
        assertTrue(events.contains(MetricsTracker.EVENT_DISCLOSURE_SHOWN))
        assertTrue(events.contains(MetricsTracker.EVENT_PERMISSION_ENABLED))
        assertTrue(events.contains(MetricsTracker.EVENT_CAPTURE_VERIFIED))
        assertTrue(events.contains(MetricsTracker.EVENT_NOTIFICATION_MARKED_LATER))
        assertTrue(events.contains(MetricsTracker.EVENT_REMINDER_SCHEDULED))
        assertTrue(events.contains(MetricsTracker.EVENT_REMINDER_DELIVERED))
        assertTrue(events.contains(MetricsTracker.EVENT_NOTIFICATION_RETRIEVED))
        assertTrue(events.contains(MetricsTracker.EVENT_LISTENER_DISCONNECTED))
        assertTrue(events.contains(MetricsTracker.EVENT_PERMISSION_REVOKED))
    }

    @Test
    fun testRecordAndGetEventCount() {
        assertEquals(0L, MetricsTracker.getEventCount(dummyContext, MetricsTracker.EVENT_CAPTURE_VERIFIED))

        MetricsTracker.recordEvent(dummyContext, MetricsTracker.EVENT_CAPTURE_VERIFIED)
        assertEquals(1L, MetricsTracker.getEventCount(dummyContext, MetricsTracker.EVENT_CAPTURE_VERIFIED))

        MetricsTracker.recordEvent(dummyContext, MetricsTracker.EVENT_CAPTURE_VERIFIED)
        assertEquals(2L, MetricsTracker.getEventCount(dummyContext, MetricsTracker.EVENT_CAPTURE_VERIFIED))
    }

    @Test
    fun testGetAllEventCounts() {
        MetricsTracker.recordEvent(dummyContext, MetricsTracker.EVENT_DISCLOSURE_SHOWN)
        MetricsTracker.recordEvent(dummyContext, MetricsTracker.EVENT_PERMISSION_ENABLED)

        val counts = MetricsTracker.getAllEventCounts(dummyContext)
        assertEquals(9, counts.size)
        assertEquals(1L, counts[MetricsTracker.EVENT_DISCLOSURE_SHOWN])
        assertEquals(1L, counts[MetricsTracker.EVENT_PERMISSION_ENABLED])
        assertEquals(0L, counts[MetricsTracker.EVENT_CAPTURE_VERIFIED])
    }

    @Test
    fun testClearMetrics() {
        MetricsTracker.recordEvent(dummyContext, MetricsTracker.EVENT_NOTIFICATION_MARKED_LATER)
        assertEquals(1L, MetricsTracker.getEventCount(dummyContext, MetricsTracker.EVENT_NOTIFICATION_MARKED_LATER))

        MetricsTracker.clearMetrics(dummyContext)
        assertEquals(0L, MetricsTracker.getEventCount(dummyContext, MetricsTracker.EVENT_NOTIFICATION_MARKED_LATER))
    }

    @Test
    fun testZeroPiiStored() {
        MetricsTracker.KNOWN_EVENTS.forEach { event ->
            MetricsTracker.recordEvent(dummyContext, event)
        }
        val allStored = fakePrefs.all
        for ((key, value) in allStored) {
            assertTrue("Key $key must be one of KNOWN_EVENTS", MetricsTracker.KNOWN_EVENTS.contains(key))
            assertTrue("Value must be a Long integer counter", value is Long)
            assertFalse("Key $key must not contain personal info", key.contains("@") || key.contains("http"))
        }
    }
}
