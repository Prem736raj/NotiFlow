package com.premraj.notiflow.intelligence

import com.premraj.notiflow.data.NotificationCategory
import com.premraj.notiflow.data.NotificationPriority
import com.premraj.notiflow.data.NotificationState
import java.util.Calendar


data class SearchIntent(
    val freeText: String,
    val from: Long? = null,
    val until: Long? = null,
    val category: NotificationCategory? = null,
    val priority: NotificationPriority? = null,
    val state: NotificationState? = null,
    val amount: Double? = null
)

object SearchInterpreter {
    private val amountRegex = Regex("(?i)(?:₹|rs\\.?|inr)?\\s*([0-9][0-9,]*(?:\\.[0-9]{1,2})?)\\s*(?:rupees?|inr)?")

    fun interpret(query: String, now: Long = System.currentTimeMillis()): SearchIntent {
        val normalized = query.trim().lowercase()
        var from: Long? = null
        var until: Long? = null

        when {
            "yesterday" in normalized -> {
                val c = startOfDay(now)
                until = c
                from = c - 86_400_000L
            }
            "today" in normalized -> from = startOfDay(now)
            "last week" in normalized -> from = now - 7 * 86_400_000L
            "this week" in normalized -> {
                val c = Calendar.getInstance().apply {
                    timeInMillis = now
                    set(Calendar.DAY_OF_WEEK, firstDayOfWeek)
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                from = c.timeInMillis
            }
        }

        val category = when {
            listOf("otp", "verification", "code").any(normalized::contains) -> NotificationCategory.OTP
            listOf("payment", "bank", "banking", "transaction", "upi").any(normalized::contains) -> NotificationCategory.PAYMENT
            listOf("delivery", "package", "order", "shipment").any(normalized::contains) -> NotificationCategory.DELIVERY
            listOf("message", "messages", "chat").any(normalized::contains) -> NotificationCategory.MESSAGE
            listOf("work", "study", "assignment", "meeting").any(normalized::contains) -> NotificationCategory.WORK_STUDY
            listOf("event", "reminder", "appointment").any(normalized::contains) -> NotificationCategory.REMINDER_EVENT
            "social" in normalized -> NotificationCategory.SOCIAL
            listOf("promotion", "promo", "offer", "deal").any(normalized::contains) -> NotificationCategory.PROMOTION
            "spam" in normalized -> NotificationCategory.SPAM
            else -> null
        }

        val priority = when {
            "high priority" in normalized || "important" in normalized || "urgent" in normalized -> NotificationPriority.HIGH
            "low priority" in normalized -> NotificationPriority.LOW
            else -> null
        }

        val state = when {
            "saved for later" in normalized || "remind" in normalized || "later" in normalized -> NotificationState.LATER
            "archived" in normalized -> NotificationState.ARCHIVED
            "completed" in normalized || "done" in normalized -> NotificationState.DONE
            else -> null
        }

        val amount = if (category == NotificationCategory.PAYMENT || listOf("₹", "rs", "rupee", "inr").any(normalized::contains)) {
            amountRegex.find(normalized)?.groupValues?.getOrNull(1)?.replace(",", "")?.toDoubleOrNull()
        } else null

        val filler = listOf(
            "show", "find", "my", "notifications", "notification", "from", "about", "around",
            "yesterday", "today", "last week", "this week", "important", "urgent", "high priority",
            "low priority", "saved for later", "archived", "completed", "done",
            "otp", "verification", "payment", "banking", "transaction", "upi",
            "delivery", "package", "shipment", "messages", "message", "chat",
            "work", "study", "assignment", "meeting", "event", "reminder", "appointment",
            "social", "promotion", "promo", "spam", "rupees", "rupee", "inr",
            "the", "a", "an"
        )
        var free = normalized
        filler.sortedByDescending { it.length }.forEach { phrase ->
            free = free.replace(Regex("(?i)\\b${Regex.escape(phrase)}\\b"), " ")
        }
        if (amount != null) free = amountRegex.replace(free, " ")
        free = free.replace(Regex("\\s+"), " ").trim()

        return SearchIntent(freeText = free, from = from, until = until, category = category, priority = priority, state = state, amount = amount)
    }

    private fun startOfDay(time: Long): Long = Calendar.getInstance().apply {
        timeInMillis = time
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
}
