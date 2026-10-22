package com.premraj.notiflow.data

import java.util.Locale

enum class AutomationAction(val label: String, val description: String) {
    PIN(
        "Pin to Important",
        "Keep matching alerts at the top until you act on them."
    ),
    HIGH_PRIORITY(
        "Mark high priority",
        "Elevate matching alerts in the Important section."
    ),
    ARCHIVE(
        "Auto-archive",
        "Move matching alerts out of the inbox and into History."
    )
}

data class AutomationRule(
    val id: String,
    val keyword: String,
    val packageName: String? = null,
    val action: AutomationAction,
    val enabled: Boolean = true
)

data class AutomationOutcome(
    val matchedRuleIds: Set<String> = emptySet(),
    val highPriority: Boolean = false,
    val pin: Boolean = false,
    val archive: Boolean = false
) {
    val matched: Boolean get() = matchedRuleIds.isNotEmpty()
}

object AutomationEngine {
    fun evaluate(
        rules: List<AutomationRule>,
        packageName: String,
        title: String?,
        body: String?,
        sender: String?
    ): AutomationOutcome {
        val haystack = listOfNotNull(title, body, sender).joinToString(" ")
        val matches = rules.filter { rule ->
            rule.enabled && matches(rule, packageName, haystack)
        }
        if (matches.isEmpty()) return AutomationOutcome()

        val archive = matches.any { it.action == AutomationAction.ARCHIVE }
        return AutomationOutcome(
            matchedRuleIds = matches.mapTo(mutableSetOf()) { it.id },
            highPriority = matches.any { it.action == AutomationAction.HIGH_PRIORITY },
            // An archived item does not belong in the pinned Important list.
            pin = !archive && matches.any { it.action == AutomationAction.PIN },
            archive = archive
        )
    }

    fun matches(rule: AutomationRule, packageName: String, haystack: String): Boolean {
        if (!rule.enabled || rule.keyword.isBlank()) return false
        if (!rule.packageName.isNullOrBlank() && rule.packageName != packageName) return false
        return normalize(rule.keyword) in normalize(haystack)
    }

    fun normalize(value: String): String =
        value.lowercase(Locale.ROOT).trim().replace(WHITESPACE, " ")

    private val WHITESPACE = Regex("\\s+")
}
