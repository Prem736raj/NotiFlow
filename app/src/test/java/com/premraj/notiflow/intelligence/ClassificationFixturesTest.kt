package com.premraj.notiflow.intelligence

import com.premraj.notiflow.data.NotificationCategory
import com.premraj.notiflow.data.NotificationItem
import com.premraj.notiflow.data.NotificationPriority
import com.premraj.notiflow.data.NotificationState
import com.premraj.notiflow.data.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * P1 Roadmap Classification Fixtures Suite:
 *
 * Validates synthetic and redacted real-world notification fixtures:
 * 1. OTP protection (zero unsafe focus suppression, high priority).
 * 2. Financial transactions (accurate debit extraction, high priority, zero suppression).
 * 3. Payment due / bill alerts (zero mistaken debit expenses).
 * 4. Low-value promotional containment.
 * 5. Pinned and VIP override safety.
 */
class ClassificationFixturesTest {

    private fun buildItem(
        id: Long = 1L,
        pkg: String,
        appName: String = "TestApp",
        title: String?,
        body: String?,
        sender: String? = null,
        pinned: Boolean = false,
        isVip: Boolean = false,
        category: NotificationCategory = NotificationCategory.OTHER,
        priority: NotificationPriority = NotificationPriority.NORMAL
    ): NotificationItem {
        return NotificationItem(
            id = id,
            notificationKey = "key_$id",
            packageName = pkg,
            appName = appName,
            title = title,
            body = body,
            sender = sender,
            postedAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis(),
            removedAt = null,
            isActiveOnSystem = true,
            category = category,
            priority = priority,
            confidence = 0.95f,
            categoryOverridden = false,
            priorityOverridden = false,
            state = NotificationState.ACTIVE,
            remindAt = null,
            pinned = pinned,
            isVip = isVip,
            read = false,
            amountHint = null
        )
    }

    @Test
    fun testSyntheticOtpFixturesNeverSuppressed() {
        val otpSamples = listOf(
            Triple("State Bank of India", "SBI", "Your OTP for login to Internet Banking is 482910. Do not share with anyone."),
            Triple("HDFC Bank", "HDFCBK", "382914 is your NetBanking OTP for transaction of Rs 1,200.00 at Swiggy."),
            Triple("ICICI Bank", "ICICIB", "OTP 902811 for card transaction. Valid for 3 mins. Never share OTP."),
            Triple("Axis Bank", "AxisBk", "Your Axis Bank OTP is 771239 for registering UPI on GPay."),
            Triple("Google", "Google", "G-839201 is your Google verification code."),
            Triple("Uber", "Uber", "Your Uber verification code is 5912. Never share this code."),
            Triple("Swiggy", "Swiggy", "4920 is your Swiggy login code. Valid for 5 mins.")
        )

        otpSamples.forEachIndexed { index, (app, sender, text) ->
            val code = ActionExtractor.extractOtpCode(sender, text)
            assertNotNull("OTP code must be extracted for $app: $text", code)

            val fast = LocalIntelligence.classify(
                packageName = "com.sample.bank$index",
                title = sender,
                body = text,
                sender = sender
            )
            assertEquals("Must classify as OTP for $app", NotificationCategory.OTP, fast.category)
            assertEquals("OTP must be HIGH priority for $app", NotificationPriority.HIGH, fast.priority)

            val item = buildItem(
                id = 100L + index,
                pkg = "com.sample.bank$index",
                appName = app,
                title = sender,
                body = text,
                category = fast.category,
                priority = fast.priority
            )

            // CRITICAL SAFETY GUARD: Must NEVER be safe for suppression
            assertFalse(
                "OTP from $app must never be safe for suppression!",
                LocalIntelligence.isSafeLowValueForSuppression(item)
            )
        }
    }

    @Test
    fun testSyntheticDebitTransactionsExtractedAndNeverSuppressed() {
        val debitSamples = listOf(
            "Acct XX1234 debited by Rs. 540.00 on 20-Oct-26 via UPI to ZOMATO. Ref 928301.",
            "Rs 1,250.00 debited from A/c 5678 at HPCL FUEL PUMP on 21-Oct. Avl Bal Rs 15,200.",
            "INR 4,500.00 withdrawn from ATM using Debit Card XX9900 on 21-Oct-2026.",
            "Paid Rs. 350 to Chai Point via Paytm UPI. Transaction ID: 109283."
        )

        debitSamples.forEachIndexed { index, sample ->
            val parsed = ExpenseParser.parse(
                title = "Bank Alert",
                body = sample,
                packageName = "com.sample.bank",
                appName = "Bank App"
            )
            assertNotNull("Must parse debit transaction: $sample", parsed)
            assertEquals(TransactionType.DEBIT, parsed!!.transactionType)
            assertTrue("Amount must be positive", parsed.amount > 0.0)

            val item = buildItem(
                id = 200L + index,
                pkg = "com.bank.debit",
                title = "Bank Alert",
                body = sample,
                category = NotificationCategory.PAYMENT,
                priority = NotificationPriority.HIGH
            )

            // CRITICAL SAFETY GUARD: Financial transactions must NEVER be safe for suppression
            assertFalse(
                "Payment transaction must never be safe for suppression!",
                LocalIntelligence.isSafeLowValueForSuppression(item)
            )
        }
    }

    @Test
    fun testPaymentDueRemindersDoNotCreateDebitTransactions() {
        val billAlerts = listOf(
            "Dear Customer, your credit card bill of Rs 8,450.00 is due on 25-Oct-2026. Pay now to avoid charges.",
            "Your Airtel postpaid bill of Rs 599 is generated. Due date is 28-Oct. Click to pay.",
            "Electricity bill payment reminder: Rs 1,420 due by tomorrow for Consumer No 82910."
        )

        billAlerts.forEach { alert ->
            val parsed = ExpenseParser.parse(
                title = "Bill Reminder",
                body = alert,
                packageName = "com.sample.telecom",
                appName = "Telecom App"
            )
            // Bill reminders that have not been debited must NOT produce a false debit expense!
            assertNull("Bill reminder must not be registered as an executed debit: $alert", parsed)
        }
    }

    @Test
    fun testPromotionsAndSpamAreLowValueForSuppression() {
        val promos = listOf(
            "MEGA SALE! 50% off on all footwear. Use code DIWALI50 now!",
            "Flash Deal: Flat 70% discount on electronics. Offer ends in 2 hours.",
            "Congratulations! You won a cash prize of 1 Lakh! Click here to claim your reward."
        )

        promos.forEachIndexed { index, promo ->
            val fast = LocalIntelligence.classify(
                packageName = "com.shopping.deals",
                title = "Hot Deals",
                body = promo,
                sender = null
            )
            assertTrue(
                "Promo must be classified as PROMOTION or SPAM",
                fast.category == NotificationCategory.PROMOTION || fast.category == NotificationCategory.SPAM
            )
            assertEquals(NotificationPriority.LOW, fast.priority)

            val item = buildItem(
                id = 300L + index,
                pkg = "com.shopping.deals",
                title = "Hot Deals",
                body = promo,
                category = fast.category,
                priority = fast.priority
            )

            assertTrue(
                "Unpinned low-priority promo should be safe for suppression",
                LocalIntelligence.isSafeLowValueForSuppression(item)
            )
        }
    }

    @Test
    fun testPinnedOrVipNotificationsAreNeverSuppressedEvenIfPromotional() {
        val promoText = "FLAT 60% OFF everything this weekend only!"

        val pinnedPromo = buildItem(
            id = 401L,
            pkg = "com.deal.app",
            title = "Deal",
            body = promoText,
            pinned = true,
            isVip = false,
            category = NotificationCategory.PROMOTION,
            priority = NotificationPriority.LOW
        )
        assertFalse(
            "Pinned item must never be suppressed!",
            LocalIntelligence.isSafeLowValueForSuppression(pinnedPromo)
        )

        val vipPromo = buildItem(
            id = 402L,
            pkg = "com.deal.app",
            title = "Deal",
            body = promoText,
            pinned = false,
            isVip = true,
            category = NotificationCategory.PROMOTION,
            priority = NotificationPriority.LOW
        )
        assertFalse(
            "VIP item must never be suppressed!",
            LocalIntelligence.isSafeLowValueForSuppression(vipPromo)
        )
    }
}
