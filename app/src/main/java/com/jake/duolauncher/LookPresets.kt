package com.jake.duolauncher

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.util.UUID

/** Switch to a preset at [minuteOfDay] on each of [days] (ISO day numbers, Monday = 1). */
data class PresetSchedule(val days: Set<Int>, val minuteOfDay: Int)

/** A saved look: icon/color theme, layout sizes for both screens, and optionally a background photo copy. */
data class LookPreset(
    val id: String,
    val name: String,
    val theme: ThemeSettings,
    val compact: LayoutPreset,
    val expanded: LayoutPreset,
    val hasPhoto: Boolean,
    val schedules: List<PresetSchedule> = emptyList(),
)

private fun encodePreset(p: LayoutPreset) = JSONObject().put("iconSize", p.iconSize.toDouble()).put("rowGap", p.rowGap.toDouble())
    .put("dockWidth", p.dockWidth.toDouble()).put("dockPosition", p.dockPosition.toDouble())
    .put("dockAlignToGrid", p.dockAlignToGrid).put("dockSide", p.dockSide.name).put("topMargin", p.topMargin.toDouble())

private fun decodePreset(j: JSONObject) = LayoutPreset(j.getDouble("iconSize").toFloat(), j.getDouble("rowGap").toFloat(),
    j.getDouble("dockWidth").toFloat(), j.getDouble("dockPosition").toFloat(), j.getBoolean("dockAlignToGrid"),
    dockSideFrom(j.optString("dockSide")), j.optDouble("topMargin", AUTO_TOP_MARGIN.toDouble()).toFloat()).sanitized()

fun encodeLookPresets(presets: List<LookPreset>): String = JSONArray().also { array ->
    presets.forEach { p ->
        array.put(JSONObject().put("id", p.id).put("name", p.name).put("theme", encodeThemeSettings(p.theme))
            .put("compact", encodePreset(p.compact)).put("expanded", encodePreset(p.expanded)).put("hasPhoto", p.hasPhoto)
            .put("schedules", JSONArray().also { s -> p.schedules.forEach { sch ->
                s.put(JSONObject().put("days", JSONArray(sch.days.sorted())).put("minute", sch.minuteOfDay)) } }))
    }
}.toString()

fun decodeLookPresets(raw: String?): List<LookPreset> = runCatching {
    if (raw.isNullOrBlank()) return emptyList()
    val array = JSONArray(raw)
    List(array.length()) { index ->
        val j = array.getJSONObject(index)
        val schedules = j.optJSONArray("schedules")?.let { s -> List(s.length()) { k ->
            val item = s.getJSONObject(k); val days = item.getJSONArray("days")
            PresetSchedule(List(days.length()) { days.getInt(it) }.filter { it in 1..7 }.toSet(), item.getInt("minute").coerceIn(0, 1439))
        } }.orEmpty().filter { it.days.isNotEmpty() }
        LookPreset(j.getString("id"), j.getString("name"), decodeThemeSettings(j.getString("theme")),
            decodePreset(j.getJSONObject("compact")), decodePreset(j.getJSONObject("expanded")), j.optBoolean("hasPhoto"), schedules)
    }
}.getOrDefault(emptyList())

/** The most recent schedule trigger at or before [now] (looking back one week), or null when nothing is scheduled. */
fun latestScheduledTrigger(presets: List<LookPreset>, now: LocalDateTime): Pair<String, LocalDateTime>? =
    presets.flatMap { preset -> preset.schedules.flatMap { schedule ->
        (0..7).mapNotNull { back ->
            val day = now.toLocalDate().minusDays(back.toLong())
            if (day.dayOfWeek.value !in schedule.days) return@mapNotNull null
            val at = day.atStartOfDay().plusMinutes(schedule.minuteOfDay.toLong())
            if (at.isAfter(now)) null else preset.id to at
        }
    } }.maxByOrNull { it.second }

val WEEKDAYS = (1..5).toSet()
val WEEKEND = setOf(6, 7)
fun dayLabel(day: Int) = DayOfWeek.of(day).getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.getDefault())

/** Presets live in SharedPreferences "look_presets"; photo copies in files/presets/<id>.jpg. */
object LookPresetStore {
    private const val PREFS = "look_presets"
    private val presets = MutableStateFlow<List<LookPreset>>(emptyList())
    private var loaded = false

    fun presets(context: Context): StateFlow<List<LookPreset>> {
        if (!loaded) { loaded = true; presets.value = decodeLookPresets(context.getSharedPreferences(PREFS, 0).getString("presets", null)) }
        return presets.asStateFlow()
    }

    fun photo(context: Context, id: String) = File(File(context.filesDir, "presets").apply { mkdirs() }, "$id.jpg")

    private fun save(context: Context, value: List<LookPreset>) {
        presets.value = value
        context.getSharedPreferences(PREFS, 0).edit().putString("presets", encodeLookPresets(value)).apply()
    }

    fun put(context: Context, preset: LookPreset) {
        val current = presets(context).value
        save(context, if (current.any { it.id == preset.id }) current.map { if (it.id == preset.id) preset else it } else current + preset)
    }

    fun delete(context: Context, id: String) {
        save(context, presets(context).value.filterNot { it.id == id })
        photo(context, id).delete()
    }

    /** Last schedule trigger already applied, so a manual change is not undone until the next trigger. */
    fun lastAppliedTrigger(context: Context): String? = context.getSharedPreferences(PREFS, 0).getString("lastTrigger", null)
    fun markApplied(context: Context, trigger: String) = context.getSharedPreferences(PREFS, 0).edit().putString("lastTrigger", trigger).apply()

    fun newId() = UUID.randomUUID().toString()
}

/** Captures the current look as a new preset. */
fun captureLookPreset(context: Context, name: String, state: LauncherState, backgrounds: LauncherBackgroundController): LookPreset {
    val id = LookPresetStore.newId()
    val hasPhoto = backgrounds.snapshotTo(LookPresetStore.photo(context, id))
    return LookPreset(id, name.ifBlank { "Look" }, ThemeStore.load(context).value, state.compact, state.expanded, hasPhoto)
}

/** Applies a preset's theme, sizes and background. */
fun applyLookPreset(context: Context, preset: LookPreset, model: LauncherModel, backgrounds: LauncherBackgroundController) {
    ThemeStore.update(context) { preset.theme }
    model.setPreset(false, preset.compact)
    model.setPreset(true, preset.expanded)
    backgrounds.applyFromFile(LookPresetStore.photo(context, preset.id).takeIf { preset.hasPhoto && it.isFile })
}
