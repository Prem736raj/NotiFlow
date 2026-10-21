package com.premraj.notiflow.util

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.FileProvider
import com.premraj.notiflow.appGraph
import com.premraj.notiflow.data.MetricsTracker
import com.premraj.notiflow.service.NotiFlowNotificationListener
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * On-device diagnostics exporter.
 *
 * Implements P1 roadmap requirement:
 * "Instrument observability privately: on-device health dashboard (listener active,
 * last seen event, work status, local storage) and consent-based diagnostics that
 * NEVER include notification text."
 */
object DiagnosticsExporter {

    fun buildDiagnosticReportJson(
        deviceManufacturer: String = Build.MANUFACTURER ?: "Unknown",
        deviceModel: String = Build.MODEL ?: "Unknown",
        deviceRelease: String = Build.VERSION.RELEASE ?: "Unknown",
        deviceSdkInt: Int = Build.VERSION.SDK_INT,
        appVersionName: String = "0.1.0",
        appVersionCode: Int = 1,
        accessGranted: Boolean,
        serviceConnected: Boolean,
        lastCaptureTimestamp: Long,
        storageStats: Pair<Int, Long>,
        expenseCount: Int,
        vipCount: Int,
        learnedCount: Int,
        quietPackagesCount: Int,
        quietCategoriesCount: Int,
        autoCopyOtp: Boolean,
        expenseTrackerEnabled: Boolean,
        hideSensitivePreviews: Boolean,
        autoCleanupEnabled: Boolean,
        retentionDays: Int,
        digestEnabled: Boolean,
        eventCounts: Map<String, Long>,
        nowTimestamp: Long = System.currentTimeMillis()
    ): JSONObject {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss z", Locale.US)
        val nowStr = dateFormat.format(Date(nowTimestamp))
        val lastCaptureStr = if (lastCaptureTimestamp > 0) dateFormat.format(Date(lastCaptureTimestamp)) else "None recorded"

        return JSONObject().apply {
            put("report_type", "NotiFlow Redacted Diagnostic Report")
            put("generated_at", nowStr)
            put("privacy_notice", "This diagnostic report contains ZERO notification titles, bodies, senders, OTP codes, or financial transactions.")

            put("device", JSONObject().apply {
                put("manufacturer", deviceManufacturer)
                put("model", deviceModel)
                put("android_version", deviceRelease)
                put("sdk_int", deviceSdkInt)
            })

            put("app", JSONObject().apply {
                put("version_name", appVersionName)
                put("version_code", appVersionCode)
            })

            put("listener_health", JSONObject().apply {
                put("permission_granted_in_settings", accessGranted)
                put("service_currently_connected", serviceConnected)
                put("last_notification_captured_at", lastCaptureStr)
            })

            put("storage", JSONObject().apply {
                put("notification_records_count", storageStats.first)
                put("storage_bytes", storageStats.second)
                put("expense_records_count", expenseCount)
            })

            put("configuration", JSONObject().apply {
                put("auto_copy_otp_enabled", autoCopyOtp)
                put("expense_tracker_enabled", expenseTrackerEnabled)
                put("hide_sensitive_previews", hideSensitivePreviews)
                put("auto_cleanup_enabled", autoCleanupEnabled)
                put("retention_days", retentionDays)
                put("digest_enabled", digestEnabled)
                put("vip_rules_count", vipCount)
                put("learned_rules_count", learnedCount)
                put("quiet_packages_count", quietPackagesCount)
                put("quiet_categories_count", quietCategoriesCount)
            })

            put("event_funnel_counters", JSONObject().apply {
                eventCounts.forEach { (key, value) -> put(key, value) }
            })
        }
    }

    suspend fun generateRedactedDiagnosticsReport(context: Context): String = withContext(Dispatchers.IO) {
        val graph = context.appGraph
        val accessGranted = NotiFlowNotificationListener.isAccessGranted(context)
        val serviceConnected = NotiFlowNotificationListener.isConnected()
        val lastCapture = NotiFlowNotificationListener.getLastCapturedAt()

        val storageStats = graph.store.storageStats()
        val expenses = graph.store.expenses.value
        val vipCount = graph.preferences.vipRules().size
        val learnedCount = graph.preferences.learnedPreferences().size
        val quietPackagesCount = graph.preferences.quietPackages().size
        val quietCategoriesCount = graph.preferences.quietCategories().size
        val eventCounts = MetricsTracker.getAllEventCounts(context)

        val report = buildDiagnosticReportJson(
            accessGranted = accessGranted,
            serviceConnected = serviceConnected,
            lastCaptureTimestamp = lastCapture,
            storageStats = storageStats,
            expenseCount = expenses.size,
            vipCount = vipCount,
            learnedCount = learnedCount,
            quietPackagesCount = quietPackagesCount,
            quietCategoriesCount = quietCategoriesCount,
            autoCopyOtp = graph.preferences.autoCopyOtp,
            expenseTrackerEnabled = graph.preferences.expenseTrackerEnabled,
            hideSensitivePreviews = graph.preferences.hideSensitivePreviews,
            autoCleanupEnabled = graph.preferences.autoCleanupEnabled,
            retentionDays = graph.preferences.retentionDays,
            digestEnabled = graph.preferences.digestEnabled,
            eventCounts = eventCounts
        )

        report.toString(2)
    }

    suspend fun exportDiagnosticsToFile(context: Context): File = withContext(Dispatchers.IO) {
        val report = generateRedactedDiagnosticsReport(context)
        val dir = File(context.cacheDir, "diagnostics").apply { mkdirs() }
        val file = File(dir, "notiflow-diagnostics-${System.currentTimeMillis()}.json")
        file.writeText(report, Charsets.UTF_8)
        file
    }

    fun shareDiagnostics(context: Context, file: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/json"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "NotiFlow Redacted Diagnostic Report")
            clipData = ClipData.newRawUri("NotiFlow Redacted Diagnostic Report", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val chooser = Intent.createChooser(intent, "Share Diagnostic Report").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(chooser)
    }
}
