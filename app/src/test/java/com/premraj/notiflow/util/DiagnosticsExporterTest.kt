package com.premraj.notiflow.util

import com.premraj.notiflow.data.MetricsTracker
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for DiagnosticsExporter.
 *
 * Validates P1 Roadmap observability requirement:
 * On-device health and consent-based diagnostics that NEVER include notification text or PII.
 */
class DiagnosticsExporterTest {

    @Test
    fun testBuildDiagnosticReportJson_structureAndMetadata() {
        val eventCounts = MetricsTracker.KNOWN_EVENTS.associateWith { 5L }
        val json = DiagnosticsExporter.buildDiagnosticReportJson(
            deviceManufacturer = "Google",
            deviceModel = "Pixel 8 Pro",
            deviceRelease = "14",
            deviceSdkInt = 34,
            appVersionName = "0.1.0",
            appVersionCode = 1,
            accessGranted = true,
            serviceConnected = true,
            lastCaptureTimestamp = 1729512000000L,
            storageStats = Pair(150, 102400L),
            expenseCount = 12,
            vipCount = 3,
            learnedCount = 4,
            quietPackagesCount = 2,
            quietCategoriesCount = 1,
            autoCopyOtp = true,
            expenseTrackerEnabled = true,
            hideSensitivePreviews = true,
            autoCleanupEnabled = true,
            retentionDays = 30,
            digestEnabled = true,
            localAiEnabled = false,
            eventCounts = eventCounts,
            nowTimestamp = 1729512000000L
        )

        assertNotNull(json)
        assertEquals("NotiFlow Redacted Diagnostic Report", json.getString("report_type"))
        assertTrue(json.has("generated_at"))
        assertTrue(json.getString("privacy_notice").contains("ZERO notification titles, bodies, senders, OTP codes"))

        val device = json.getJSONObject("device")
        assertEquals("Google", device.getString("manufacturer"))
        assertEquals("Pixel 8 Pro", device.getString("model"))
        assertEquals("14", device.getString("android_version"))
        assertEquals(34, device.getInt("sdk_int"))

        val app = json.getJSONObject("app")
        assertEquals("0.1.0", app.getString("version_name"))
        assertEquals(1, app.getInt("version_code"))

        val health = json.getJSONObject("listener_health")
        assertTrue(health.getBoolean("permission_granted_in_settings"))
        assertTrue(health.getBoolean("service_currently_connected"))
        assertFalse(health.getString("last_notification_captured_at").isEmpty())

        val storage = json.getJSONObject("storage")
        assertEquals(150, storage.getInt("notification_records_count"))
        assertEquals(102400L, storage.getLong("storage_bytes"))
        assertEquals(12, storage.getInt("expense_records_count"))

        val config = json.getJSONObject("configuration")
        assertTrue(config.getBoolean("auto_copy_otp_enabled"))
        assertTrue(config.getBoolean("expense_tracker_enabled"))
        assertTrue(config.getBoolean("hide_sensitive_previews"))
        assertTrue(config.getBoolean("auto_cleanup_enabled"))
        assertEquals(30, config.getInt("retention_days"))
        assertTrue(config.getBoolean("digest_enabled"))
        assertEquals(3, config.getInt("vip_rules_count"))
        assertEquals(4, config.getInt("learned_rules_count"))
        assertEquals(2, config.getInt("quiet_packages_count"))
        assertEquals(1, config.getInt("quiet_categories_count"))
        assertFalse(config.getBoolean("local_ai_enabled"))
    }

    @Test
    fun testBuildDiagnosticReportJson_zeroPiiGuarantee() {
        val eventCounts = MetricsTracker.KNOWN_EVENTS.associateWith { 1L }
        val json = DiagnosticsExporter.buildDiagnosticReportJson(
            accessGranted = true,
            serviceConnected = true,
            lastCaptureTimestamp = 0L,
            storageStats = Pair(0, 0L),
            expenseCount = 0,
            vipCount = 0,
            learnedCount = 0,
            quietPackagesCount = 0,
            quietCategoriesCount = 0,
            autoCopyOtp = false,
            expenseTrackerEnabled = false,
            hideSensitivePreviews = false,
            autoCleanupEnabled = false,
            retentionDays = 7,
            digestEnabled = false,
            localAiEnabled = false,
            eventCounts = eventCounts
        )

        val forbiddenKeys = setOf(
            "title", "body", "message", "sender", "otp", "code",
            "amount", "balance", "card", "account", "text", "notification_content"
        )

        fun checkJsonForPii(obj: JSONObject) {
            val keys = obj.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                assertFalse("Diagnostic report must NEVER have PII key: $key", forbiddenKeys.contains(key.lowercase()))
                when (val value = obj.get(key)) {
                    is JSONObject -> checkJsonForPii(value)
                    is String -> {
                        // Ensure no personal data was serialized
                        assertFalse("Diagnostic report string value should not contain sensitive notification data",
                            value.contains("password", ignoreCase = true) || value.contains("secret", ignoreCase = true))
                    }
                }
            }
        }

        checkJsonForPii(json)
    }

    @Test
    fun testBuildDiagnosticReportJson_eventFunnelCountersPreserved() {
        val counts = mapOf(
            MetricsTracker.EVENT_DISCLOSURE_SHOWN to 12L,
            MetricsTracker.EVENT_PERMISSION_ENABLED to 10L,
            MetricsTracker.EVENT_CAPTURE_VERIFIED to 150L,
            MetricsTracker.EVENT_NOTIFICATION_MARKED_LATER to 25L,
            MetricsTracker.EVENT_REMINDER_SCHEDULED to 20L,
            MetricsTracker.EVENT_REMINDER_DELIVERED to 18L,
            MetricsTracker.EVENT_NOTIFICATION_RETRIEVED to 45L,
            MetricsTracker.EVENT_LISTENER_DISCONNECTED to 2L,
            MetricsTracker.EVENT_PERMISSION_REVOKED to 1L
        )

        val json = DiagnosticsExporter.buildDiagnosticReportJson(
            accessGranted = true,
            serviceConnected = true,
            lastCaptureTimestamp = 1000L,
            storageStats = Pair(10, 500L),
            expenseCount = 0,
            vipCount = 0,
            learnedCount = 0,
            quietPackagesCount = 0,
            quietCategoriesCount = 0,
            autoCopyOtp = false,
            expenseTrackerEnabled = false,
            hideSensitivePreviews = false,
            autoCleanupEnabled = false,
            retentionDays = 7,
            digestEnabled = false,
            localAiEnabled = false,
            eventCounts = counts
        )

        val funnel = json.getJSONObject("event_funnel_counters")
        counts.forEach { (event, expectedCount) ->
            assertTrue("Must contain counter for $event", funnel.has(event))
            assertEquals(expectedCount, funnel.getLong(event))
        }
    }

    @Test
    fun testBuildDiagnosticReportJson_disconnectedStandbyState() {
        val json = DiagnosticsExporter.buildDiagnosticReportJson(
            accessGranted = false,
            serviceConnected = false,
            lastCaptureTimestamp = 0L,
            storageStats = Pair(0, 0L),
            expenseCount = 0,
            vipCount = 0,
            learnedCount = 0,
            quietPackagesCount = 0,
            quietCategoriesCount = 0,
            autoCopyOtp = false,
            expenseTrackerEnabled = false,
            hideSensitivePreviews = false,
            autoCleanupEnabled = false,
            retentionDays = 7,
            digestEnabled = false,
            localAiEnabled = false,
            eventCounts = emptyMap()
        )

        val health = json.getJSONObject("listener_health")
        assertFalse(health.getBoolean("permission_granted_in_settings"))
        assertFalse(health.getBoolean("service_currently_connected"))
        assertEquals("None recorded", health.getString("last_notification_captured_at"))
    }
}
