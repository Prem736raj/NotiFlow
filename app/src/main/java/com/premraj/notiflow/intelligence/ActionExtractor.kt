package com.premraj.notiflow.intelligence

import com.premraj.notiflow.data.NotificationCategory
import com.premraj.notiflow.data.NotificationItem
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

sealed interface SmartAction {
    val label: String

    data class CopyCode(val code: String) : SmartAction { override val label = "Copy code" }
    data class OpenUrl(val url: String) : SmartAction { override val label = "Open link" }
    data class Call(val phone: String) : SmartAction { override val label = "Call" }
    data class OpenMap(val address: String) : SmartAction { override val label = "Open in Maps" }
    data class AddCalendar(val title: String, val startAt: Long) : SmartAction { override val label = "Add to calendar" }
    data object CreateReminder : SmartAction { override val label = "Remind me" }
}

object ActionExtractor {
    private val urlRegex = Regex("https?://[^\\s]+", RegexOption.IGNORE_CASE)
    private val phoneRegex = Regex("(?<!\\d)(\\+?91[- ]?)?([6-9]\\d{9})(?!\\d)")
    private val phoneContext = Regex("(?i)\\b(?:call|phone|mobile|contact|telephone|tel|reach|whatsapp)\\b")
    private val trackingContext = Regex("(?i)\\b(?:order|tracking|shipment|awb|consignment|reference|invoice)\\b")
    private val codeRegex = Regex("(?<!\\d)(\\d{4,8})(?!\\d)")
    private val strongAddressHints = Regex("(?i)\\b(?:road|rd\\.?|street|st\\.?|sector|avenue|lane|nagar|colony|mall|airport|highway)\\b")
    private val weakAddressHints = Regex("(?i)\\b(?:phase|block|market|station)\\b")
    private val addressNumberHint = Regex("(?i)\\b(?:plot|house|flat|shop|sector|block|phase)\\s*[-#:]*\\s*[A-Za-z0-9-]+\\b|\\b\\d{1,4}\\s+[A-Za-z][A-Za-z .'-]{1,30}\\s+(?:road|rd\\.?|street|st\\.?|avenue|lane)\\b")
    private val postalCodeHint = Regex("(?<!\\d)\\d{6}(?!\\d)")
    private val dateFormats = listOf("dd/MM/yyyy", "dd-MM-yyyy", "yyyy-MM-dd", "dd MMM yyyy", "MMM dd, yyyy")
    private val time12Regex = Regex("(?i)\\b(?:at\\s*)?(1[0-2]|0?[1-9])(?::([0-5]\\d))?\\s*(am|pm)\\b")
    private val time24Regex = Regex("(?i)\\b(?:at\\s*)?([01]?\\d|2[0-3]):([0-5]\\d)\\b")

    // Keywords that appear BEFORE the OTP digits (gap up to 40 chars including newlines)
    private val otpAfterKeyword = Regex(
        "(?i)\\b(?:otp|verification code|security code|login code|auth code|passcode" +
        "|one[ -]?time (?:password|code)|confirmation code|confirm(?:ation)? code" +
        "|code to (?:confirm|verify)|use this code|enter (?:this )?code" +
        "|your code is|pin code)\\b[^0-9]{0,40}([0-9]{4,8})(?!\\d)"
    )
    // Keywords that appear AFTER the OTP digits
    private val otpBeforeKeyword = Regex(
        "(?i)(?<!\\d)([0-9]{4,8})\\b[^A-Za-z0-9]{0,15}(?:is\\s+)?(?:your\\s+)?" +
        "(?:otp|verification code|security code|login code|auth code|passcode" +
        "|one[ -]?time (?:password|code)|confirmation code|confirm(?:ation)? code)\\b"
    )
    // OTP on its own line after a colon or keyword line
    private val otpOnNextLine = Regex(
        "(?i)(?:otp|code|verification|confirm|identity|password)[^\\n]{0,30}[:.]\\s*\\n\\s*([0-9]{4,8})(?!\\d)"
    )
    // Standalone code on its own line (common in emails)
    private val standaloneCode = Regex("(?m)^\\s*([0-9]{4,8})\\s*$")

    /** Check if a digit sequence is embedded inside an alphanumeric token (like a username) */
    private fun isPartOfAlphanumericToken(text: String, matchStart: Int, matchEnd: Int): Boolean {
        // Check if there's a letter immediately before or after the digit match
        val before = if (matchStart > 0) text[matchStart - 1] else ' '
        val after = if (matchEnd < text.length) text[matchEnd] else ' '
        return before.isLetter() || after.isLetter()
    }

    /** Check if a code appears in a greeting/salutation line */
    private fun isInGreetingLine(text: String, matchStart: Int): Boolean {
        // Find the line containing this match
        val lineStart = text.lastIndexOf('\n', matchStart - 1).let { if (it < 0) 0 else it + 1 }
        val lineEnd = text.indexOf('\n', matchStart).let { if (it < 0) text.length else it }
        val line = text.substring(lineStart, lineEnd)
        return Regex("(?i)^\\s*(?:hi|hello|hey|dear|welcome)\\b").containsMatchIn(line)
    }

    fun extractOtpCode(text: String): String? {
        // 1. Try keyword-based extraction (highest confidence)
        otpAfterKeyword.find(text)?.groupValues?.getOrNull(1)?.let { return it }
        otpBeforeKeyword.find(text)?.groupValues?.getOrNull(1)?.let { return it }

        // 2. Try OTP on the next line after a colon (common in emails like Instagram)
        otpOnNextLine.find(text)?.groupValues?.getOrNull(1)?.let { return it }

        // 3. Fallback: if text mentions otp/code/verification, find best candidate
        if (Regex("(?i)\\b(?:otp|code|verification|confirm|pin)\\b").containsMatchIn(text)) {
            val candidates = codeRegex.findAll(text)
                .filter { match ->
                    val code = match.groupValues[1]
                    val start = match.range.first
                    val end = match.range.last + 1
                    // Skip codes embedded in alphanumeric tokens (usernames like "0000I0IIIII")
                    !isPartOfAlphanumericToken(text, start, end) &&
                    // Skip codes in greeting lines ("Hi 0000...")
                    !isInGreetingLine(text, start) &&
                    // Valid code length
                    code.length in 4..8
                }
                .map { it.groupValues[1] }
                .toList()

            // Prefer 6-digit codes (most common OTP length), then longer codes
            return candidates.sortedByDescending { it.length }.firstOrNull()
        }

        // 4. Try standalone code on its own line (even without keyword context)
        standaloneCode.find(text)?.groupValues?.getOrNull(1)?.let { return it }

        return null
    }

    fun extractOtpCode(title: String?, body: String?): String? {
        // Try body first (OTP is almost always in the body, not title)
        body?.let { b ->
            val bodyResult = extractOtpCode(b)
            if (bodyResult != null) return bodyResult
        }
        // Try combined title + body
        val text = listOfNotNull(title, body).joinToString(" ").trim()
        return if (text.isBlank()) null else extractOtpCode(text)
    }

    fun extract(item: NotificationItem): List<SmartAction> {
        val text = listOfNotNull(item.title, item.body).joinToString(" ").trim()
        if (text.isBlank()) return if (item.state.name != "LATER") listOf(SmartAction.CreateReminder) else emptyList()

        val actions = mutableListOf<SmartAction>()

        urlRegex.find(text)?.value?.trimEnd('.', ',', ')')?.let { actions += SmartAction.OpenUrl(it) }
        extractPhone(text)?.let { actions += SmartAction.Call(it) }

        val code = extractOtpCode(text)
        if (code != null) {
            actions += SmartAction.CopyCode(code)
        }

        parseDate(text)?.let { actions += SmartAction.AddCalendar(item.displayTitle, it) }

        if (looksLikeAddress(text)) {
            actions += SmartAction.OpenMap(text)
        }

        if (item.state.name != "LATER") actions += SmartAction.CreateReminder
        return actions.distinctBy { it::class }.take(5)
    }

    private fun parseDate(text: String): Long? {
        val candidates = Regex("\\b(?:\\d{1,2}[/-]\\d{1,2}[/-]\\d{4}|\\d{4}-\\d{1,2}-\\d{1,2}|\\d{1,2} [A-Za-z]{3,9} \\d{4}|[A-Za-z]{3,9} \\d{1,2}, \\d{4})\\b")
            .findAll(text).map { it.value }.toList()
        for (candidate in candidates) {
            for (format in dateFormats) {
                val parsed = runCatching {
                    SimpleDateFormat(format, Locale.ENGLISH).apply { isLenient = false }.parse(candidate)
                }.getOrNull()
                if (parsed != null) return applyTime(parsed.time, text)
            }
        }
        return null
    }

    private fun extractPhone(text: String): String? {
        return phoneRegex.findAll(text).firstNotNullOfOrNull { match ->
            val prefix = match.groupValues[1]
            val windowStart = (match.range.first - 36).coerceAtLeast(0)
            val windowEnd = (match.range.last + 37).coerceAtMost(text.length)
            val context = text.substring(windowStart, windowEnd)
            val hasPhoneContext = phoneContext.containsMatchIn(context)
            val looksLikeTracking = trackingContext.containsMatchIn(context) && !hasPhoneContext
            if (looksLikeTracking || (prefix.isBlank() && !hasPhoneContext)) {
                null
            } else {
                match.value.replace(" ", "").replace("-", "")
            }
        }
    }

    private fun looksLikeAddress(text: String): Boolean {
        if (text.length !in 12..220) return false
        val strongCount = strongAddressHints.findAll(text).count()
        val weakCount = weakAddressHints.findAll(text).count()
        val hasNumber = addressNumberHint.containsMatchIn(text)
        val hasPostalCode = postalCodeHint.containsMatchIn(text)
        return strongCount >= 2 ||
            (strongCount >= 1 && (weakCount >= 1 || hasNumber || hasPostalCode)) ||
            (weakCount >= 2 && (hasNumber || hasPostalCode))
    }

    private fun applyTime(dateMillis: Long, text: String): Long {
        val calendar = Calendar.getInstance().apply { timeInMillis = dateMillis }
        time12Regex.find(text)?.let { match ->
            var hour = match.groupValues[1].toInt()
            val minute = match.groupValues[2].toIntOrNull() ?: 0
            val meridiem = match.groupValues[3].lowercase(Locale.ENGLISH)
            if (hour == 12) hour = 0
            if (meridiem == "pm") hour += 12
            calendar.set(Calendar.HOUR_OF_DAY, hour)
            calendar.set(Calendar.MINUTE, minute)
            calendar.set(Calendar.SECOND, 0)
            calendar.set(Calendar.MILLISECOND, 0)
            return calendar.timeInMillis
        }

        time24Regex.find(text)?.let { match ->
            calendar.set(Calendar.HOUR_OF_DAY, match.groupValues[1].toInt())
            calendar.set(Calendar.MINUTE, match.groupValues[2].toInt())
            calendar.set(Calendar.SECOND, 0)
            calendar.set(Calendar.MILLISECOND, 0)
        }
        return calendar.timeInMillis
    }
}

