package com.premraj.notiflow.voice

import com.premraj.notiflow.data.NotificationCategory
import com.premraj.notiflow.data.NotificationItem
import com.premraj.notiflow.data.NotificationPriority
import com.premraj.notiflow.data.NotificationState
import com.premraj.notiflow.data.VoiceReaderFilter
import com.premraj.notiflow.data.VoiceReadingDetail
import com.premraj.notiflow.data.VoiceTriggerCondition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class VoiceReaderTest {

    private fun createItem(
        appName: String = "WhatsApp",
        sender: String? = "Alice",
        title: String? = "Alice",
        body: String? = "Hey there, are we still meeting?"
    ): NotificationItem {
        return NotificationItem(
            id = 1L,
            notificationKey = "key_1",
            packageName = "com.whatsapp",
            appName = appName,
            title = title,
            body = body,
            sender = sender,
            postedAt = 1000L,
            updatedAt = 1000L,
            removedAt = null,
            isActiveOnSystem = true,
            category = NotificationCategory.MESSAGE,
            priority = NotificationPriority.NORMAL,
            confidence = 0.95f,
            categoryOverridden = false,
            priorityOverridden = false,
            state = NotificationState.ACTIVE,
            remindAt = null,
            pinned = false,
            isVip = false,
            read = false,
            amountHint = null
        )
    }

    @Test
    fun testVoiceTriggerConditions() {
        val conditions = VoiceTriggerCondition.entries
        assertEquals(3, conditions.size)
        assertNotNull(VoiceTriggerCondition.valueOf("HEADPHONES_ONLY"))
        assertNotNull(VoiceTriggerCondition.valueOf("DRIVING_OR_HEADPHONES"))
        assertNotNull(VoiceTriggerCondition.valueOf("ALWAYS"))
    }

    @Test
    fun testVoiceReaderFilters() {
        val filters = VoiceReaderFilter.entries
        assertEquals(3, filters.size)
        assertNotNull(VoiceReaderFilter.valueOf("VIP_ONLY"))
        assertNotNull(VoiceReaderFilter.valueOf("MESSAGES_ONLY"))
        assertNotNull(VoiceReaderFilter.valueOf("ALL_EXCEPT_OTP"))
    }

    @Test
    fun testVoiceReadingDetails() {
        val details = VoiceReadingDetail.entries
        assertEquals(2, details.size)
        assertNotNull(VoiceReadingDetail.valueOf("SENDER_ONLY"))
        assertNotNull(VoiceReadingDetail.valueOf("FULL_MESSAGE"))
    }

    @Test
    fun testBuildAnnouncementSenderOnly() {
        val withSender = createItem(appName = "Signal", sender = "Bob")
        val announcement1 = VoiceReaderPolicy.buildAnnouncement(withSender, VoiceReadingDetail.SENDER_ONLY)
        assertEquals("Signal from Bob", announcement1)

        val withoutSender = createItem(appName = "Calendar", sender = null)
        val announcement2 = VoiceReaderPolicy.buildAnnouncement(withoutSender, VoiceReadingDetail.SENDER_ONLY)
        assertEquals("Calendar", announcement2)
    }

    @Test
    fun testBuildAnnouncementFullMessage() {
        val withSender = createItem(appName = "Slack", sender = "Lead", body = "Build passed!\nLet's deploy.")
        val announcement = VoiceReaderPolicy.buildAnnouncement(withSender, VoiceReadingDetail.FULL_MESSAGE)
        assertEquals("Slack from Lead: Build passed! Let's deploy.", announcement)

        val withoutSender = createItem(appName = "Weather", sender = null, body = "Rain expected at 3 PM")
        val announcementNoSender = VoiceReaderPolicy.buildAnnouncement(withoutSender, VoiceReadingDetail.FULL_MESSAGE)
        assertEquals("Weather: Rain expected at 3 PM", announcementNoSender)
    }

    @Test
    fun testBuildAnnouncementFallbackAppName() {
        val blankApp = createItem(appName = "", sender = null, body = null)
        val announcement = VoiceReaderPolicy.buildAnnouncement(blankApp, VoiceReadingDetail.FULL_MESSAGE)
        assertEquals("New notification", announcement)
    }

    @Test
    fun testBuildAnnouncementTruncation() {
        val longBody = "A".repeat(200)
        val item = createItem(appName = "App", sender = "Sender", body = longBody)
        val announcement = VoiceReaderPolicy.buildAnnouncement(item, VoiceReadingDetail.FULL_MESSAGE)
        assertEquals("App from Sender: " + "A".repeat(120), announcement)
    }
}
