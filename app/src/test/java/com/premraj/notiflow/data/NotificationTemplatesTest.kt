package com.premraj.notiflow.data

import com.premraj.notiflow.testutil.FakeSharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class NotificationTemplatesTest {

    private lateinit var fakePrefs: FakeSharedPreferences
    private lateinit var userPrefs: UserPreferences

    @Before
    fun setUp() {
        fakePrefs = FakeSharedPreferences()
        userPrefs = UserPreferences(fakePrefs)
    }

    @Test
    fun testAllPresetTemplatesExist() {
        val templates = NotificationTemplates.getAvailableTemplates(userPrefs)
        assertEquals(4, templates.size)

        val ids = templates.map { it.id }
        assertTrue(ids.contains(NotificationTemplates.ID_BANK_PROTECT))
        assertTrue(ids.contains(NotificationTemplates.ID_PROMO_DIGEST))
        assertTrue(ids.contains(NotificationTemplates.ID_DELIVERY_REMIND))
        assertTrue(ids.contains(NotificationTemplates.ID_WORK_HOURS))
    }

    @Test
    fun testBankOtpProtectTemplateEnablesProtections() {
        userPrefs.setQuietCategory(NotificationCategory.OTP, quiet = true)
        userPrefs.setQuietCategory(NotificationCategory.PAYMENT, quiet = true)
        assertTrue(userPrefs.quietCategories().contains(NotificationCategory.OTP))
        assertTrue(userPrefs.quietCategories().contains(NotificationCategory.PAYMENT))

        NotificationTemplates.applyTemplate(userPrefs, NotificationTemplates.ID_BANK_PROTECT, enable = true)
        assertTrue(userPrefs.isTemplateEnabled(NotificationTemplates.ID_BANK_PROTECT, default = true))
        assertFalse(userPrefs.quietCategories().contains(NotificationCategory.OTP))
        assertFalse(userPrefs.quietCategories().contains(NotificationCategory.PAYMENT))
    }

    @Test
    fun testPromoDigestTemplateTogglesPromotionAndSpamQuietCategories() {
        NotificationTemplates.applyTemplate(userPrefs, NotificationTemplates.ID_PROMO_DIGEST, enable = true)
        val quietCategories = userPrefs.quietCategories()
        assertTrue(quietCategories.contains(NotificationCategory.PROMOTION))
        assertTrue(quietCategories.contains(NotificationCategory.SPAM))

        NotificationTemplates.applyTemplate(userPrefs, NotificationTemplates.ID_PROMO_DIGEST, enable = false)
        val updatedCategories = userPrefs.quietCategories()
        assertFalse(updatedCategories.contains(NotificationCategory.PROMOTION))
        assertFalse(updatedCategories.contains(NotificationCategory.SPAM))
    }

    @Test
    fun testWorkHoursTemplateTogglesWorkFocusScheduled() {
        assertFalse(userPrefs.workFocusScheduled)
        NotificationTemplates.applyTemplate(userPrefs, NotificationTemplates.ID_WORK_HOURS, enable = true)
        assertTrue(userPrefs.workFocusScheduled)

        NotificationTemplates.applyTemplate(userPrefs, NotificationTemplates.ID_WORK_HOURS, enable = false)
        assertFalse(userPrefs.workFocusScheduled)
    }

    @Test
    fun testDeliveryRemindTemplateTogglesEnabledState() {
        NotificationTemplates.applyTemplate(userPrefs, NotificationTemplates.ID_DELIVERY_REMIND, enable = false)
        assertFalse(userPrefs.isTemplateEnabled(NotificationTemplates.ID_DELIVERY_REMIND, default = true))

        NotificationTemplates.applyTemplate(userPrefs, NotificationTemplates.ID_DELIVERY_REMIND, enable = true)
        assertTrue(userPrefs.isTemplateEnabled(NotificationTemplates.ID_DELIVERY_REMIND, default = true))
    }
}
