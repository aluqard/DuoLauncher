package com.jake.duolauncher

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class LauncherExtrasTest {
    private val sample = LauncherExtras(
        hiddenApps = setOf("a/.Main", "b/.Main"),
        appOverrides = mapOf("a/.Main" to AppOverride(label = "Mail", icon = "pack:com.pack/mail")),
        categoryOverrides = mapOf("b/.Main" to "games"),
        mainPage = 1,
        widgetContent = mapOf(3 to "{\"text\":\"milk\"}"),
    )

    @Test fun `schema 9 extras round trip through json`() {
        val json = encodeLauncherExtras(sample, JSONObject())
        assertEquals(sample, decodeLauncherExtras(JSONObject(json.toString())))
    }

    @Test fun `schema 8 payload without extras decodes to defaults`() {
        assertEquals(LauncherExtras(), decodeLauncherExtras(JSONObject("{\"schema\":8,\"homeSlots\":[]}")))
    }

    @Test fun `invalid extras are rejected instead of silently changed`() {
        assertThrows(Exception::class.java) { decodeLauncherExtras(JSONObject("{\"categoryOverrides\":{\"a\":\"nope\"}}")) }
        assertThrows(Exception::class.java) { decodeLauncherExtras(JSONObject("{\"widgetContent\":{\"-1\":\"x\"}}")) }
        assertThrows(Exception::class.java) { decodeLauncherExtras(JSONObject("{\"mainPage\":500}")) }
    }

    @Test fun `pruning drops removed apps empty overrides stale widgets and clamps main page`() {
        val extras = sample.copy(appOverrides = sample.appOverrides + ("b/.Main" to AppOverride()))
        val pruned = extras.pruned(knownApps = setOf("a/.Main"), widgetSlots = emptySet(), pageCount = 1)
        assertEquals(setOf("a/.Main"), pruned.hiddenApps)
        assertEquals(setOf("a/.Main"), pruned.appOverrides.keys)
        assertTrue(pruned.categoryOverrides.isEmpty())
        assertTrue(pruned.widgetContent.isEmpty())
        assertEquals(0, pruned.mainPage)
    }

    @Test fun `dock side parsing falls back to the original right dock`() {
        assertEquals(DockSide.BOTTOM, dockSideFrom("BOTTOM"))
        assertEquals(DockSide.RIGHT, dockSideFrom(null))
        assertEquals(DockSide.RIGHT, dockSideFrom("diagonal"))
    }

    @Test fun `theme settings round trip and repair unknown values`() {
        val value = ThemeSettings(iconStyle = IconStyle.GLYPH, iconShape = IconShape.SQUIRCLE, iconTone = IconTone.TINTED,
            darkIconsAtNight = true, iconPack = "com.example.pack", colorTheme = "forest", glassOpacity = .5f)
        assertEquals(value, decodeThemeSettings(encodeThemeSettings(value)))
        assertEquals(ThemeSettings(), decodeThemeSettings("{\"iconStyle\":\"???\",\"colorTheme\":\"missing\"}").copy())
        assertEquals(ThemeSettings(), decodeThemeSettings("not json"))
    }

    @Test fun `dark icons at night only darkens the light tone while dark`() {
        val theme = ThemeSettings(darkIconsAtNight = true)
        assertEquals(IconTone.DARK, theme.effectiveTone(dark = true))
        assertEquals(IconTone.LIGHT, theme.effectiveTone(dark = false))
        assertEquals(IconTone.TINTED, theme.copy(iconTone = IconTone.TINTED).effectiveTone(dark = true))
        assertNotEquals(theme.iconKey(true), theme.iconKey(false))
    }
}
