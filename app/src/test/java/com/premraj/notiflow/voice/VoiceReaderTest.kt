package com.premraj.notiflow.voice

import com.premraj.notiflow.data.VoiceReaderFilter
import com.premraj.notiflow.data.VoiceReadingDetail
import com.premraj.notiflow.data.VoiceTriggerCondition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class VoiceReaderTest {

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
}
