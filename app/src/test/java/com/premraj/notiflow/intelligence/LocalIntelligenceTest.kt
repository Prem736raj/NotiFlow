package com.premraj.notiflow.intelligence

import com.premraj.notiflow.data.NotificationCategory
import com.premraj.notiflow.data.NotificationItem
import com.premraj.notiflow.data.NotificationPriority
import com.premraj.notiflow.data.NotificationState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
    fun extractsSuffixCurrencyAmounts() {
        listOf(
            "Payment of 500 INR completed",
            "Payment of 500 rs completed",
            "Payment of 500 rupees completed"
        ).forEach { body ->
            val result = LocalIntelligence.classify(
                packageName = "com.example.bank",
                title = "Bank alert",
                body = body,
                sender = null
            )
            assertEquals(500.0, result.amountHint ?: 0.0, 0.001)
        }
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
        assertTrue(LocalIntelligence.isSafeLowValueForSuppression(itemFrom(result)))
    }

    @Test
    fun testOtpExpiryDowngradesPriority() {
        val postedAt = 1_000_000L
        val otpItem = NotificationItem(
            id = 1L,
            notificationKey = "otp_key",
            packageName = "com.google.android.apps.authenticator2",
            appName = "Authenticator",
            title = "Your login code",
            body = "OTP: 123456",
            sender = null,
            postedAt = postedAt,
            updatedAt = postedAt,
            removedAt = null,
            isActiveOnSystem = true,
            category = NotificationCategory.OTP,
            priority = NotificationPriority.HIGH,
            confidence = 0.99f,
            categoryOverridden = false,
            priorityOverridden = false,
            state = NotificationState.ACTIVE,
            remindAt = null,
            pinned = false,
            isVip = false,
            read = false,
            amountHint = null
        )

        // 10 minutes later (less than 15 mins) -> still HIGH
        val tenMinsLater = postedAt + 10 * 60_000L
        assertEquals(NotificationPriority.HIGH, LocalIntelligence.effectivePriority(otpItem, tenMinsLater))

        // 16 minutes later (greater than 15 mins) -> downgraded to NORMAL
        val sixteenMinsLater = postedAt + 16 * 60_000L
        assertEquals(NotificationPriority.NORMAL, LocalIntelligence.effectivePriority(otpItem, sixteenMinsLater))
    }

    @Test
    fun testPaymentFraudAlertIsHighPriority() {
        val result = LocalIntelligence.classify(
            packageName = "com.bank.app",
            title = "Security Alert",
            body = "Suspicious transaction detected on card ending in 9876",
            sender = "Fraud Protection"
        )
        assertEquals(NotificationCategory.PAYMENT, result.category)
        assertEquals(NotificationPriority.HIGH, result.priority)
    }

    @Test
    fun testWorkDeadlineUrgentIsHighPriority() {
        val result = LocalIntelligence.classify(
            packageName = "com.atlassian.jira",
            title = "Jira Ticket",
            body = "URGENT deadline today for project release",
            sender = "Scrum Master"
        )
        assertEquals(NotificationCategory.WORK_STUDY, result.category)
        assertEquals(NotificationPriority.HIGH, result.priority)
    }

    @Test
    fun testDeliveryClassification() {
        val result = LocalIntelligence.classify(
            packageName = "in.amazon.mShop.android.shopping",
            title = "Amazon",
            body = "Your package is out for delivery with courier",
            sender = null
        )
        assertEquals(NotificationCategory.DELIVERY, result.category)
    }

    @Test
    fun testSocialClassificationLowPriority() {
        val result = LocalIntelligence.classify(
            packageName = "com.instagram.android",
            title = "Instagram",
            body = "Jane liked your reel",
            sender = null
        )
        assertEquals(NotificationCategory.SOCIAL, result.category)
        assertEquals(NotificationPriority.LOW, result.priority)
        assertTrue(LocalIntelligence.isSafeLowValueForSuppression(itemFrom(result)))
        assertFalse(
            LocalIntelligence.isSafeLowValueForSuppression(
                itemFrom(result.copy(confidence = 0.69f))
            )
        )
    }

    @Test
    fun packageNameDoesNotContaminateSemanticClassification() {
        val result = LocalIntelligence.classify(
            packageName = "com.example.bank.promotions",
            title = "Status update",
            body = "Everything is ready",
            sender = null
        )

        assertEquals(NotificationCategory.OTHER, result.category)
    }

    @Test
    fun aiRefinementUsesAmbiguityInsteadOfConfidenceThreshold() {
        val fallback = LocalIntelligence.classify(
            packageName = "com.discord",
            title = "Order update",
            body = "Your package shipped and is arriving tomorrow",
            sender = "Store"
        )

        assertTrue(
            LocalIntelligence.shouldRefineWithAi(
                packageName = "com.discord",
                title = "Order update",
                body = "Your package shipped and is arriving tomorrow",
                sender = "Store",
                fallback = fallback
            )
        )

        val payment = LocalIntelligence.classify(
            packageName = "com.example.bank",
            title = "Debit alert",
            body = "Rs. 500 debited from your account",
            sender = null
        )
        assertFalse(
            LocalIntelligence.shouldRefineWithAi(
                packageName = "com.example.bank",
                title = "Debit alert",
                body = "Rs. 500 debited from your account",
                sender = null,
                fallback = payment
            )
        )
    }

    @Test
    fun vipPriorityIsAnEffectiveOverlay() {
        val item = NotificationItem(
            id = 2L,
            notificationKey = "vip_key",
            packageName = "com.example.chat",
            appName = "Chat",
            title = "Hello",
            body = "Checking in",
            sender = "Dad",
            postedAt = 1_000_000L,
            updatedAt = 1_000_000L,
            removedAt = null,
            isActiveOnSystem = true,
            category = NotificationCategory.MESSAGE,
            priority = NotificationPriority.NORMAL,
            confidence = 0.8f,
            categoryOverridden = false,
            priorityOverridden = false,
            state = NotificationState.ACTIVE,
            remindAt = null,
            pinned = false,
            isVip = true,
            read = false,
            amountHint = null
        )

        assertEquals(NotificationPriority.HIGH, LocalIntelligence.effectivePriority(item))
        assertEquals(NotificationPriority.NORMAL, item.priority)
    }

    private fun itemFrom(result: com.premraj.notiflow.data.ClassificationResult): NotificationItem {
        return NotificationItem(
            id = 99L,
            notificationKey = "test-key",
            packageName = "com.example",
            appName = "Example",
            title = "Test",
            body = "Test body",
            sender = null,
            postedAt = 1_000_000L,
            updatedAt = 1_000_000L,
            removedAt = null,
            isActiveOnSystem = true,
            category = result.category,
            priority = result.priority,
            confidence = result.confidence,
            categoryOverridden = false,
            priorityOverridden = false,
            state = NotificationState.ACTIVE,
            remindAt = null,
            pinned = false,
            isVip = false,
            read = false,
            amountHint = result.amountHint
        )
    }
}
