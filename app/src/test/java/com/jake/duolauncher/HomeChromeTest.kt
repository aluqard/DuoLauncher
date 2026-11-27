package com.jake.duolauncher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class HomeChromeTest {
    @Test fun `rubber band keeps direction, grows slower and never passes the limit`() {
        assertEquals(0f, rubberBand(0f, 100f), 0f)
        assertTrue(rubberBand(50f, 100f) > 0f)
        assertTrue(rubberBand(-50f, 100f) < 0f)
        assertTrue(rubberBand(100f, 100f) - rubberBand(50f, 100f) < rubberBand(50f, 100f))
        assertTrue(abs(rubberBand(100_000f, 100f)) <= 100f)
        assertEquals(0f, rubberBand(80f, 0f), 0f)
    }

    @Test fun `settings search matches titles and keywords without case`() {
        val titles = matchingSettings("ca").map { it.title }
        assertTrue("Notifications" in titles)
        assertTrue("Position" in titles)
        assertTrue("Apps" in titles)
        assertEquals(listOf("Wi-Fi"), matchingSettings("WIFI").map { it.title })
        assertTrue(matchingSettings("  ").isEmpty())
    }
}
