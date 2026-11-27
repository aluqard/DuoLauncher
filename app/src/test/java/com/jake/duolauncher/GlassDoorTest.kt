package com.jake.duolauncher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GlassDoorTest {
    @Test fun `frost is full while folded, clears as the hinge opens flat`() {
        assertEquals(0f, glassDoorAmount(null), 0f)
        assertEquals(0f, glassDoorAmount(Float.NaN), 0f)
        assertEquals(1f, glassDoorAmount(30f), 0f)
        assertEquals(1f, glassDoorAmount(GLASS_DOOR_FULL), 0f)
        assertEquals(0f, glassDoorAmount(GLASS_DOOR_FLAT), 0f)
        assertEquals(0f, glassDoorAmount(180f), 0f)
        // Halfway between full and flat sits at half frost, and the curve only ever falls while opening.
        assertEquals(.5f, glassDoorAmount((GLASS_DOOR_FULL + GLASS_DOOR_FLAT) / 2f), .001f)
        var previous = 1f
        for (angle in 90..180) {
            val amount = glassDoorAmount(angle.toFloat())
            assertTrue("$angle°", amount <= previous + 1e-6f)
            previous = amount
        }
    }

    @Test fun `hinges that report only stops are eased in time`() {
        assertTrue(coarseHinge(90f))
        assertFalse(coarseHinge(1f))
    }

    @Test fun `crease falls back to the middle of the long side`() {
        assertEquals(GlassDoorLine(1000f, false), glassDoorLine(null, 2000f, 1800f))
        assertEquals(GlassDoorLine(1100f, true), glassDoorLine(null, 1000f, 2200f))
    }

    @Test fun `cover screen is clear when shut and fully frosted where the inner screen takes over`() {
        assertEquals(0f, coverDoorAmount(null, 30f), 0f)
        assertEquals(0f, coverDoorAmount(2f, 30f), 0f)
        assertEquals(0f, coverDoorAmount(GLASS_DOOR_CLOSED, 30f), 0f)
        assertEquals(1f, coverDoorAmount(30f, 30f), 0f)
        assertEquals(1f, coverDoorAmount(60f, 30f), 0f)
        assertEquals(.5f, coverDoorAmount((GLASS_DOOR_CLOSED + 30f) / 2f, 30f), .001f)
    }

    @Test fun `hand-over angle is learned only when believable`() {
        assertEquals(32f, handoverAngle(34f, 30f)!!, 0f)
        assertEquals(null, handoverAngle(2f, 4f))
        assertEquals(null, handoverAngle(176f, 170f))
    }
}
