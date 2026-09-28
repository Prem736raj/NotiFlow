package com.premraj.notiflow.focus

import com.premraj.notiflow.data.FocusProfileType
import com.premraj.notiflow.data.UserPreferences
import com.premraj.notiflow.testutil.FakeSharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.Calendar

class FocusEngineTest {

    private lateinit var fakePrefs: FakeSharedPreferences
    private lateinit var userPrefs: UserPreferences
    private lateinit var engine: FocusEngine

    @Before
    fun setUp() {
        fakePrefs = FakeSharedPreferences()
        userPrefs = UserPreferences(fakePrefs).apply {
            sleepFocusScheduled = false
            workFocusScheduled = false
        }
        engine = FocusEngine(userPrefs)
    }

    @Test
    fun testFocusProfileTypes() {
        val types = FocusProfileType.entries
        assertEquals(4, types.size)
        assertNotNull(FocusProfileType.valueOf("WORK"))
        assertNotNull(FocusProfileType.valueOf("STUDY"))
        assertNotNull(FocusProfileType.valueOf("SLEEP"))
        assertNotNull(FocusProfileType.valueOf("QUIET_HOURS"))
    }

    @Test
    fun testInitialStatusIsInactive() {
        val status = engine.currentStatus()
        assertFalse(status.isActive)
        assertNull(status.activeProfile)
        assertFalse(status.isScheduled)
    }

    @Test
    fun testManualFocusToggle() {
        engine.toggleManualFocus(FocusProfileType.WORK, nowMillis = 1000L)
        val activeStatus = engine.currentStatus()
        assertTrue(activeStatus.isActive)
        assertEquals(FocusProfileType.WORK, activeStatus.activeProfile)
        assertFalse(activeStatus.isScheduled)
        assertEquals(1000L, activeStatus.activatedAt)

        // Switching to STUDY
        engine.toggleManualFocus(FocusProfileType.STUDY, nowMillis = 2000L)
        val studyStatus = engine.currentStatus()
        assertTrue(studyStatus.isActive)
        assertEquals(FocusProfileType.STUDY, studyStatus.activeProfile)

        // Toggling same type deactivates
        engine.toggleManualFocus(FocusProfileType.STUDY)
        val inactiveStatus = engine.currentStatus()
        assertFalse(inactiveStatus.isActive)
    }

    @Test
    fun testScheduledSleepFocus() {
        userPrefs.sleepFocusScheduled = true

        // 23:30 (night)
        val calLateNight = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 30)
        }
        val nightStatus = engine.currentStatus(calLateNight.timeInMillis)
        assertTrue(nightStatus.isActive)
        assertEquals(FocusProfileType.SLEEP, nightStatus.activeProfile)
        assertTrue(nightStatus.isScheduled)

        // 04:00 (early morning)
        val calEarlyMorning = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 4)
            set(Calendar.MINUTE, 0)
        }
        val earlyStatus = engine.currentStatus(calEarlyMorning.timeInMillis)
        assertTrue(earlyStatus.isActive)
        assertEquals(FocusProfileType.SLEEP, earlyStatus.activeProfile)
        assertTrue(earlyStatus.isScheduled)

        // 14:00 (daytime)
        val calAfternoon = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 14)
            set(Calendar.MINUTE, 0)
        }
        val dayStatus = engine.currentStatus(calAfternoon.timeInMillis)
        assertFalse(dayStatus.isActive)
    }

    @Test
    fun testScheduledWorkFocusOnWeekdays() {
        userPrefs.workFocusScheduled = true

        // Wednesday 11:00 (work hours)
        val calWeekdayWork = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_WEEK, Calendar.WEDNESDAY)
            set(Calendar.HOUR_OF_DAY, 11)
            set(Calendar.MINUTE, 0)
        }
        val workStatus = engine.currentStatus(calWeekdayWork.timeInMillis)
        assertTrue(workStatus.isActive)
        assertEquals(FocusProfileType.WORK, workStatus.activeProfile)
        assertTrue(workStatus.isScheduled)

        // Wednesday 20:00 (after work)
        val calWeekdayEvening = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_WEEK, Calendar.WEDNESDAY)
            set(Calendar.HOUR_OF_DAY, 20)
            set(Calendar.MINUTE, 0)
        }
        val eveningStatus = engine.currentStatus(calWeekdayEvening.timeInMillis)
        assertFalse(eveningStatus.isActive)

        // Sunday 11:00 (weekend)
        val calSunday = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_WEEK, Calendar.SUNDAY)
            set(Calendar.HOUR_OF_DAY, 11)
            set(Calendar.MINUTE, 0)
        }
        val sundayStatus = engine.currentStatus(calSunday.timeInMillis)
        assertFalse(sundayStatus.isActive)
    }

    @Test
    fun testManualFocusOverridesScheduledFocus() {
        userPrefs.workFocusScheduled = true
        val calWeekdayWork = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_WEEK, Calendar.WEDNESDAY)
            set(Calendar.HOUR_OF_DAY, 11)
            set(Calendar.MINUTE, 0)
        }

        // Manually activate STUDY
        engine.toggleManualFocus(FocusProfileType.STUDY)

        val status = engine.currentStatus(calWeekdayWork.timeInMillis)
        assertTrue(status.isActive)
        assertEquals(FocusProfileType.STUDY, status.activeProfile)
        assertFalse(status.isScheduled)
    }
}
