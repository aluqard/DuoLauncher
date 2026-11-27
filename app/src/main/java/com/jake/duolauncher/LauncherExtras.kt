package com.jake.duolauncher

import org.json.JSONArray
import org.json.JSONObject

/** Which edge of Home holds the dock. RIGHT is the original foldable layout. */
enum class DockSide { RIGHT, BOTTOM, LEFT }

/** Per-app presentation changes. Null fields keep the installed app's own label or icon. */
data class AppOverride(val label: String? = null, val icon: String? = null) {
    val isEmpty: Boolean get() = label.isNullOrBlank() && icon.isNullOrBlank()
}

/**
 * Launcher state added in saved-state schema 9. Kept as one value so persistence, backup and
 * tests share a single codec instead of repeating field handling.
 */
data class LauncherExtras(
    val hiddenApps: Set<String> = emptySet(),
    val appOverrides: Map<String, AppOverride> = emptyMap(),
    val categoryOverrides: Map<String, String> = emptyMap(),
    val mainPage: Int = 0,
    val widgetContent: Map<Int, String> = emptyMap(),
) {
    /** Drops entries that no longer refer to anything, so saved state cannot grow without bound. */
    fun pruned(knownApps: Set<String>, widgetSlots: Set<Int>, pageCount: Int) = copy(
        hiddenApps = hiddenApps.filterTo(linkedSetOf()) { it in knownApps },
        appOverrides = appOverrides.filter { (id, value) -> id in knownApps && !value.isEmpty },
        categoryOverrides = categoryOverrides.filter { (id, value) -> id in knownApps && value in APP_CATEGORY_KEYS },
        mainPage = mainPage.coerceIn(0, maxOf(0, pageCount - 1)),
        widgetContent = widgetContent.filterKeys { it in widgetSlots },
    )
}

/** Category keys used by the categorized All apps page; "other" collects everything unmapped. */
val APP_CATEGORY_KEYS = setOf("productivity", "social", "entertainment", "photos", "travel", "games", "news",
    "utilities", "other")

/** Category keys from earlier builds, folded into the current set. */
fun normalizeCategoryKey(key: String): String = when (key) {
    "audio", "video" -> "entertainment"
    "maps" -> "travel"
    "accessibility" -> "utilities"
    else -> key
}

const val MAX_WIDGET_CONTENT_CHARS = 20_000
private const val MAX_LABEL_CHARS = 60
private const val MAX_ICON_REF_CHARS = 400

fun encodeLauncherExtras(extras: LauncherExtras, into: JSONObject): JSONObject {
    into.put("hiddenApps", JSONArray(extras.hiddenApps.sorted()))
    into.put("appOverrides", JSONObject().also { result ->
        extras.appOverrides.toSortedMap().forEach { (id, value) ->
            result.put(id, JSONObject().apply {
                value.label?.takeIf(String::isNotBlank)?.let { put("label", it) }
                value.icon?.takeIf(String::isNotBlank)?.let { put("icon", it) }
            })
        }
    })
    into.put("categoryOverrides", JSONObject(extras.categoryOverrides.toSortedMap() as Map<*, *>))
    into.put("mainPage", extras.mainPage)
    into.put("widgetContent", JSONObject().also { result ->
        extras.widgetContent.toSortedMap().forEach { (slot, value) -> result.put(slot.toString(), value) }
    })
    return into
}

/**
 * Reads schema-9 fields. Older payloads simply have none of them and yield defaults; present
 * fields are validated strictly so a damaged payload is reported instead of silently changed.
 */
fun decodeLauncherExtras(source: JSONObject): LauncherExtras {
    val hidden = source.optJSONArray("hiddenApps")?.let { array ->
        List(array.length()) { array.getString(it).also { id -> require(id.isNotBlank()) } }.toCollection(linkedSetOf())
    } ?: emptySet()
    val overrides = source.optJSONObject("appOverrides")?.let { obj ->
        obj.keys().asSequence().associateWith { id ->
            val item = obj.getJSONObject(id)
            val label = item.optString("label").takeIf { item.has("label") }
            val icon = item.optString("icon").takeIf { item.has("icon") }
            require(id.isNotBlank() && (label == null || label.length in 1..MAX_LABEL_CHARS) &&
                (icon == null || icon.length in 1..MAX_ICON_REF_CHARS)) { "Invalid app override" }
            AppOverride(label, icon)
        }
    } ?: emptyMap()
    val categories = source.optJSONObject("categoryOverrides")?.let { obj ->
        obj.keys().asSequence().associateWith { id -> normalizeCategoryKey(obj.getString(id)).also { require(it in APP_CATEGORY_KEYS) } }
    } ?: emptyMap()
    val mainPage = if (source.has("mainPage")) source.getInt("mainPage").also { require(it in 0..99) } else 0
    val content = source.optJSONObject("widgetContent")?.let { obj ->
        obj.keys().asSequence().associate { key ->
            val slot = key.toIntOrNull()?.takeIf { it >= 0 } ?: error("Invalid widget content slot")
            slot to obj.getString(key).also { require(it.length <= MAX_WIDGET_CONTENT_CHARS) }
        }
    } ?: emptyMap()
    return LauncherExtras(hidden, overrides, categories, mainPage, content)
}

fun dockSideFrom(value: String?): DockSide =
    value?.let { name -> DockSide.entries.firstOrNull { it.name == name } } ?: DockSide.RIGHT

/** Pinned shortcuts are stored in Home slots as `shortcut:<package>/<shortcutId>@<userSerial>`. */
const val SHORTCUT_PREFIX = "shortcut:"
fun isShortcutId(id: String) = id.startsWith(SHORTCUT_PREFIX)
