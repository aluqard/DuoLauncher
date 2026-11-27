package com.jake.duolauncher

import org.junit.Assert.assertEquals
import org.junit.Test

class BadgesTest {
    @Test fun `badges count per package and profile and skip ongoing and group summaries`() {
        val counts = countBadges(listOf(
            BadgeNotification("chat", 0, ongoing = false, groupSummary = false),
            BadgeNotification("chat", 0, ongoing = false, groupSummary = false),
            BadgeNotification("chat", 0, ongoing = false, groupSummary = true),
            BadgeNotification("chat", 10, ongoing = false, groupSummary = false),
            BadgeNotification("music", 0, ongoing = true, groupSummary = false),
        ))
        assertEquals(mapOf(badgeKey("chat", 0) to 2, badgeKey("chat", 10) to 1), counts)
    }
}

class ShortcutIdTest {
    @org.junit.Test fun `shortcut slot ids round trip including unusual characters`() {
        val key = ShortcutKey("com.example.maps", "home/route@work 1", 10)
        val id = shortcutSlotId(key)
        org.junit.Assert.assertTrue(isShortcutId(id))
        org.junit.Assert.assertEquals(key, parseShortcutSlotId(id))
        org.junit.Assert.assertNull(parseShortcutSlotId("com.example/.Main"))
        org.junit.Assert.assertNull(parseShortcutSlotId("shortcut:broken"))
    }

    @org.junit.Test fun `shortcuts are removed only for uninstalled apps or confirmed unpinning`() {
        val a = shortcutSlotId(ShortcutKey("a", "x", 0)); val b = shortcutSlotId(ShortcutKey("b", "y", 0))
        val ids = listOf(a, b, "c/.Main")
        org.junit.Assert.assertEquals(setOf(a), removedShortcutIds(ids, setOf(0L to "a"), pinned = null))
        org.junit.Assert.assertEquals(setOf(b), removedShortcutIds(ids, emptySet(), pinned = setOf(a)))
        org.junit.Assert.assertEquals(emptySet<String>(), removedShortcutIds(ids, emptySet(), pinned = null))
        org.junit.Assert.assertEquals(emptySet<String>(), removedAppIds(listOf(a), emptySet(), setOf(0L), emptySet(), emptySet(), 0L))
    }
}

class AutoBackupTest {
    @org.junit.Test fun `backup policy respects change detection and period`() {
        val day = 24L * 60 * 60 * 1000
        org.junit.Assert.assertTrue(shouldWriteAutoBackup(AutoBackupPolicy.EVERY_CHANGE, 1000, 1001, changed = true))
        org.junit.Assert.assertFalse(shouldWriteAutoBackup(AutoBackupPolicy.EVERY_CHANGE, 1000, 1001, changed = false))
        org.junit.Assert.assertFalse(shouldWriteAutoBackup(AutoBackupPolicy.DAILY, 0, day - 1, changed = true))
        org.junit.Assert.assertTrue(shouldWriteAutoBackup(AutoBackupPolicy.DAILY, 0, day, changed = true))
        org.junit.Assert.assertTrue(shouldWriteAutoBackup(AutoBackupPolicy.WEEKLY, null, 5, changed = true))
        org.junit.Assert.assertFalse(shouldWriteAutoBackup(AutoBackupPolicy.OFF, null, 5, changed = true))
    }

    @org.junit.Test fun `rotation deletes only the oldest backups beyond the limit`() {
        org.junit.Assert.assertEquals(listOf(2L, 1L), autoBackupsToDelete(listOf(1, 5, 2, 4, 3), keep = 3))
        org.junit.Assert.assertEquals(emptyList<Long>(), autoBackupsToDelete(listOf(1, 2), keep = 7))
    }
}
