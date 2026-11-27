package com.jake.duolauncher

import android.content.Context
import android.content.pm.ApplicationInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Maps an app to a library category key in [APP_CATEGORY_KEYS]. The category the app declares wins;
 * apps that declare none (most preinstalled ones) are placed by well-known words in their package name.
 */
fun categoryKey(androidCategory: Int, packageName: String = ""): String = when (androidCategory) {
    ApplicationInfo.CATEGORY_GAME -> "games"
    ApplicationInfo.CATEGORY_AUDIO, ApplicationInfo.CATEGORY_VIDEO -> "entertainment"
    ApplicationInfo.CATEGORY_IMAGE -> "photos"
    ApplicationInfo.CATEGORY_SOCIAL -> "social"
    ApplicationInfo.CATEGORY_NEWS -> "news"
    ApplicationInfo.CATEGORY_MAPS -> "travel"
    ApplicationInfo.CATEGORY_PRODUCTIVITY -> "productivity"
    ApplicationInfo.CATEGORY_ACCESSIBILITY -> "utilities"
    else -> packageCategory(packageName)
}

private val KNOWN_PACKAGES = mapOf("com.google.android.gm" to "productivity", "com.android.vending" to "other")

private val PACKAGE_HINTS = listOf(
    "photos" to listOf("camera", "photo", "gallery", "snapseed", "lightroom"),
    "entertainment" to listOf("youtube", "music", "netflix", "spotify", "video", "podcast", "twitch"),
    "social" to listOf("messag", "mms", "whatsapp", "telegram", "kakao", "instagram", "facebook", "twitter", "discord", "line.android"),
    "travel" to listOf("maps", "uber", "airbnb", "booking", "transit", "waze"),
    "productivity" to listOf("calendar", "email", "apps.docs", "drive", "office", "notes", "keep", "chrome", "browser", "slack", "teams"),
    "news" to listOf("news", "magazine"),
    "utilities" to listOf("clock", "contacts", "dialer", "settings", "files", "documentsui", "calculator",
        "quicksearchbox", "weather", "recorder", "flashlight"),
)

internal fun packageCategory(packageName: String): String {
    val name = packageName.lowercase()
    KNOWN_PACKAGES[name]?.let { return it }
    return PACKAGE_HINTS.firstOrNull { (_, words) -> words.any { it in name } }?.first ?: "other"
}

val CATEGORY_TITLES = linkedMapOf(
    "productivity" to "Productivity", "social" to "Social", "entertainment" to "Entertainment",
    "photos" to "Photo & Creativity", "travel" to "Travel", "games" to "Games", "news" to "News & Reading",
    "utilities" to "Utilities", "other" to "Other",
)

data class LibraryGroup<T>(val key: String, val title: String, val apps: List<T>)

const val RECENTLY_ADDED_DAYS = 14L

/**
 * Groups visible apps into Suggestions (recent launches), Recently added (installed in the last two
 * weeks) and one group per category, in a fixed order. Apps keep their label order within a group.
 */
fun groupLibrary(apps: List<AppEntry>, overrides: Map<String, String>, recentIds: List<String>, now: Long) =
    groupLibrary(apps, AppEntry::id, AppEntry::category, AppEntry::installedAt, overrides, recentIds, now)

fun <T> groupLibrary(apps: List<T>, id: (T) -> String, category: (T) -> String, installedAt: (T) -> Long,
    overrides: Map<String, String>, recentIds: List<String>, now: Long): List<LibraryGroup<T>> {
    val byId = apps.associateBy(id)
    val suggestions = recentIds.mapNotNull(byId::get).distinct().take(8)
    val cutoff = now - RECENTLY_ADDED_DAYS * 24 * 60 * 60 * 1000
    val recent = apps.filter { installedAt(it) in cutoff..now }.sortedByDescending(installedAt).take(8)
    val categories = apps.groupBy { normalizeCategoryKey(overrides[id(it)] ?: category(it)) }
    return buildList {
        if (suggestions.isNotEmpty()) add(LibraryGroup("suggestions", "Suggestions", suggestions))
        if (recent.isNotEmpty()) add(LibraryGroup("recent", "Recently Added", recent))
        CATEGORY_TITLES.forEach { (key, title) -> categories[key]?.takeIf { it.isNotEmpty() }?.let { add(LibraryGroup(key, title, it)) } }
    }
}

/** Most recently launched app ids (newest first), for the Suggestions group. Device-local only. */
object RecentApps {
    private const val PREFS = "library"
    private val recents = MutableStateFlow<List<String>>(emptyList())
    private val categoriesMode = MutableStateFlow(true)
    private var loaded = false

    private fun load(context: Context) {
        if (loaded) return
        loaded = true
        val prefs = context.getSharedPreferences(PREFS, 0)
        recents.value = prefs.getString("recent", "").orEmpty().split('\n').filter(String::isNotBlank)
        categoriesMode.value = prefs.getBoolean("categories", true)
    }

    fun recents(context: Context): StateFlow<List<String>> { load(context); return recents.asStateFlow() }
    fun categoriesMode(context: Context): StateFlow<Boolean> { load(context); return categoriesMode.asStateFlow() }

    fun setCategoriesMode(context: Context, value: Boolean) {
        load(context); categoriesMode.value = value
        context.getSharedPreferences(PREFS, 0).edit().putBoolean("categories", value).apply()
    }

    fun launched(context: Context, id: String) {
        load(context)
        recents.value = (listOf(id) + recents.value.filterNot(id::equals)).take(12)
        context.getSharedPreferences(PREFS, 0).edit().putString("recent", recents.value.joinToString("\n")).apply()
    }
}
