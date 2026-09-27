package com.premraj.notiflow.intelligence

import com.premraj.notiflow.data.ExpenseCategory
import com.premraj.notiflow.data.ExpenseTransaction
import com.premraj.notiflow.data.TransactionType
import java.util.Locale

object ExpenseParser {

    private val amountRegexes = listOf(
        Regex("""(?i)(?:(?:rs\.?|inr|₹)\s*|debited\s+by\s+|credited\s+by\s+)([0-9,]+(?:\.[0-9]{1,2})?)"""),
        Regex("""(?i)([0-9,]+(?:\.[0-9]{1,2})?)\s*(?:rs\.?|inr|₹)"""),
        Regex("""(?i)(?:paid|sent|received|spent)\s+([0-9,]+(?:\.[0-9]{1,2})?)""")
    )

    private val debitKeywords = listOf(
        "debited", "paid", "sent", "spent", "withdrawn", "transferred to", "transfer to",
        "purchase of", "deducted", "charged", "payment of", "debited by", "successful payment to"
    )

    private val creditKeywords = listOf(
        "credited", "received", "refund", "cashback", "deposited", "added to",
        "credited by", "received from", "money received"
    )

    private val accountRegex = Regex("""(?i)(?:a/c|ac|account|card|acct)\s*(?:no\.?|ending|xx|\*+)?\s*([0-9xX*]{2,16})""")
    private val balanceRegex = Regex("""(?i)(?:avail(?:able)?\s*bal(?:ance)?|bal|avlbl\s*bal)[^0-9]*([0-9,]+(?:\.[0-9]{1,2})?)""")

    private val merchantToRegex = Regex("""(?i)(?:paid\s+to|payment\s+to|transfer\s+to|sent\s+to|paid\s+at)\s+([A-Za-z0-9 .'&_-]{2,35})(?:\s+using|\s+via|\s+on|\s+for|\s+ref|\.|,|$)""")
    private val merchantFromRegex = Regex("""(?i)(?:received\s+from|from\s+vpa|received\s+via)\s+([A-Za-z0-9 .'&_-]{2,35})(?:\s+using|\s+via|\s+on|\s+for|\s+ref|\.|,|$)""")
    private val merchantAtRegex = Regex("""(?i)(?:\bat|info:\s*|vpa\s+)([A-Za-z0-9 .'&_-]{2,30})""")

    fun parse(
        title: String?,
        body: String?,
        packageName: String,
        appName: String,
        notificationId: Long = 0,
        timestamp: Long = System.currentTimeMillis()
    ): ExpenseTransaction? {
        val fullText = "${title.orEmpty()} ${body.orEmpty()}".trim()
        if (fullText.isBlank()) return null

        val lowerText = fullText.lowercase(Locale.ROOT)

        // Fast rejection: check if there's any financial hint
        val isFinancialContext = isPaymentApp(packageName) ||
                lowerText.contains("rs") ||
                lowerText.contains("inr") ||
                lowerText.contains("₹") ||
                lowerText.contains("debited") ||
                lowerText.contains("credited") ||
                lowerText.contains("bank") ||
                lowerText.contains("upi") ||
                lowerText.contains("paid") ||
                lowerText.contains("balance")

        if (!isFinancialContext) return null

        // Negative check: avoid OTP/recharge promotion confusion if not actually a debit/credit
        if (lowerText.contains("otp") && !lowerText.contains("debited") && !lowerText.contains("credited")) {
            return null
        }

        val amount = extractAmount(fullText) ?: return null
        val type = extractTransactionType(lowerText) ?: return null
        val merchantOrParty = extractMerchant(fullText, lowerText, type)
        val accountRef = extractAccountRef(fullText)
        val balance = extractBalance(fullText)
        val category = categorize(merchantOrParty, lowerText)

        return ExpenseTransaction(
            id = 0,
            notificationId = notificationId,
            amount = amount,
            transactionType = type,
            merchantOrParty = merchantOrParty,
            accountRef = accountRef,
            balanceAfter = balance,
            expenseCategory = category,
            timestamp = timestamp
        )
    }

    private fun isPaymentApp(pkg: String): Boolean {
        return pkg.contains("paisa") ||
                pkg.contains("phonepe") ||
                pkg.contains("paytm") ||
                pkg.contains("cred") ||
                pkg.contains("upi") ||
                pkg.contains("bank") ||
                pkg.contains("gpay") ||
                pkg.contains("freecharge") ||
                pkg.contains("mobikwik")
    }

    private fun extractAmount(text: String): Double? {
        for (regex in amountRegexes) {
            val match = regex.find(text)
            if (match != null) {
                val rawNumber = match.groupValues[1].replace(",", "").trim()
                val parsed = rawNumber.toDoubleOrNull()
                if (parsed != null && parsed > 0.0 && parsed < 100_000_000.0) {
                    return parsed
                }
            }
        }
        return null
    }

    private fun extractTransactionType(lowerText: String): TransactionType? {
        val debitIndex = debitKeywords.map { lowerText.indexOf(it) }.filter { it >= 0 }.minOrNull() ?: Int.MAX_VALUE
        val creditIndex = creditKeywords.map { lowerText.indexOf(it) }.filter { it >= 0 }.minOrNull() ?: Int.MAX_VALUE

        return when {
            debitIndex < creditIndex && debitIndex != Int.MAX_VALUE -> TransactionType.DEBIT
            creditIndex < debitIndex && creditIndex != Int.MAX_VALUE -> TransactionType.CREDIT
            debitIndex != Int.MAX_VALUE -> TransactionType.DEBIT
            creditIndex != Int.MAX_VALUE -> TransactionType.CREDIT
            else -> null
        }
    }

    private fun extractMerchant(fullText: String, lowerText: String, type: TransactionType): String? {
        val regex = if (type == TransactionType.DEBIT) merchantToRegex else merchantFromRegex
        val match = regex.find(fullText)
        if (match != null) {
            val candidate = match.groupValues[1].trim()
            if (isValidMerchantName(candidate)) return candidate
        }

        val atMatch = merchantAtRegex.find(fullText)
        if (atMatch != null) {
            val candidate = atMatch.groupValues[1].trim()
            if (isValidMerchantName(candidate)) return candidate
        }

        val knownMerchants = mapOf(
            "swiggy" to "Swiggy",
            "zomato" to "Zomato",
            "blinkit" to "Blinkit",
            "zepto" to "Zepto",
            "instamart" to "Instamart",
            "bigbasket" to "BigBasket",
            "amazon" to "Amazon",
            "flipkart" to "Flipkart",
            "myntra" to "Myntra",
            "uber" to "Uber",
            "ola" to "Ola",
            "rapido" to "Rapido",
            "starbucks" to "Starbucks",
            "mcdonald" to "McDonald's",
            "domino" to "Domino's",
            "kfc" to "KFC",
            "netflix" to "Netflix",
            "spotify" to "Spotify",
            "hotstar" to "Disney+ Hotstar",
            "bookmyshow" to "BookMyShow",
            "irctc" to "IRCTC",
            "zerodha" to "Zerodha",
            "groww" to "Groww",
            "apollo" to "Apollo Pharmacy",
            "pharmeasy" to "PharmEasy",
            "airtel" to "Airtel",
            "jio" to "Jio"
        )

        for ((key, name) in knownMerchants) {
            if (lowerText.contains(key)) return name
        }

        return null
    }

    private fun isValidMerchantName(name: String): Boolean {
        val lower = name.lowercase(Locale.ROOT)
        if (lower.contains("account") || lower.contains("bank") || lower.contains("balance") ||
            lower.contains("ref") || lower.contains("upi") || lower.contains("card") ||
            lower.contains("your") || lower.contains("available") || lower.length < 2
        ) {
            return false
        }
        return true
    }

    private fun extractAccountRef(text: String): String? {
        val match = accountRegex.find(text) ?: return null
        val raw = match.groupValues[1].trim()
        val digits = raw.filter { it.isDigit() }
        return if (digits.length >= 2) {
            "A/c **${digits.takeLast(4)}"
        } else {
            "A/c $raw"
        }
    }

    private fun extractBalance(text: String): Double? {
        val match = balanceRegex.find(text) ?: return null
        val raw = match.groupValues[1].replace(",", "").trim()
        return raw.toDoubleOrNull()
    }

    fun categorize(merchantOrParty: String?, lowerText: String): ExpenseCategory {
        val text = "${merchantOrParty.orEmpty().lowercase(Locale.ROOT)} $lowerText"

        return when {
            text.containsAny("swiggy", "zomato", "mcdonald", "starbucks", "domino", "kfc", "burger", "pizza",
                "restaurant", "cafe", "coffee", "dhaba", "dining", "eats", "food", "kitchen", "biryani", "bakery") ->
                ExpenseCategory.FOOD

            text.containsAny("blinkit", "zepto", "instamart", "bigbasket", "dmart", "supermarket", "grocery",
                "vegetable", "fruit", "milk", "dairy", "nature's basket", "kirana") ->
                ExpenseCategory.GROCERY

            text.containsAny("uber", "ola", "rapido", "irctc", "makemytrip", "goibibo", "redbus", "flight",
                "metro", "petrol", "diesel", "fuel", "hpcl", "bpcl", "iocl", "toll", "fastag", "railway", "indigo") ->
                ExpenseCategory.TRAVEL

            text.containsAny("electricity", "bescom", "tneb", "water bill", "gas bill", "cylinder", "broadband",
                "wifi", "recharge", "airtel", "jio", "vi bill", "dth", "tata play", "electricity bill", "utility") ->
                ExpenseCategory.BILLS

            text.containsAny("amazon", "flipkart", "myntra", "ajio", "meesho", "nykaa", "tata cliq", "retail",
                "clothing", "apparel", "footwear", "shopping", "store", "mall") ->
                ExpenseCategory.SHOPPING

            text.containsAny("zerodha", "groww", "upstox", "kuvera", "indmoney", "mutual fund", "sip",
                "shares", "nse", "bse", "invest", "securities") ->
                ExpenseCategory.INVESTMENT

            text.containsAny("apollo", "pharmeasy", "1mg", "medplus", "hospital", "clinic", "pharmacy",
                "doctor", "medical", "diagnostic", "dr.", "medicines", "health") ->
                ExpenseCategory.HEALTH

            text.containsAny("netflix", "spotify", "hotstar", "prime video", "bookmyshow", "cinema",
                "pvr", "inox", "youtube", "playstation", "entertainment", "movie") ->
                ExpenseCategory.ENTERTAINMENT

            text.containsAny("transfer to", "transferred to", "sent to", "vpa", "upi/p2p", "personal") ->
                ExpenseCategory.TRANSFER

            else -> ExpenseCategory.GENERAL
        }
    }

    private fun String.containsAny(vararg terms: String): Boolean {
        for (term in terms) {
            if (this.contains(term)) return true
        }
        return false
    }
}

