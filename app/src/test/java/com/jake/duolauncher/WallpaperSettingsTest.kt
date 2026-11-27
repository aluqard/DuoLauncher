package com.jake.duolauncher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WallpaperSettingsTest {
    @Test fun `settings survive a round trip and default sensibly`() {
        val custom = WallpaperSettings(coverPhoto = true, motion = false, glassShadow = true, device = DeviceFit.FOLD8,
            scene = BuiltInScene.DUNES, glassDoor = false, glassDoorStrength = 1.3f, glassDoorSide = GlassDoorSide.BOTH)
        assertEquals(custom, decodeWallpaperSettings(encodeWallpaperSettings(custom)))
        assertEquals(WallpaperSettings(), decodeWallpaperSettings(null))
        assertEquals(WallpaperSettings(), decodeWallpaperSettings("not json"))
        // Motion is on unless turned off; an unknown device falls back to detection.
        assertEquals(true, decodeWallpaperSettings("{}").motion)
        assertNull(decodeWallpaperSettings("{\"device\":\"PIXEL_FOLD_99\"}").device)
        // Without a saved scene, or with an unknown one, the starry night is shown.
        assertEquals(BuiltInScene.NIGHT, decodeWallpaperSettings("{}").scene)
        assertEquals(BuiltInScene.NIGHT, decodeWallpaperSettings("{\"scene\":\"MOON\"}").scene)
        // The glass door is on by default, and a stored strength is kept in range.
        assertEquals(true, decodeWallpaperSettings("{}").glassDoor)
        assertEquals(GlassDoorSide.LEFT, decodeWallpaperSettings("{}").glassDoorSide)
        assertEquals(1.5f, decodeWallpaperSettings("{\"glassDoorStrength\":9}").glassDoorStrength, 0f)
    }

    @Test fun `device detection uses model codes then the hinge`() {
        assertEquals(DeviceFit.FOLD7, detectDeviceFit("samsung", "SM-F966N", hasHinge = true))
        assertNull(detectDeviceFit("Google", "Pixel 9 Pro Fold", hasHinge = true))
        assertEquals(DeviceFit.PHONE, detectDeviceFit("Google", "Pixel 8", hasHinge = false))
    }
}
