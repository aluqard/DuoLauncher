package com.jake.duolauncher

import org.junit.Assert.*
import org.junit.Test

class LayoutModelTest {
    @Test fun `aligned side dock holds home-size icons that line up with the app rows`() {
        for (labels in listOf(true, false)) {
            val g = homeGeometry(851f, 850f, LayoutPreset(), labels)
            val firstImageTop = g.contentTop + g.widgetHeight + 18f
            assertEquals(g.rowHeight, g.dockRowHeight, .01f)
            assertEquals(4f * g.rowHeight, g.dockHeight, .01f)
            assertEquals(g.iconSize, g.dockIconSize, .01f)
            // Slots center their icons, so the first dock icon starts level with the first row of app icons.
            assertEquals(firstImageTop, g.dockTop + (g.dockRowHeight - g.dockIconSize) / 2f, .01f)
            val feed = homeGeometry(851f, 850f, LayoutPreset(), labels, inLibrary = true)
            assertEquals(g.dockTop, feed.dockTop, .01f)
            assertEquals(g.dockHeight, feed.dockHeight, .01f)
        }
    }
    @Test fun `side dock bar wraps the saved icon column in glass`() {
        val g = homeGeometry(851f, 850f, LayoutPreset(dockWidth = 68f), true)
        assertEquals(95f, g.dockWidth, .01f)
        assertEquals(g.homeWidth - 95f - 44f, g.gridWidth, .01f)
        // A narrow column shrinks its icons rather than overflowing the glass.
        assertEquals(56f, homeGeometry(851f, 850f, LayoutPreset(dockWidth = 56f), true).dockIconSize, .01f)
        // A bottom dock has no side bar and keeps its smaller icons.
        val bottom = homeGeometry(400f, 850f, LayoutPreset(dockSide = DockSide.BOTTOM), true)
        assertEquals(0f, bottom.dockWidth, .01f)
        assertEquals(dockIconSize(bottom.iconSize), bottom.dockIconSize, .01f)
    }
    @Test fun `manual dock mode keeps saved position and alignment does not overwrite it`() {
        val p = LayoutPreset(dockPosition = .43f, dockAlignToGrid = false)
        val manual = homeGeometry(475f, 700f, p, true)
        assertEquals(700f * .43f - 2f * manual.rowHeight, manual.dockTop, .01f)
        assertEquals(4f * manual.rowHeight, manual.dockHeight, .01f)
        assertEquals(.43f, p.copy(dockAlignToGrid = true).sanitized().dockPosition)
    }
    @Test fun `search keeps four complete dock targets between status and keyboard`() {
        for (width in listOf(475f, 933f)) for (height in listOf(310f, 330f, 375f, 420f)) {
            for (position in listOf(.25f, .56f, .75f)) {
                val g = homeGeometry(width, height, LayoutPreset(dockPosition = position), true,
                    statusHeight = 80f, inLibrary = true)
                assertTrue(g.dockTop >= 80f)
                assertTrue(g.dockTop + g.dockHeight <= height - 12f + .01f)
                assertTrue(g.dockRowHeight >= 48f)
                assertTrue(g.dockIconSize + 6f <= g.dockRowHeight + .01f)
                assertTrue(4f * g.dockRowHeight <= g.dockHeight + .01f)
            }
        }
    }
    @Test fun `hiding labels preserves row rhythm and large text gains room`() {
        val regular = homeGeometry(475f, 700f, LayoutPreset(), true)
        val hidden = homeGeometry(475f, 700f, LayoutPreset(), false)
        val largeText = homeGeometry(475f, 700f, LayoutPreset(), true, labelHeight = 34f)
        assertEquals(regular.rowHeight, hidden.rowHeight)
        assertTrue(largeText.rowHeight >= regular.rowHeight + 14f)
        // The side dock's glass bar leaves a slightly tighter grid, close to Duomo's 66dp icons in 80dp cells.
        assertTrue(regular.iconSize / (regular.gridWidth / 4f) in .70f.. .80f)
    }
    @Test fun `new icon default preserves tuned presets and current schema values`() {
        assertEquals(66f, upgradePreset(LayoutPreset(iconSize = 60f), 2, false).iconSize)
        val custom = LayoutPreset(62f, 13f, 72f, .67f)
        assertEquals(custom, upgradePreset(custom, 2, true))
        assertEquals(LayoutPreset(iconSize = 60f), upgradePreset(LayoutPreset(iconSize = 60f), 3, false))
        assertEquals(LayoutPreset(), upgradePreset(LayoutPreset(54f, 12f, 64f), 1, false))
        assertEquals(LayoutPreset(), upgradePreset(LayoutPreset(58f, 12f, 64f), 1, true))
    }
    @Test fun `dock fits above controls at Fold cover inner and short landscape sizes`() {
        val sizes = listOf(475f to 700f, 933f to 650f, 850f to 840f, 360f to 620f, 740f to 280f)
        for ((width, height) in sizes) for (position in listOf(.25f, .56f, .75f)) {
            val p = LayoutPreset(dockPosition = position)
            val g = homeGeometry(width, height, p, true)
            assertTrue("$width x $height", g.dockTop >= 8f)
            assertTrue("Dock overlaps bottom controls at $width x $height", g.dockTop + g.dockHeight <= height - 124f + .01f)
            assertTrue(g.gridWidth + sideDockBarWidth(p.dockWidth) + 24f <= g.homeWidth)
            assertTrue(g.iconSize + 8f <= g.gridWidth / 4f)
            if (g.dockHeight == 4f * g.rowHeight) {
                val library = homeGeometry(width, height, p, true, inLibrary = true)
                assertEquals("Paging must preserve even a low dock position", g.dockTop, library.dockTop, .01f)
            }
        }
    }
    @Test fun `expanded pane appears from actual window width`() {
        assertFalse(homeGeometry(475f, 700f, LayoutPreset(), true).expanded)
        assertTrue(homeGeometry(933f, 650f, LayoutPreset(), true).expanded)
        assertTrue(homeGeometry(933f, 650f, LayoutPreset(), true).homeWidth <= 460f)
    }
    @Test fun `raising dock cannot overlap measured status or lower controls`() {
        for ((height, status) in listOf(700f to 124f, 650f to 124f, 280f to 80f)) {
            for (position in listOf(.25f, .56f, .75f)) {
                val g = homeGeometry(475f, height, LayoutPreset(dockPosition = position), true, status)
                assertTrue(g.dockTop >= status)
                assertTrue(g.dockTop + g.dockHeight <= height - 124f + .01f)
                assertTrue(g.dockHeight >= 68f)
            }
        }
    }
    @Test fun `reconciliation preserves custom order while handling installs removals duplicates`() {
        assertEquals(listOf("c", "a", "d"), reconcileOrder(listOf("c", "gone", "a", "c"), listOf("a", "c", "d")))
        assertEquals(emptyList<String>(), reconcileOrder(listOf("a"), emptyList()))
    }
    @Test fun `reordering across page boundary does not drop apps`() {
        val apps = (0..31).map { "app$it" }
        val moved = moveApp(apps, "app16", -1)
        assertEquals("app16", moved[15])
        assertEquals("app15", moved[16])
        assertEquals(apps.toSet(), moved.toSet())
        assertEquals("app31", moveApp(apps, "app31", -100).first())
        assertEquals(apps, moveApp(apps, "missing", 1))
    }
    @Test fun `out of range preferences are constrained before layout`() {
        val p = LayoutPreset(999f, -40f, 2f, 12f).sanitized()
        assertEquals(68f, p.iconSize); assertEquals(0f, p.rowGap)
        assertEquals(56f, p.dockWidth); assertEquals(.75f, p.dockPosition)
    }
    @Test fun `new installs and refresh do not pin apps and empty home stays empty`() {
        assertEquals(listOf("c", "a"), reconcilePins(listOf("c", "gone", "a", "c"), listOf("a", "c", "new")))
        assertEquals(emptyList<String>(), reconcilePins(emptyList(), listOf("new")))
    }
    @Test fun `migration uses suggestions for auto sorted legacy home but retains manual first page`() {
        val installed = (0..31).map { "app%02d".format(it) }
        assertEquals(listOf("app20", "app10"), migrateHomePins(installed, installed, listOf("app20", "app10", "gone")))
        val custom = listOf("app31") + installed.dropLast(1)
        assertEquals(custom.take(16), migrateHomePins(custom, installed, listOf("app20")))
    }
    @Test fun `home always has a page independently of the library`() {
        assertEquals(1, homePageCount(0)); assertEquals(1, homePageCount(24)); assertEquals(2, homePageCount(25))
    }

    @Test fun `top margin is automatic until set, then fixed and kept in range`() {
        val auto = homeGeometry(411f, 860f, LayoutPreset(dockSide = DockSide.BOTTOM), true)
        assertTrue(auto.contentTop in 16f..72f)
        assertEquals(100f, homeGeometry(411f, 860f, LayoutPreset(dockSide = DockSide.BOTTOM, topMargin = 100f), true).contentTop, .001f)
        assertEquals(160f, LayoutPreset(topMargin = 400f).sanitized().topMargin, .001f)
        assertEquals(8f, LayoutPreset(topMargin = 2f).sanitized().topMargin, .001f)
        assertEquals(AUTO_TOP_MARGIN, LayoutPreset(topMargin = -5f).sanitized().topMargin, .001f)
    }
}
