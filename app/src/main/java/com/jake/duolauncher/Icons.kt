package com.jake.duolauncher

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asComposePath
import androidx.core.graphics.drawable.toBitmap
import org.xmlpull.v1.XmlPullParser
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sign
import kotlin.math.sin

const val ICON_PX = 144

/** Points of a superellipse |x/r|^n + |y/r|^n = 1 (n=5 gives a continuous-corner "squircle"). */
fun superellipsePoints(size: Float, exponent: Float = 5f, steps: Int = 96): List<Pair<Float, Float>> {
    val r = size / 2f
    return List(steps) { index ->
        val t = 2.0 * Math.PI * index / steps
        val c = cos(t); val s = sin(t)
        val x = abs(c).pow(2.0 / exponent) * sign(c)
        val y = abs(s).pow(2.0 / exponent) * sign(s)
        (r + r * x).toFloat() to (r + r * y).toFloat()
    }
}

/** The device's adaptive-icon outline at [size] pixels. */
fun systemIconMask(size: Float): Path = AdaptiveIconDrawable(android.graphics.drawable.ColorDrawable(), android.graphics.drawable.ColorDrawable())
    .apply { setBounds(0, 0, size.toInt().coerceAtLeast(1), size.toInt().coerceAtLeast(1)) }.iconMask

fun iconPath(shape: IconShape, size: Float): Path = Path().apply {
    when (shape) {
        IconShape.SYSTEM -> addPath(systemIconMask(size))
        IconShape.ROUNDED_SQUARE -> addRoundRect(0f, 0f, size, size, size * .236f, size * .236f, Path.Direction.CW)
        IconShape.CIRCLE -> addCircle(size / 2f, size / 2f, size / 2f, Path.Direction.CW)
        IconShape.SQUIRCLE -> superellipsePoints(size).forEachIndexed { i, (x, y) -> if (i == 0) moveTo(x, y) else lineTo(x, y) }
            .also { close() }
    }
}

/** Compose clip matching the rendered icon outline, used wherever an icon bitmap is drawn. */
fun composeIconShape(shape: IconShape): Shape = when (shape) {
    IconShape.SYSTEM -> GenericShape { size, _ ->
        val mask = systemIconMask(size.width)
        if (size.height != size.width) mask.transform(android.graphics.Matrix().apply { setScale(1f, size.height / size.width) })
        addPath(mask.asComposePath())
    }
    IconShape.ROUNDED_SQUARE -> RoundedCornerShape(24)
    IconShape.CIRCLE -> CircleShape
    IconShape.SQUIRCLE -> GenericShape { size, _ ->
        superellipsePoints(size.width).forEachIndexed { i, (x, y) -> if (i == 0) moveTo(x, y * size.height / size.width) else lineTo(x, y * size.height / size.width) }
        close()
    }
}

val LocalIconShape = staticCompositionLocalOf<Shape> { RoundedCornerShape(24) }
fun Modifier.iconClip(shape: Shape) = clip(shape)

/** Parsed `appfilter.xml` of an ADW/Nova-style icon pack. */
data class AppFilter(
    val icons: Map<String, String>,
    val backs: List<String> = emptyList(),
    val mask: String? = null,
    val upon: String? = null,
    val scale: Float = 1f,
)

/** Builds an [AppFilter] from (tag, attributes) events, so the rules can be tested without Android's parser. */
fun parseAppFilter(events: Sequence<Pair<String, Map<String, String>>>): AppFilter {
    val icons = linkedMapOf<String, String>()
    val backs = mutableListOf<String>()
    var mask: String? = null; var upon: String? = null; var scale = 1f
    events.forEach { (tag, attrs) ->
        when (tag) {
            "item" -> {
                val component = attrs["component"]?.let(::componentFromFilter)
                val drawable = attrs["drawable"]?.takeIf(String::isNotBlank)
                if (component != null && drawable != null) icons.putIfAbsent(component, drawable)
            }
            "iconback" -> attrs.entries.sortedBy { it.key }.forEach { if (it.key.startsWith("img")) backs += it.value }
            "iconmask" -> mask = attrs["img1"] ?: mask
            "iconupon" -> upon = attrs["img1"] ?: upon
            "scale" -> scale = attrs["factor"]?.toFloatOrNull()?.takeIf { it in .1f..2f } ?: scale
        }
    }
    return AppFilter(icons, backs, mask, upon, scale)
}

/** `ComponentInfo{com.app/com.app.Main}` → `com.app/com.app.Main` (relative class names expanded). */
fun componentFromFilter(value: String): String? {
    val inner = value.trim().removePrefix("ComponentInfo{").removeSuffix("}")
    val slash = inner.indexOf('/')
    if (slash <= 0 || slash == inner.lastIndex) return null
    val pkg = inner.substring(0, slash); val cls = inner.substring(slash + 1)
    return "$pkg/${if (cls.startsWith(".")) pkg + cls else cls}"
}

class IconPack(val packageName: String, val label: String, private val resources: Resources, val filter: AppFilter) {
    private fun drawable(name: String): Drawable? = runCatching {
        val id = resources.getIdentifier(name, "drawable", packageName)
        if (id == 0) null else resources.getDrawable(id, null)
    }.getOrNull()

    fun iconFor(component: ComponentName): Drawable? =
        filter.icons[component.flattenToString()]?.let(::drawable)
            ?: filter.icons["${component.packageName}/${component.className}"]?.let(::drawable)

    fun drawableNamed(name: String): Drawable? = drawable(name)
    val drawableNames: List<String> get() = filter.icons.values.distinct().sorted()

    /** Fallback for apps the pack does not cover: iconback + scaled app icon + iconmask + iconupon. */
    fun decorate(source: Bitmap): Bitmap? {
        if (filter.backs.isEmpty() && filter.mask == null && filter.upon == null) return null
        val out = Bitmap.createBitmap(ICON_PX, ICON_PX, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        filter.backs.firstOrNull()?.let(::drawable)?.apply { setBounds(0, 0, ICON_PX, ICON_PX); draw(canvas) }
        val inset = (ICON_PX * (1f - filter.scale) / 2f).toInt()
        canvas.drawBitmap(source, null, android.graphics.Rect(inset, inset, ICON_PX - inset, ICON_PX - inset), Paint(Paint.FILTER_BITMAP_FLAG))
        filter.mask?.let(::drawable)?.let { mask ->
            val maskBitmap = mask.toBitmap(ICON_PX, ICON_PX)
            canvas.drawBitmap(maskBitmap, 0f, 0f, Paint().apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT) })
        }
        filter.upon?.let(::drawable)?.apply { setBounds(0, 0, ICON_PX, ICON_PX); draw(canvas) }
        return out
    }
}

data class InstalledIconPack(val packageName: String, val label: String)

private val ICON_PACK_ACTIONS = listOf("org.adw.launcher.THEMES", "com.novalauncher.THEME", "com.anddoes.launcher.THEME",
    "com.teslacoilsw.launcher.THEME", "com.gau.go.launcherex.theme")

fun installedIconPacks(context: Context): List<InstalledIconPack> {
    val pm = context.packageManager
    return ICON_PACK_ACTIONS.flatMap { action ->
        runCatching { pm.queryIntentActivities(Intent(action), PackageManager.GET_META_DATA) }.getOrDefault(emptyList())
    }.map { it.activityInfo.packageName }.distinct().mapNotNull { pkg ->
        runCatching { InstalledIconPack(pkg, pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()) }.getOrNull()
    }.sortedBy { it.label.lowercase() }
}

object IconPacks {
    private var cached: IconPack? = null

    /** Forgets the parsed pack so a reinstalled or updated pack is read again. */
    fun clear() { cached = null }

    /** Loads (and caches) a pack's appfilter from its res/xml or assets. Returns null if it cannot be read. */
    fun load(context: Context, packageName: String?): IconPack? {
        if (packageName == null) return null
        cached?.takeIf { it.packageName == packageName }?.let { return it }
        return runCatching {
            val packContext = context.createPackageContext(packageName, 0)
            val res = packContext.resources
            val events = mutableListOf<Pair<String, Map<String, String>>>()
            val xmlId = res.getIdentifier("appfilter", "xml", packageName)
            val parser: XmlPullParser = if (xmlId != 0) res.getXml(xmlId) else {
                android.util.Xml.newPullParser().apply { setInput(packContext.assets.open("appfilter.xml"), "UTF-8") }
            }
            while (parser.next() != XmlPullParser.END_DOCUMENT) {
                if (parser.eventType == XmlPullParser.START_TAG)
                    events += parser.name to (0 until parser.attributeCount).associate { parser.getAttributeName(it) to parser.getAttributeValue(it) }
            }
            val label = context.packageManager.getApplicationLabel(context.packageManager.getApplicationInfo(packageName, 0)).toString()
            IconPack(packageName, label, res, parseAppFilter(events.asSequence()))
        }.getOrNull()?.also { cached = it }
    }
}

/**
 * Renders a launcher icon from an app's own artwork. Nothing is replaced by third-party art unless the
 * user picked an icon pack. GLYPH draws every icon as a uniform full tile; legacy icons sit on a plate.
 */
object IconRenderer {
    private val darkPlate = 0xFF1C1C1E.toInt()

    fun render(source: Drawable, theme: ThemeSettings, dark: Boolean): Bitmap {
        val tone = theme.effectiveTone(dark)
        val out = Bitmap.createBitmap(ICON_PX, ICON_PX, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val size = ICON_PX.toFloat()
        val adaptive = source as? AdaptiveIconDrawable
        // Legacy icons in the original style keep their own silhouette; everything else gets the outline.
        val keepSilhouette = adaptive == null && tone == IconTone.LIGHT && theme.iconStyle == IconStyle.ORIGINAL
        if (keepSilhouette) { canvas.drawBitmap(source.toBitmap(ICON_PX, ICON_PX), 0f, 0f, null); return out }
        canvas.clipPath(iconPath(theme.iconShape, size))
        // ORIGINAL keeps Duo's historic framing (whole 108dp layer in the tile); GLYPH zooms to the 72dp safe zone.
        val extra = if (theme.iconStyle == IconStyle.GLYPH) ICON_PX / 6 else 0
        when {
            tone == IconTone.TINTED -> {
                canvas.drawColor(darkPlate)
                val glyph = (if (android.os.Build.VERSION.SDK_INT >= 33) adaptive?.monochrome else null) ?: adaptive?.foreground
                if (glyph != null) drawLayer(canvas, glyph, extra, tint = theme.accent())
                else drawLegacy(canvas, source, monochromeTint = theme.accent())
            }
            tone == IconTone.DARK -> {
                canvas.drawColor(darkPlate)
                if (adaptive?.foreground != null) drawLayer(canvas, adaptive.foreground, extra) else drawLegacy(canvas, source)
            }
            adaptive != null -> {
                adaptive.background?.let { drawLayer(canvas, it, extra) }
                adaptive.foreground?.let { drawLayer(canvas, it, extra) }
            }
            else -> { canvas.drawColor(android.graphics.Color.WHITE); drawLegacy(canvas, source) }
        }
        return out
    }

    /** Renders an icon pack's (or user-chosen) drawable inside the chosen outline, without plates. */
    fun renderPacked(drawable: Drawable, theme: ThemeSettings): Bitmap {
        val out = Bitmap.createBitmap(ICON_PX, ICON_PX, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        if (theme.iconStyle == IconStyle.GLYPH) canvas.clipPath(iconPath(theme.iconShape, ICON_PX.toFloat()))
        drawable.setBounds(0, 0, ICON_PX, ICON_PX); drawable.draw(canvas)
        return out
    }

    private fun drawLayer(canvas: Canvas, layer: Drawable, extra: Int, tint: Int? = null) {
        layer.setBounds(-extra, -extra, ICON_PX + extra, ICON_PX + extra)
        if (tint != null) layer.mutate().setTint(tint)
        layer.draw(canvas)
        if (tint != null) layer.setTintList(null)
    }

    private fun drawLegacy(canvas: Canvas, source: Drawable, monochromeTint: Int? = null) {
        val inset = (ICON_PX * .16f).toInt()
        val bitmap = source.toBitmap(ICON_PX - 2 * inset, ICON_PX - 2 * inset)
        val paint = Paint(Paint.FILTER_BITMAP_FLAG)
        if (monochromeTint != null) {
            val r = android.graphics.Color.red(monochromeTint) / 255f
            val g = android.graphics.Color.green(monochromeTint) / 255f
            val b = android.graphics.Color.blue(monochromeTint) / 255f
            paint.colorFilter = ColorMatrixColorFilter(ColorMatrix(floatArrayOf(
                .3f * r, .59f * r, .11f * r, 0f, 0f,
                .3f * g, .59f * g, .11f * g, 0f, 0f,
                .3f * b, .59f * b, .11f * b, 0f, 0f,
                0f, 0f, 0f, 1f, 0f)))
        }
        canvas.drawBitmap(bitmap, inset.toFloat(), inset.toFloat(), paint)
    }
}
