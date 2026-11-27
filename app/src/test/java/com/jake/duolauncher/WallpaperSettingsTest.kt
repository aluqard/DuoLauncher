package com.jake.duolauncher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WallpaperSettingsTest {
    @Test fun `settings survive a round trip and default sensibly`() {
        val custom = WallpaperSettings(coverPhoto = true, motion = false, glassShadow = true, device = DeviceFit.FOLD8)
        assertEquals(custom, decodeWallpaperSettings(encodeWallpaperSettings(custom)))
        assertEquals(WallpaperSettings(), decodeWallpaperSettings(null))
        assertEquals(WallpaperSettings(), decodeWallpaperSettings("not json"))
        // Motion is on unless turned off; an unknown device falls back to detection.
        assertEquals(true, decodeWallpaperSettings("{}").motion)
        assertNull(decodeWallpaperSettings("{\"device\":\"PIXEL_FOLD_99\"}").device)
    }

    @Test fun `device detection uses model codes then the hinge`() {
        assertEquals(DeviceFit.FOLD7, detectDeviceFit("samsung", "SM-F966N", hasHinge = true))
        assertNull(detectDeviceFit("Google", "Pixel 9 Pro Fold", hasHinge = true))
        assertEquals(DeviceFit.PHONE, detectDeviceFit("Google", "Pixel 8", hasHinge = false))
    }
}
