package com.jake.duolauncher

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

enum class AutoBackupPolicy(val label: String, val minimumGapMs: Long) {
    OFF("Off", Long.MAX_VALUE),
    EVERY_CHANGE("Every change", 0L),
    DAILY("Daily", 24L * 60 * 60 * 1000),
    WEEKLY("Weekly", 7L * 24 * 60 * 60 * 1000),
}

data class AutoBackupSettings(val policy: AutoBackupPolicy = AutoBackupPolicy.EVERY_CHANGE, val keep: Int = 7)

data class AutoBackupFile(val file: File, val savedAt: Long)

/** Decides whether a changed layout should produce a new automatic backup. */
fun shouldWriteAutoBackup(policy: AutoBackupPolicy, lastSavedAt: Long?, now: Long, changed: Boolean): Boolean =
    changed && policy != AutoBackupPolicy.OFF && (lastSavedAt == null || now - lastSavedAt >= policy.minimumGapMs)

/** Oldest backups beyond [keep], newest first in [savedAt] order. */
fun autoBackupsToDelete(savedAt: List<Long>, keep: Int): List<Long> = savedAt.sortedDescending().drop(keep.coerceAtLeast(1))

/** Rotating layout backups kept in app-private storage (removed with the app unless Android backup keeps them). */
object AutoBackupStore {
    private const val PREFS = "autobackup"
    private const val PREFIX = "layout-"
    private val settingsFlow = MutableStateFlow(AutoBackupSettings())
    private val filesFlow = MutableStateFlow<List<AutoBackupFile>>(emptyList())
    private var loaded = false
    val files: StateFlow<List<AutoBackupFile>> = filesFlow.asStateFlow()

    private fun dir(context: Context) = File(context.filesDir, "autobackup").apply { mkdirs() }

    fun settings(context: Context): StateFlow<AutoBackupSettings> {
        if (!loaded) {
            loaded = true
            val prefs = context.getSharedPreferences(PREFS, 0)
            settingsFlow.value = AutoBackupSettings(
                AutoBackupPolicy.entries.firstOrNull { it.name == prefs.getString("policy", null) } ?: AutoBackupPolicy.EVERY_CHANGE,
                prefs.getInt("keep", 7).takeIf { it == 7 || it == 30 } ?: 7)
            refreshList(context)
        }
        return settingsFlow.asStateFlow()
    }

    fun update(context: Context, value: AutoBackupSettings) {
        settings(context)
        settingsFlow.value = value
        context.getSharedPreferences(PREFS, 0).edit().putString("policy", value.policy.name).putInt("keep", value.keep).apply()
        prune(context, value.keep)
    }

    fun refreshList(context: Context) {
        filesFlow.value = dir(context).listFiles().orEmpty()
            .mapNotNull { file -> file.name.removePrefix(PREFIX).removeSuffix(".json").toLongOrNull()?.let { AutoBackupFile(file, it) } }
            .sortedByDescending { it.savedAt }
    }

    /** Writes [raw] if it differs from the newest backup and the policy allows it. Call off the main thread. */
    fun save(context: Context, raw: String, now: Long = System.currentTimeMillis()): Boolean {
        val settings = settings(context).value
        val latest = filesFlow.value.firstOrNull()
        val changed = latest == null || runCatching { latest.file.readText() }.getOrNull() != raw
        if (!shouldWriteAutoBackup(settings.policy, latest?.savedAt, now, changed)) return false
        val target = File(dir(context), "$PREFIX$now.json")
        val temp = File(dir(context), "$PREFIX$now.tmp")
        temp.writeText(raw)
        if (!temp.renameTo(target)) { temp.delete(); return false }
        refreshList(context)
        prune(context, settings.keep)
        return true
    }

    private fun prune(context: Context, keep: Int) {
        val doomed = autoBackupsToDelete(filesFlow.value.map { it.savedAt }, keep).toSet()
        filesFlow.value.filter { it.savedAt in doomed }.forEach { it.file.delete() }
        refreshList(context)
    }
}
