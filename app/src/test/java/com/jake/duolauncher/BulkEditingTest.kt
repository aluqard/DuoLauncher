package com.jake.duolauncher

import org.junit.Assert.*
import org.junit.Test

class BulkEditingTest {
    private fun page(vararg cells: Pair<Int, String>): List<String?> =
        MutableList<String?>(HOME_CELLS) { null }.apply { cells.forEach { (i, id) -> this[i] = id } }

    @Test fun `bulk remove clears home and dock in one step`() {
        val layout = HomeLayout(page(8 to "a", 9 to "b", 10 to "c"), listOf("d", null, null, null))
        val next = bulkRemove(layout, listOf("a", "c", "d"))
        assertEquals(listOf("b"), next.slots.filterNotNull())
        assertTrue(next.dock.all { it == null })
    }

    @Test fun `bulk move fills free cells on the target page and reports overflow`() {
        val second = page(*Array(HOME_CELLS - 1) { it to "x$it" })
        val layout = HomeLayout(page(8 to "a", 9 to "b") + second, List(4) { null })
        val (next, moved) = bulkMoveToPage(layout, listOf("a", "b"), page = 1)
        assertEquals(1, moved)
        assertEquals(1, homeCellPage(next.indexOfShortcut("a")!!))
        assertEquals(0, homeCellPage(next.indexOfShortcut("b")!!))
    }

    @Test fun `bulk folder groups every selected app at the first app's cell`() {
        val layout = HomeLayout(page(8 to "a", 9 to "b", 12 to "c"), List(4) { null })
        val folder = FolderEntry(newFolderId(), "Folder", emptyList())
        val next = bulkFolder(layout, listOf("a", "b", "c"), folder)
        assertEquals(folder.id, next.slotAt(8))
        assertEquals(listOf("a", "b", "c"), next.folder(folder.id)?.appIds)
        assertEquals(layout, bulkFolder(layout, listOf("a"), folder))
    }

    @Test fun `remapping pages reorders apps and widgets and deletes left out pages`() {
        val widget = WidgetPlacement(4, 77, 1, 0, 0, 2, 2)
        val layout = HomeLayout(page(8 to "a") + page(8 to "b") + page(8 to "c"), List(4) { null }, listOf(widget))
        val swapped = remapPages(layout, listOf(1, 0, 2))
        assertEquals(homeCellIndex(1, 8), swapped.indexOfShortcut("a"))
        assertEquals(homeCellIndex(0, 8), swapped.indexOfShortcut("b"))
        assertEquals(0, swapped.widgetPlacements.single().page)
        val deleted = remapPages(layout, listOf(0, 2))
        assertNull(deleted.indexOfShortcut("b"))
        assertTrue(deleted.widgetPlacements.isEmpty())
        assertEquals(homeCellIndex(1, 8), deleted.indexOfShortcut("c"))
    }

    @Test fun `new pages from the page editor start empty and an empty last page does not persist`() {
        val widget = WidgetPlacement(4, 77, 1, 0, 0, 2, 2)
        val layout = HomeLayout(page(8 to "a") + page(8 to "b"), List(4) { null }, listOf(widget))
        val inserted = remapPages(layout, listOf(0, NEW_PAGE, 1))
        assertEquals(3, inserted.pageCount)
        assertEquals(homeCellIndex(0, 8), inserted.indexOfShortcut("a"))
        assertEquals(homeCellIndex(2, 8), inserted.indexOfShortcut("b"))
        assertEquals(2, inserted.widgetPlacements.single().page)
        assertTrue((0 until HOME_CELLS).all { inserted.slotAt(homeCellIndex(1, it)) == null })
        // Pages are only as many as their contents need, so a new page left at the end disappears.
        val trailing = remapPages(layout, listOf(0, 1, NEW_PAGE, NEW_PAGE - 1))
        assertEquals(2, trailing.pageCount)
        assertEquals(normalizeHomeSlots(layout.slots), trailing.slots)
    }
}
