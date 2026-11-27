package com.jake.duolauncher

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDateTime

class ThemeTest {
    @Test fun `appfilter parsing maps components and pack decoration`() {
        val filter = parseAppFilter(sequenceOf(
            "item" to mapOf("component" to "ComponentInfo{com.mail/com.mail.Main}", "drawable" to "mail"),
            "item" to mapOf("component" to "ComponentInfo{com.maps/.Home}", "drawable" to "maps"),
            "item" to mapOf("component" to "ComponentInfo{com.mail/com.mail.Main}", "drawable" to "mail_alt"),
            "item" to mapOf("component" to ":broken", "drawable" to "x"),
            "iconback" to mapOf("img2" to "back2", "img1" to "back1"),
            "iconmask" to mapOf("img1" to "mask"),
            "iconupon" to mapOf("img1" to "upon"),
            "scale" to mapOf("factor" to "0.8"),
        ))
        assertEquals(mapOf("com.mail/com.mail.Main" to "mail", "com.maps/com.maps.Home" to "maps"), filter.icons)
        assertEquals(listOf("back1", "back2"), filter.backs)
        assertEquals("mask", filter.mask); assertEquals("upon", filter.upon); assertEquals(.8f, filter.scale, .0001f)
    }

    @Test fun `squircle outline stays inside its box and reaches each edge`() {
        val points = superellipsePoints(100f)
        assertTrue(points.all { (x, y) -> x in -0.01f..100.01f && y in -0.01f..100.01f })
        assertEquals(100f, points.maxOf { it.first }, .01f); assertEquals(0f, points.minOf { it.second }, .01f)
    }

    @Test fun `scheduled looks pick the most recent trigger within a week`() {
        val work = LookPreset("work", "Work", ThemeSettings(), LayoutPreset(), LayoutPreset(), false,
            listOf(PresetSchedule(WEEKDAYS, 8 * 60)))
        val night = LookPreset("night", "Night", ThemeSettings(), LayoutPreset(), LayoutPreset(), false,
            listOf(PresetSchedule((1..7).toSet(), 21 * 60)))
        // 2026-11-26 is a Thursday.
        assertEquals("work", latestScheduledTrigger(listOf(work, night), LocalDateTime.of(2026, 11, 26, 9, 0))?.first)
        assertEquals("night", latestScheduledTrigger(listOf(work, night), LocalDateTime.of(2026, 11, 26, 22, 0))?.first)
        assertEquals("night", latestScheduledTrigger(listOf(work, night), LocalDateTime.of(2026, 11, 28, 9, 0))?.first) // Saturday
        assertNull(latestScheduledTrigger(listOf(work.copy(schedules = emptyList())), LocalDateTime.of(2026, 11, 26, 9, 0)))
    }

    @Test fun `look presets round trip`() {
        val preset = LookPreset("a", "Calm", ThemeSettings(iconShape = IconShape.CIRCLE), LayoutPreset(iconSize = 50f),
            LayoutPreset(dockSide = DockSide.BOTTOM), true, listOf(PresetSchedule(setOf(6, 7), 600)))
        assertEquals(listOf(preset), decodeLookPresets(encodeLookPresets(listOf(preset))))
        assertEquals(emptyList<LookPreset>(), decodeLookPresets("garbage"))
    }

    @Test fun `duo color theme keeps the original palette`() {
        assertEquals(LightDuoPalette, themedPalette(LightDuoPalette, ThemeSettings()))
        assertNotEquals(DarkDuoPalette, themedPalette(DarkDuoPalette, ThemeSettings(colorTheme = "forest")))
    }
}

class DockSideGeometryTest {
    @org.junit.Test fun `bottom dock frees the side column and reserves a bar below the grid`() {
        val right = homeGeometry(400f, 860f, LayoutPreset(), labels = true)
        val bottom = homeGeometry(400f, 860f, LayoutPreset(dockSide = DockSide.BOTTOM), labels = true)
        org.junit.Assert.assertEquals(DockSide.RIGHT, right.dockSide)
        org.junit.Assert.assertEquals(0f, right.bottomDockHeight, 0f)
        org.junit.Assert.assertEquals(DockSide.BOTTOM, bottom.dockSide)
        org.junit.Assert.assertTrue(bottom.gridWidth > right.gridWidth)
        org.junit.Assert.assertTrue(bottom.bottomDockHeight > 0f)
        org.junit.Assert.assertTrue(bottom.dockWidth < bottom.gridWidth)
        org.junit.Assert.assertTrue(bottom.dockRowHeight > bottom.dockIconSize)
    }

    @org.junit.Test fun `unfolded layout always keeps the right dock`() {
        val expanded = homeGeometry(900f, 860f, LayoutPreset(dockSide = DockSide.BOTTOM), labels = true)
        org.junit.Assert.assertEquals(DockSide.RIGHT, expanded.dockSide)
        org.junit.Assert.assertEquals(DockSide.LEFT, effectiveDockSide(LayoutPreset(dockSide = DockSide.LEFT), expanded = false))
    }
}

class LibraryGroupTest {
    private data class App(val id: String, val category: String = "other", val installedAt: Long = 0L)

    @org.junit.Test fun `library groups suggestions recent installs and categories in fixed order`() {
        val day = 24L * 60 * 60 * 1000
        val now = 100 * day
        val apps = listOf(App("chat", "social"), App("docs", "productivity", now - 2 * day), App("game", "games"),
            App("old", "productivity", now - 30 * day), App("misc"))
        val groups = groupLibrary(apps, App::id, App::category, App::installedAt,
            overrides = mapOf("misc" to "games"), recentIds = listOf("game", "gone", "chat"), now = now)
        org.junit.Assert.assertEquals(listOf("suggestions", "recent", "productivity", "social", "games"), groups.map { it.key })
        org.junit.Assert.assertEquals(listOf("game", "chat"), groups[0].apps.map { it.id })
        org.junit.Assert.assertEquals(listOf("docs"), groups[1].apps.map { it.id })
        org.junit.Assert.assertEquals(listOf("game", "misc"), groups.last().apps.map { it.id })
    }

    @org.junit.Test fun `android categories map to library keys`() {
        org.junit.Assert.assertEquals("games", categoryKey(android.content.pm.ApplicationInfo.CATEGORY_GAME))
        org.junit.Assert.assertEquals("other", categoryKey(-1))
        org.junit.Assert.assertTrue(CATEGORY_TITLES.keys == APP_CATEGORY_KEYS)
    }

    @org.junit.Test fun `undeclared apps are placed by package name`() {
        val expected = mapOf(
            "com.google.android.deskclock" to "utilities", "com.google.android.contacts" to "utilities",
            "com.android.settings" to "utilities", "com.google.android.apps.nbu.files" to "utilities",
            "com.google.android.googlequicksearchbox" to "utilities", "com.google.android.dialer" to "utilities",
            "com.android.chrome" to "productivity", "com.google.android.gm" to "productivity",
            "com.google.android.calendar" to "productivity", "com.google.android.apps.docs" to "productivity",
            "com.google.android.apps.photos" to "photos", "com.android.camera2" to "photos",
            "com.google.android.youtube" to "entertainment", "com.google.android.apps.youtube.music" to "entertainment",
            "com.google.android.apps.messaging" to "social", "com.google.android.apps.maps" to "travel",
            "com.android.vending" to "other", "com.example.unknown" to "other",
        )
        expected.forEach { (pkg, key) -> org.junit.Assert.assertEquals(pkg, key, categoryKey(-1, pkg)) }
        // A declared category wins over the package name.
        org.junit.Assert.assertEquals("games", categoryKey(android.content.pm.ApplicationInfo.CATEGORY_GAME, "com.android.settings"))
        org.junit.Assert.assertEquals("entertainment", categoryKey(android.content.pm.ApplicationInfo.CATEGORY_AUDIO))
    }

    @org.junit.Test fun `earlier category keys fold into current ones`() {
        org.junit.Assert.assertEquals("entertainment", normalizeCategoryKey("video"))
        org.junit.Assert.assertEquals("travel", normalizeCategoryKey("maps"))
        org.junit.Assert.assertEquals("utilities", normalizeCategoryKey("accessibility"))
        org.junit.Assert.assertEquals("social", normalizeCategoryKey("social"))
        org.junit.Assert.assertTrue(APP_CATEGORY_KEYS.containsAll(listOf("audio", "video", "maps", "accessibility").map(::normalizeCategoryKey)))
    }

    @org.junit.Test fun `category tiles show four large icons or three plus a preview`() {
        org.junit.Assert.assertEquals(listOf(1, 2, 3, 4) to emptyList<Int>(), categoryTileSlots(listOf(1, 2, 3, 4)))
        org.junit.Assert.assertEquals(listOf(1) to emptyList<Int>(), categoryTileSlots(listOf(1)))
        org.junit.Assert.assertEquals(listOf(1, 2, 3) to listOf(4, 5), categoryTileSlots(listOf(1, 2, 3, 4, 5)))
        org.junit.Assert.assertEquals(listOf(1, 2, 3) to listOf(4, 5, 6, 7), categoryTileSlots((1..9).toList()))
    }
}
