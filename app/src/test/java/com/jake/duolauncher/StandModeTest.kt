package com.jake.duolauncher

import org.junit.Assert.*
import org.junit.Test

/** Readings follow what the Pixel 9 Pro Fold emulator reports for each posture. */
class StandModeTest {
    private fun r(hinge: Float?, x: Float, y: Float, z: Float, charging: Boolean = false) = PostureReading(hinge, x, y, z, charging)

    @Test fun `half folded upright is a book and level hinge is a laptop`() {
        assertEquals(StandPosture.BOOK, classifyPosture(r(120f, 0f, 9.78f, .81f)))
        assertEquals(StandPosture.LAPTOP, classifyPosture(r(100f, 9.78f, 0f, .81f)))
    }

    @Test fun `folded on its side needs a charger`() {
        assertEquals(StandPosture.COVER_SIDEWAYS, classifyPosture(r(0f, 9.78f, 0f, .81f, charging = true)))
        assertEquals(StandPosture.NONE, classifyPosture(r(0f, 9.78f, 0f, .81f, charging = false)))
        assertEquals(StandPosture.NONE, classifyPosture(r(0f, 0f, 9.78f, .81f, charging = true)))
    }

    @Test fun `open phone only stands when upright on a charger`() {
        assertEquals(StandPosture.NONE, classifyPosture(r(180f, 0f, 9.78f, .81f)))
        assertEquals(StandPosture.OPEN_STANDING, classifyPosture(r(180f, 0f, 9.78f, .81f, charging = true)))
    }

    @Test fun `lying flat or without a hinge sensor never stands`() {
        assertEquals(StandPosture.NONE, classifyPosture(r(110f, 0f, 1f, 9.7f)))
        assertEquals(StandPosture.NONE, classifyPosture(r(null, 0f, 9.78f, .81f, charging = true)))
        assertEquals(StandPosture.NONE, classifyPosture(r(110f, 0f, 0f, 0f)))
    }

    @Test fun `settings decide whether a posture shows`() {
        val defaults = StandSettings()
        // Half-folded in the hand is not standing: by default Stand mode waits for the charger.
        assertFalse(shouldShowStand(StandPosture.BOOK, defaults, charging = false))
        assertTrue(shouldShowStand(StandPosture.BOOK, defaults, charging = true))
        assertTrue(shouldShowStand(StandPosture.BOOK, defaults.copy(trigger = StandTrigger.ALWAYS), charging = false))
        assertFalse(shouldShowStand(StandPosture.OPEN_STANDING, defaults, charging = true))
        assertFalse(shouldShowStand(StandPosture.BOOK, defaults.copy(trigger = StandTrigger.CHARGING), charging = false))
        assertFalse(shouldShowStand(StandPosture.BOOK, defaults.copy(trigger = StandTrigger.OFF), charging = true))
        assertEquals(defaults, decodeStandSettings(encodeStandSettings(defaults)))
    }

    @Test fun `night follows room light or the clock without a sensor`() {
        assertTrue(isNight(0f, 12)); assertFalse(isNight(400f, 23))
        assertTrue(isNight(null, 23)); assertFalse(isNight(null, 12))
    }
}
