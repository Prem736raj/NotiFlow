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
            return FocusStatus(
                isActive = true,
                activeProfile = preferences.manualFocusType,
                isScheduled = false,
                blockedCount = preferences.focusBlockedCount,
                activatedAt = preferences.focusActivatedAt
            )
        }

        val calendar = Calendar.getInstance().apply { timeInMillis = nowMillis }
        val hour = calendar.get(Calendar.HOUR_OF_DAY)
        val dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)

        if (preferences.sleepFocusScheduled && (hour >= SLEEP_START_HOUR || hour < SLEEP_END_HOUR)) {
            return FocusStatus(
                isActive = true,
                activeProfile = FocusProfileType.SLEEP,
                isScheduled = true,
                blockedCount = preferences.focusBlockedCount,
                activatedAt = 0L
            )
        }

        val isWeekday = dayOfWeek in Calendar.MONDAY..Calendar.FRIDAY
        if (preferences.workFocusScheduled && isWeekday && hour in WORK_START_HOUR until WORK_END_HOUR) {
            return FocusStatus(
                isActive = true,
                activeProfile = FocusProfileType.WORK,
                isScheduled = true,
                blockedCount = preferences.focusBlockedCount,
                activatedAt = 0L
            )
        }

        return FocusStatus(blockedCount = preferences.focusBlockedCount)
    }

    fun toggleManualFocus(
        type: FocusProfileType,
        nowMillis: Long = System.currentTimeMillis()
    ) {
        if (preferences.manualFocusActive && preferences.manualFocusType == type) {
            preferences.manualFocusActive = false
            preferences.focusActivatedAt = 0L
            return
        }

        preferences.manualFocusType = type
        preferences.manualFocusActive = true
        preferences.focusBlockedCount = 0
        preferences.focusActivatedAt = nowMillis
    }

    private companion object {
        const val SLEEP_START_HOUR = 23
        const val SLEEP_END_HOUR = 7
        const val WORK_START_HOUR = 9
        const val WORK_END_HOUR = 17
    }
}
