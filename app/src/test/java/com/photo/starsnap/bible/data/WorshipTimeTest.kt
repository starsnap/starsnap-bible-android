package com.photo.starsnap.bible.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WorshipTimeTest {
    @Test
    fun acceptsStrictLocalMinuteOnly() {
        assertTrue(WorshipTime.isValid("2026-09-01T19:30"))
        assertFalse(WorshipTime.isValid("2026-09-01T19:30:00"))
        assertFalse(WorshipTime.isValid("2026-09-01T19:30+09:00"))
        assertFalse(WorshipTime.isValid("2026-02-30T10:00"))
    }
}
