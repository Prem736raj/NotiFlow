package com.premraj.notiflow.intelligence

import com.premraj.notiflow.data.NotificationCategory
import com.premraj.notiflow.data.NotificationPriority
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalIntelligenceTest {
    @Test
    fun classifiesPaymentAndExtractsAmount() {
        val result = LocalIntelligence.classify(
            packageName = "com.example.bank",
            title = "Bank alert",
            body = "Rs. 2,000 debited from your account",
            sender = null
        )
        assertEquals(NotificationCategory.PAYMENT, result.category)
        assertEquals(2000.0, result.amountHint ?: 0.0, 0.001)
    }

    @Test
    fun otpIsTimeSensitive() {
        val result = LocalIntelligence.classify(
            packageName = "com.example.app",
            title = "Verification",
            body = "Your OTP is 482991",
            sender = null
        )
        assertEquals(NotificationCategory.OTP, result.category)
        assertEquals(NotificationPriority.HIGH, result.priority)
    }

    @Test
    fun promotionDefaultsLowPriority() {
        val result = LocalIntelligence.classify(
            packageName = "com.example.shop",
            title = "Sale",
            body = "50% off today, shop now",
            sender = null
        )
        assertEquals(NotificationCategory.PROMOTION, result.category)
        assertEquals(NotificationPriority.LOW, result.priority)
    }
}
