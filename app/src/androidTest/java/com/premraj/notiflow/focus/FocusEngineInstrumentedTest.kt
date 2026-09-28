package com.premraj.notiflow.focus

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.premraj.notiflow.data.FocusProfileType
import com.premraj.notiflow.data.UserPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Calendar

@RunWith(AndroidJUnit4::class)
class FocusEngineInstrumentedTest {

    @Test
    fun schedulesAreOptInAndScheduledSessionsResetTheirBlockedCount() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("notiflow_preferences", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        val preferences = UserPreferences(context)
        val engine = FocusEngine(preferences)

        assertFalse(preferences.sleepFocusScheduled)
        assertFalse(preferences.workFocusScheduled)

        preferences.workFocusScheduled = true
        val firstSession = calendarAt(2026, Calendar.SEPTEMBER, 29, 10, 0)
        val firstStatus = engine.currentStatus(firstSession)
        assertTrue(firstStatus.isActive)
        assertTrue(firstStatus.isScheduled)
        assertEquals(FocusProfileType.WORK, firstStatus.activeProfile)

        engine.recordSuppressed(firstSession)
        engine.recordSuppressed(firstSession)
        assertEquals(2, engine.currentStatus(firstSession).blockedCount)

        val nextSession = calendarAt(2026, Calendar.SEPTEMBER, 30, 10, 0)
        assertEquals(0, engine.currentStatus(nextSession).blockedCount)

        engine.stopActiveFocus(nextSession)
        assertFalse(preferences.workFocusScheduled)
        assertFalse(engine.currentStatus(nextSession).isActive)
    }

    private fun calendarAt(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long {
        return Calendar.getInstance().apply {
            set(year, month, day, hour, minute, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }
}
