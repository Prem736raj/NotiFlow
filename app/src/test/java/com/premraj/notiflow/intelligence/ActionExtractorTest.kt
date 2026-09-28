package com.premraj.notiflow.intelligence

import com.premraj.notiflow.data.NotificationCategory
import com.premraj.notiflow.data.NotificationItem
import com.premraj.notiflow.data.NotificationPriority
import com.premraj.notiflow.data.NotificationState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class ActionExtractorTest {

    @Test
    fun testExtractOtpCode_keywordAfter() {
        val code1 = ActionExtractor.extractOtpCode("Your OTP is 482910 for logging in.")
        assertEquals("482910", code1)

        val code2 = ActionExtractor.extractOtpCode("Your verification code is 123456.")
        assertEquals("123456", code2)

        val code3 = ActionExtractor.extractOtpCode("Do not share your one-time password 8392 with anyone.")
        assertEquals("8392", code3)

        val code4 = ActionExtractor.extractOtpCode("Your security code is: 775432")
        assertEquals("775432", code4)
    }

    @Test
    fun testExtractOtpCode_keywordBefore() {
        val code = ActionExtractor.extractOtpCode("391823 is your verification code.")
        assertEquals("391823", code)
    }

    @Test
    fun testExtractOtpCode_instagramBug() {
        // This is the exact Instagram scenario: username contains "0000" but OTP is "608432"
        val instagramText = "Hi 0000I0IIIII, We received a request to use this email address with your Instagram account. Use this code to confirm your identity:\n608432"
        val code = ActionExtractor.extractOtpCode(instagramText)
        assertEquals("608432", code)
    }

    @Test
    fun testExtractOtpCode_instagramVariation() {
        // Variation with code on same line after colon
        val text = "Hi user123, Use this code to confirm your identity: 608432"
        val code = ActionExtractor.extractOtpCode(text)
        assertEquals("608432", code)
    }

    @Test
    fun testExtractOtpCode_codeOnNextLine() {
        val text = "Your verification code:\n847291"
        val code = ActionExtractor.extractOtpCode(text)
        assertEquals("847291", code)
    }

    @Test
    fun testExtractOtpCode_skipUsernameDigits() {
        // Digits embedded in alphanumeric token should be skipped
        val text = "Hello user0000test, your code is 567890"
        val code = ActionExtractor.extractOtpCode(text)
        assertEquals("567890", code)
    }

    @Test
    fun testExtractOtpCode_skipGreetingLine() {
        // Digits in greeting line should be skipped even if standalone
        val text = "Hi 1234,\nYour login code is 567890."
        val code = ActionExtractor.extractOtpCode(text)
        assertEquals("567890", code)
    }

    @Test
    fun testExtractOtpCode_titleAndBody() {
        val code = ActionExtractor.extractOtpCode("Login Alert", "Your OTP is 998877 for login.")
        assertEquals("998877", code)
    }

    @Test
    fun testExtractOtpCode_bodyOnly() {
        val code = ActionExtractor.extractOtpCode(null, "Pin code: 4455")
        assertEquals("4455", code)
    }

    @Test
    fun testExtractOtpCode_noCode() {
        val code = ActionExtractor.extractOtpCode("Your package has been delivered.")
        assertNull(code)
    }

    @Test
    fun testExtractOtpCode_confirmationCode() {
        val code = ActionExtractor.extractOtpCode("Your confirmation code is 223344.")
        assertEquals("223344", code)
    }

    @Test
    fun testExtractOtpCode_standaloneCodeOnOwnLine() {
        val text = "Enter the code below to verify:\n\n  887766  \n\nThis code expires in 10 minutes."
        val code = ActionExtractor.extractOtpCode(text)
        assertEquals("887766", code)
    }

    @Test
    fun trackingNumberIsNotOfferedAsPhoneCall() {
        val actions = ActionExtractor.extract(item("Order tracking number 9876543210 is in transit"))
        assertFalse(actions.any { it is SmartAction.Call })
    }

    @Test
    fun contextualPhoneNumberIsOfferedAsPhoneCall() {
        val actions = ActionExtractor.extract(item("Call courier at 9876543210 for delivery help"))
        val call = actions.filterIsInstance<SmartAction.Call>().single()
        assertEquals("9876543210", call.phone)
    }

    @Test
    fun genericMarketOrStationWordsDoNotTriggerMaps() {
        val actions = ActionExtractor.extract(item("Package reached the local market station and is processing"))
        assertFalse(actions.any { it is SmartAction.OpenMap })
    }

    @Test
    fun structuredAddressTriggersMaps() {
        val actions = ActionExtractor.extract(item("Deliver to Sector 17, Chandigarh tomorrow"))
        assertTrue(actions.any { it is SmartAction.OpenMap })
    }

    @Test
    fun calendarActionKeepsExplicitTime() {
        val actions = ActionExtractor.extract(item("Meeting on 30 Sep 2026 at 3:45 PM"))
        val calendarAction = actions.filterIsInstance<SmartAction.AddCalendar>().single()
        val calendar = Calendar.getInstance().apply { timeInMillis = calendarAction.startAt }
        assertEquals(15, calendar.get(Calendar.HOUR_OF_DAY))
        assertEquals(45, calendar.get(Calendar.MINUTE))
    }

    private fun item(body: String): NotificationItem = NotificationItem(
        id = 1L,
        notificationKey = "action-test",
        packageName = "com.example",
        appName = "Example",
        title = "Update",
        body = body,
        sender = null,
        postedAt = 1_000_000L,
        updatedAt = 1_000_000L,
        removedAt = null,
        isActiveOnSystem = true,
        category = NotificationCategory.OTHER,
        priority = NotificationPriority.NORMAL,
        confidence = 0.8f,
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

