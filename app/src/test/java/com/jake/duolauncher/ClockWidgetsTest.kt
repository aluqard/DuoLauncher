package com.jake.duolauncher

import org.junit.Assert.*
import org.junit.Test

class ClockWidgetsTest {
    private fun near(expected: Float, actual: Float) = assertEquals(expected, actual, 0.001f)

    @Test fun `hands point to twelve at midnight`() {
        val a = clockHandAngles(0, 0, 0, 0)
        near(0f, a.hour); near(0f, a.minute); near(0f, a.second)
    }

    @Test fun `minute and hour hands move continuously with seconds`() {
        val a = clockHandAngles(3, 30, 30, 500)
        near(30.5f * 6f, a.second)
        near((30f + 30.5f / 60f) * 6f, a.minute)
        near((3f + (30f + 30.5f / 60f) / 60f) * 30f, a.hour)
    }

    @Test fun `afternoon hours wrap to the twelve hour dial`() {
        near(clockHandAngles(3, 0, 0, 0).hour, clockHandAngles(15, 0, 0, 0).hour)
    }

    @Test fun `memo and checklist content round trips and ignores damage`() {
        val note = NoteContent("Shop", "milk", listOf(ChecklistItem("eggs", true), ChecklistItem("bread")))
        assertEquals(note, decodeNoteContent(encodeNoteContent(note)))
        assertEquals(NoteContent(), decodeNoteContent("{broken"))
        assertEquals(NoteContent(), decodeNoteContent(null))
    }

    @Test fun `every built-in id is named and accepted by backup`() {
        assertEquals(BUILTIN_WIDGETS, BUILTIN_WIDGET_NAMES.keys)
        assertTrue(BUILTIN_WIDGETS.containsAll(PICKABLE_BUILTIN_WIDGETS))
    }
}
