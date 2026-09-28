package com.premraj.notiflow.focus

import com.premraj.notiflow.data.FocusProfileType
import com.premraj.notiflow.data.FocusStatus
import com.premraj.notiflow.data.UserPreferences
import java.util.Calendar

class FocusEngine(
    private val preferences: UserPreferences
) {
    fun currentStatus(nowMillis: Long = System.currentTimeMillis()): FocusStatus {
        if (preferences.manualFocusActive) {
            val activatedAt = preferences.focusActivatedAt.takeIf { it > 0L } ?: nowMillis.also {
                preferences.focusActivatedAt = it
            }
            ensureSession(manualSessionToken(preferences.manualFocusType, activatedAt))
            return FocusStatus(
                isActive = true,
                activeProfile = preferences.manualFocusType,
                isScheduled = false,
                blockedCount = preferences.focusBlockedCount,
                activatedAt = activatedAt
            )
        }

        val calendar = Calendar.getInstance().apply { timeInMillis = nowMillis }
        val hour = calendar.get(Calendar.HOUR_OF_DAY)
        val dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)

        if (preferences.sleepFocusScheduled && (hour >= SLEEP_START_HOUR || hour < SLEEP_END_HOUR)) {
            val startedAt = scheduledStart(calendar, SLEEP_START_HOUR, crossesMidnight = true)
            ensureSession(scheduledSessionToken(FocusProfileType.SLEEP, startedAt))
            return FocusStatus(
                isActive = true,
                activeProfile = FocusProfileType.SLEEP,
                isScheduled = true,
                blockedCount = preferences.focusBlockedCount,
                activatedAt = startedAt
            )
        }

        val isWeekday = dayOfWeek in Calendar.MONDAY..Calendar.FRIDAY
        if (preferences.workFocusScheduled && isWeekday && hour in WORK_START_HOUR until WORK_END_HOUR) {
            val startedAt = scheduledStart(calendar, WORK_START_HOUR, crossesMidnight = false)
            ensureSession(scheduledSessionToken(FocusProfileType.WORK, startedAt))
            return FocusStatus(
                isActive = true,
                activeProfile = FocusProfileType.WORK,
                isScheduled = true,
                blockedCount = preferences.focusBlockedCount,
                activatedAt = startedAt
            )
        }

        return FocusStatus()
    }

    fun toggleManualFocus(
        type: FocusProfileType,
        nowMillis: Long = System.currentTimeMillis()
    ) {
        if (preferences.manualFocusActive && preferences.manualFocusType == type) {
            preferences.manualFocusActive = false
            preferences.focusActivatedAt = 0L
            preferences.focusBlockedCount = 0
            preferences.focusSessionToken = null
            return
        }

        preferences.manualFocusType = type
        preferences.manualFocusActive = true
        preferences.focusBlockedCount = 0
        preferences.focusActivatedAt = nowMillis
        preferences.focusSessionToken = manualSessionToken(type, nowMillis)
    }

    fun stopActiveFocus(nowMillis: Long = System.currentTimeMillis()) {
        val status = currentStatus(nowMillis)
        if (!status.isActive) return

        if (status.isScheduled) {
            when (status.activeProfile) {
                FocusProfileType.SLEEP -> preferences.sleepFocusScheduled = false
                FocusProfileType.WORK -> preferences.workFocusScheduled = false
                else -> Unit
            }
        } else {
            preferences.manualFocusActive = false
        }

        preferences.focusActivatedAt = 0L
        preferences.focusBlockedCount = 0
        preferences.focusSessionToken = null
    }

    fun recordSuppressed(nowMillis: Long = System.currentTimeMillis()) {
        if (!currentStatus(nowMillis).isActive) return
        preferences.focusBlockedCount = preferences.focusBlockedCount + 1
    }

    private fun ensureSession(token: String) {
        if (preferences.focusSessionToken == token) return
        preferences.focusSessionToken = token
        preferences.focusBlockedCount = 0
    }

    private fun scheduledStart(calendar: Calendar, startHour: Int, crossesMidnight: Boolean): Long {
        return (calendar.clone() as Calendar).apply {
            if (crossesMidnight && get(Calendar.HOUR_OF_DAY) < SLEEP_END_HOUR) {
                add(Calendar.DAY_OF_MONTH, -1)
            }
            set(Calendar.HOUR_OF_DAY, startHour)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    private companion object {
        const val SLEEP_START_HOUR = 23
        const val SLEEP_END_HOUR = 7
        const val WORK_START_HOUR = 9
        const val WORK_END_HOUR = 17

        fun manualSessionToken(type: FocusProfileType, activatedAt: Long): String =
            "manual:${type.name}:$activatedAt"

        fun scheduledSessionToken(type: FocusProfileType, startedAt: Long): String =
            "scheduled:${type.name}:$startedAt"
    }
}
