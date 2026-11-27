package com.jake.duolauncher

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.json.JSONObject

/** How app icons are drawn. GLYPH = uniform continuous-corner tiles built from each app's own icon. */
enum class IconStyle { ORIGINAL, GLYPH }
/** SYSTEM follows the device's own adaptive-icon mask. */
enum class IconShape { SYSTEM, ROUNDED_SQUARE, SQUIRCLE, CIRCLE }
/** LIGHT keeps app colors; DARK uses dark plates; TINTED recolors monochrome layers with the accent. */
enum class IconTone { LIGHT, DARK, TINTED }

data class ThemeSettings(
    val iconStyle: IconStyle = IconStyle.ORIGINAL,
    val iconShape: IconShape = IconShape.ROUNDED_SQUARE,
    val iconTone: IconTone = IconTone.LIGHT,
    /** Switch icons to DARK while the launcher appearance is dark (night or dark mode). */
    val darkIconsAtNight: Boolean = false,
    /** Package name of an installed third-party icon pack, or null for none. */
    val iconPack: String? = null,
    /** Color theme id from [COLOR_THEMES], or "custom" to use [customAccent]/[customGlass]. */
    val colorTheme: String = "duo",
    val customAccent: Int = 0xFF4C8DFF.toInt(),
    val customGlass: Int = 0xFF1F2A33.toInt(),
    /** 0..1 opacity applied to glass surfaces such as the dock and sheets. */
    val glassOpacity: Float = .72f,
) {
    fun sanitized() = copy(glassOpacity = glassOpacity.coerceIn(.2f, 1f),
        colorTheme = if (colorTheme == "custom" || COLOR_THEMES.any { it.id == colorTheme }) colorTheme else "duo")

    /** Effective tone after the night rule; [dark] is the resolved launcher appearance. */
    fun effectiveTone(dark: Boolean) = if (darkIconsAtNight && dark && iconTone == IconTone.LIGHT) IconTone.DARK else iconTone

    /** Key for icon caches: any change that alters rendered icon pixels must change it. */
    fun iconKey(dark: Boolean) = "$iconStyle|$iconShape|${effectiveTone(dark)}|${iconPack.orEmpty()}|${accent()}"

    fun accent(): Int = if (colorTheme == "custom") customAccent else COLOR_THEMES.first { it.id == colorTheme }.accent
    fun glass(): Int = if (colorTheme == "custom") customGlass else COLOR_THEMES.first { it.id == colorTheme }.glass
}

data class ColorTheme(val id: String, val name: String, val accent: Int, val glass: Int)

val COLOR_THEMES = listOf(
    ColorTheme("duo", "Duo", 0xFF4C8DFF.toInt(), 0xFF1F2A33.toInt()),
    ColorTheme("sand", "Sand", 0xFFE0A15A.toInt(), 0xFF3A2E24.toInt()),
    ColorTheme("forest", "Forest", 0xFF5DBB7A.toInt(), 0xFF1E2B22.toInt()),
    ColorTheme("rose", "Rose", 0xFFE56B8C.toInt(), 0xFF2F2027.toInt()),
    ColorTheme("graphite", "Graphite", 0xFFB8BEC6.toInt(), 0xFF202226.toInt()),
)

internal fun encodeThemeSettings(value: ThemeSettings): String = JSONObject()
    .put("iconStyle", value.iconStyle.name).put("iconShape", value.iconShape.name).put("iconTone", value.iconTone.name)
    .put("darkIconsAtNight", value.darkIconsAtNight).put("iconPack", value.iconPack ?: JSONObject.NULL)
    .put("colorTheme", value.colorTheme).put("customAccent", value.customAccent).put("customGlass", value.customGlass)
    .put("glassOpacity", value.glassOpacity.toDouble()).toString()

internal fun decodeThemeSettings(raw: String?): ThemeSettings = runCatching {
    if (raw.isNullOrBlank()) return ThemeSettings()
    val j = JSONObject(raw)
    val defaults = ThemeSettings()
    fun <E : Enum<E>> enumOf(values: Array<E>, key: String, fallback: E) =
        values.firstOrNull { it.name == j.optString(key) } ?: fallback
    ThemeSettings(
        iconStyle = enumOf(IconStyle.entries.toTypedArray(), "iconStyle", defaults.iconStyle),
        iconShape = enumOf(IconShape.entries.toTypedArray(), "iconShape", defaults.iconShape),
        iconTone = enumOf(IconTone.entries.toTypedArray(), "iconTone", defaults.iconTone),
        darkIconsAtNight = j.optBoolean("darkIconsAtNight", defaults.darkIconsAtNight),
        iconPack = if (j.isNull("iconPack")) null else j.optString("iconPack").takeIf { it.isNotBlank() },
        colorTheme = j.optString("colorTheme", defaults.colorTheme),
        customAccent = j.optInt("customAccent", defaults.customAccent),
        customGlass = j.optInt("customGlass", defaults.customGlass),
        glassOpacity = j.optDouble("glassOpacity", defaults.glassOpacity.toDouble()).toFloat(),
    ).sanitized()
}.getOrDefault(ThemeSettings())

/** Process-wide theme settings in SharedPreferences "theme"; shared by Home, Discover and icon rendering. */
object ThemeStore {
    private const val PREFS = "theme"
    private const val KEY = "settings"
    private val mutable = MutableStateFlow(ThemeSettings())
    private var loaded = false
    val state: StateFlow<ThemeSettings> = mutable.asStateFlow()

    fun load(context: Context): StateFlow<ThemeSettings> {
        if (!loaded) {
            loaded = true
            mutable.value = decodeThemeSettings(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null))
        }
        return state
    }

    fun update(context: Context, transform: (ThemeSettings) -> ThemeSettings) {
        load(context)
        mutable.update { transform(it).sanitized() }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, encodeThemeSettings(mutable.value)).apply()
    }
}

/**
 * The "duo" theme keeps the original palette exactly. Other themes tint the glass surfaces: dark
 * appearance uses the theme glass color, light appearance a pale wash of it.
 */
fun themedPalette(base: DuoPalette, theme: ThemeSettings): DuoPalette {
    if (theme.colorTheme == "duo") return base
    val glass = androidx.compose.ui.graphics.Color(theme.glass())
    val tinted = if (base.dark) glass else androidx.compose.ui.graphics.lerp(glass, androidx.compose.ui.graphics.Color.White, .86f)
    return base.copy(glass = tinted)
}
