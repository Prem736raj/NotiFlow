package com.premraj.notiflow.focus

import com.premraj.notiflow.data.FocusProfileType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class FocusEngineTest {

    @Test
    fun testFocusProfileTypes() {
        val types = FocusProfileType.entries
        assertEquals(4, types.size)
        assertNotNull(FocusProfileType.valueOf("WORK"))
        assertNotNull(FocusProfileType.valueOf("STUDY"))
        assertNotNull(FocusProfileType.valueOf("SLEEP"))
        assertNotNull(FocusProfileType.valueOf("CUSTOM"))
    }
}
