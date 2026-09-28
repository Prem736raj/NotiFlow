package com.premraj.notiflow.intelligence

import com.premraj.notiflow.data.ClassificationResult
import com.premraj.notiflow.data.NotificationCategory
import com.premraj.notiflow.data.NotificationItem
import com.premraj.notiflow.data.NotificationPriority

object LocalIntelligence {
    private val otpRegex = Regex("(?i)\\b(?:otp|one[ -]?time password|verification code|security code|login code|auth code)\\b")
    private val amountRegex = Regex("(?i)(?:₹|rs\\.?|inr|usd|\\$)\\s*([0-9][0-9,]*(?:\\.[0-9]{1,2})?)")
    private val paymentRegex = Regex("(?i)\\b(?:debited|credited|transaction|payment|upi|bank|account|card|paid|refund|balance|emi|autopay|mandate)\\b")
    private val paymentAlertRegex = Regex("(?i)\\b(?:declined|failed|fraud|suspicious|blocked|unauthori[sz]ed|overdue|due today)\\b")
    private val explicitTransactionRegex = Regex("(?i)\\b(?:debited|credited|upi (?:payment|transaction)|card (?:payment|transaction)|refund (?:received|processed))\\b")
    private val deliveryRegex = Regex("(?i)\\b(?:delivery|delivered|out for delivery|shipped|shipment|order|courier|package|tracking|dispatch|arriving)\\b")
    private val messageRegex = Regex("(?i)\\b(?:message|replied|sent you|mentioned you|dm|chat|whatsapp|telegram|signal)\\b")
    private val workRegex = Regex("(?i)\\b(?:meeting|assignment|deadline|class|lecture|exam|project|task|github|jira|slack|teams|workspace|school|college|university)\\b")
    private val reminderRegex = Regex("(?i)\\b(?:reminder|appointment|event|calendar|starts? at|due|scheduled|reservation|booking)\\b")
    private val socialRegex = Regex("(?i)\\b(?:liked|followed|commented|subscribed|story|stories|reel|post|instagram|facebook|reddit|snapchat|youtube)\\b")
    private val promotionRegex = Regex("(?i)\\b(?:sale|offer|discount|coupon|deal|cashback|save \\d|% off|limited time|shop now|promo|promotion)\\b")
    private val spamRegex = Regex("(?i)\\b(?:you won|winner|claim prize|lottery|free money|urgent loan|click immediately)\\b")

    fun classify(packageName: String, title: String?, body: String?, sender: String?): ClassificationResult {
        val text = semanticText(title, body, sender)
        val amount = amountRegex.find(text)?.groupValues?.getOrNull(1)?.replace(",", "")?.toDoubleOrNull()

        val category = when {
            otpRegex.containsMatchIn(text) -> NotificationCategory.OTP
            paymentRegex.containsMatchIn(text) -> NotificationCategory.PAYMENT
            deliveryRegex.containsMatchIn(text) -> NotificationCategory.DELIVERY
            workRegex.containsMatchIn(text) -> NotificationCategory.WORK_STUDY
            reminderRegex.containsMatchIn(text) -> NotificationCategory.REMINDER_EVENT
            promotionRegex.containsMatchIn(text) -> NotificationCategory.PROMOTION
            spamRegex.containsMatchIn(text) -> NotificationCategory.SPAM
            messageRegex.containsMatchIn(text) || looksLikeMessagingPackage(packageName) -> NotificationCategory.MESSAGE
            socialRegex.containsMatchIn(text) || looksLikeSocialPackage(packageName) -> NotificationCategory.SOCIAL
            else -> NotificationCategory.OTHER
        }

        val priority = when {
            paymentAlertRegex.containsMatchIn(text) -> NotificationPriority.HIGH
            category == NotificationCategory.OTP -> NotificationPriority.HIGH
            category == NotificationCategory.WORK_STUDY && Regex("(?i)\\b(?:urgent|deadline|today|now|overdue)\\b").containsMatchIn(text) -> NotificationPriority.HIGH
            category == NotificationCategory.REMINDER_EVENT && Regex("(?i)\\b(?:today|now|starts? in|due)\\b").containsMatchIn(text) -> NotificationPriority.HIGH
            category == NotificationCategory.PROMOTION || category == NotificationCategory.SPAM || category == NotificationCategory.SOCIAL -> NotificationPriority.LOW
            else -> NotificationPriority.NORMAL
        }

        val confidence = when (category) {
            NotificationCategory.OTHER -> 0.42f
            NotificationCategory.MESSAGE, NotificationCategory.SOCIAL -> 0.70f
            else -> 0.88f
        }

        return ClassificationResult(
            category = category,
            priority = priority,
            confidence = confidence,
            amountHint = amount,
            reason = "Fast on-device rules"
        )
    }

    fun shouldRefineWithAi(
        packageName: String,
        title: String?,
        body: String?,
        sender: String?,
        fallback: ClassificationResult
    ): Boolean {
        val text = semanticText(title, body, sender)
        if (fallback.category == NotificationCategory.OTP || explicitTransactionRegex.containsMatchIn(text)) return false
        if (fallback.category == NotificationCategory.OTHER || fallback.confidence < 0.65f) return true

        val semanticCategories = buildSet {
            if (paymentRegex.containsMatchIn(text)) add(NotificationCategory.PAYMENT)
            if (deliveryRegex.containsMatchIn(text)) add(NotificationCategory.DELIVERY)
            if (workRegex.containsMatchIn(text)) add(NotificationCategory.WORK_STUDY)
            if (reminderRegex.containsMatchIn(text)) add(NotificationCategory.REMINDER_EVENT)
            if (promotionRegex.containsMatchIn(text)) add(NotificationCategory.PROMOTION)
            if (spamRegex.containsMatchIn(text)) add(NotificationCategory.SPAM)
            if (messageRegex.containsMatchIn(text)) add(NotificationCategory.MESSAGE)
            if (socialRegex.containsMatchIn(text)) add(NotificationCategory.SOCIAL)
        }
        if (semanticCategories.size > 1) return true

        val packageCategory = when {
            looksLikeMessagingPackage(packageName) -> NotificationCategory.MESSAGE
            looksLikeSocialPackage(packageName) -> NotificationCategory.SOCIAL
            else -> null
        }
        return packageCategory != null && semanticCategories.isNotEmpty() && packageCategory !in semanticCategories
    }

    fun effectivePriority(item: NotificationItem, now: Long = System.currentTimeMillis()): NotificationPriority {
        if (item.isVip) return NotificationPriority.HIGH
        if (item.priority != NotificationPriority.HIGH) return item.priority
        if (item.category == NotificationCategory.OTP && now - item.postedAt > 15 * 60_000L) {
            return NotificationPriority.NORMAL
        }
        return item.priority
    }

    private fun semanticText(title: String?, body: String?, sender: String?): String =
        listOf(title, body, sender).filterNotNull().joinToString(" ").trim()

    private fun looksLikeMessagingPackage(packageName: String): Boolean {
        val p = packageName.lowercase()
        return listOf("whatsapp", "telegram", "signal", "messenger", "messages", "discord").any(p::contains)
    }

    private fun looksLikeSocialPackage(packageName: String): Boolean {
        val p = packageName.lowercase()
        return listOf("instagram", "facebook", "reddit", "snapchat", "twitter", "youtube").any(p::contains)
    }
}
