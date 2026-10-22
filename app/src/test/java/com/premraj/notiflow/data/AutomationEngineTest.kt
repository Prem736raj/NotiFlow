package com.premraj.notiflow.data

import com.premraj.notiflow.testutil.FakeSharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutomationEngineTest {

    @Test
    fun matchesKeywordAcrossTitleBodyAndSenderIgnoringCaseAndSpacing() {
        val rule = rule(keyword = "  urgent   payment  ")

        assertTrue(
            AutomationEngine.matches(
                rule,
                packageName = "com.bank.app",
                haystack = "URGENT payment received"
            )
        )
        assertTrue(
            AutomationEngine.matches(
                rule,
                packageName = "com.chat.app",
                haystack = "Alex: urgent payment reminder"
            )
        )
        assertFalse(
            AutomationEngine.matches(
                rule,
                packageName = "com.chat.app",
                haystack = "weekly newsletter"
            )
        )
    }

    @Test
    fun packageFilterLimitsRuleToSourceApp() {
        val rule = rule(keyword = "invoice", packageName = "com.work.app")

        assertTrue(AutomationEngine.matches(rule, "com.work.app", "New invoice"))
        assertFalse(AutomationEngine.matches(rule, "com.other.app", "New invoice"))
    }

    @Test
    fun disabledOrBlankRulesNeverMatch() {
        val disabled = rule(keyword = "invoice", enabled = false)
        val blank = rule(keyword = "   ")

        assertFalse(AutomationEngine.matches(disabled, "com.work.app", "New invoice"))
        assertFalse(AutomationEngine.matches(blank, "com.work.app", "New invoice"))
        assertFalse(
            AutomationEngine.evaluate(
                rules = listOf(disabled, blank),
                packageName = "com.work.app",
                title = "New invoice",
                body = null,
                sender = null
            ).matched
        )
    }

    @Test
    fun evaluateCombinesMatchingActionsAndArchiveSuppressesPin() {
        val rules = listOf(
            rule(id = "pin", keyword = "server", action = AutomationAction.PIN),
            rule(id = "high", keyword = "down", action = AutomationAction.HIGH_PRIORITY),
            rule(id = "archive", keyword = "server", action = AutomationAction.ARCHIVE)
        )

        val outcome = AutomationEngine.evaluate(
            rules = rules,
            packageName = "com.status.app",
            title = "Server down",
            body = "Production is down",
            sender = null
        )

        assertTrue(outcome.matched)
        assertEquals(setOf("pin", "high", "archive"), outcome.matchedRuleIds)
        assertTrue(outcome.highPriority)
        assertTrue(outcome.archive)
        assertFalse(outcome.pin)
    }

    @Test
    fun pinnedRuleWinsWhenNoArchiveRuleMatches() {
        val outcome = AutomationEngine.evaluate(
            rules = listOf(rule(keyword = "invoice", action = AutomationAction.PIN)),
            packageName = "com.work.app",
            title = "Invoice ready",
            body = "Tap to review",
            sender = null
        )

        assertTrue(outcome.pin)
        assertFalse(outcome.archive)
        assertFalse(outcome.highPriority)
    }

    @Test
    fun preferencesRoundTripAndMutationsPersistRules() {
        val prefs = UserPreferences(FakeSharedPreferences())
        val first = rule(id = "first", keyword = "invoice")
        val second = rule(id = "second", keyword = "delivery", action = AutomationAction.ARCHIVE)

        prefs.addAutomationRule(first)
        prefs.addAutomationRule(second)
        assertEquals(listOf(first, second), prefs.automationRules())

        prefs.setAutomationRuleEnabled("first", false)
        assertFalse(prefs.automationRules().first { it.id == "first" }.enabled)

        prefs.removeAutomationRule("second")
        assertEquals(listOf("first"), prefs.automationRules().map { it.id })
    }

    @Test
    fun malformedStoredRulesAreIgnoredInsteadOfCrashingCapture() {
        val fake = FakeSharedPreferences()
        fake.edit().putString("automation_rules", "{not-json").apply()
        val prefs = UserPreferences(fake)

        assertTrue(prefs.automationRules().isEmpty())

        prefs.addAutomationRule(rule(id = "valid", keyword = "invoice"))
        assertEquals(listOf("valid"), prefs.automationRules().map { it.id })
    }

    private fun rule(
        id: String = "rule",
        keyword: String,
        packageName: String? = null,
        action: AutomationAction = AutomationAction.HIGH_PRIORITY,
        enabled: Boolean = true
    ) = AutomationRule(
        id = id,
        keyword = keyword,
        packageName = packageName,
        action = action,
        enabled = enabled
    )
}
