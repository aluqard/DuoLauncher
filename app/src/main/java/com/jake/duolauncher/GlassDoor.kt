package com.jake.duolauncher

import android.app.Activity
import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.SystemClock
import androidx.annotation.RequiresApi
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowInfoTracker
import kotlinx.coroutines.delay
import kotlin.math.abs

/*
 * Glass door: on a book-style foldable, the half of Home that swings (the left one by default) is drawn as a
 * pane of frosted glass hinged at the crease. While the phone opens, the pane is blurred and dimmed the farther
 * it is from the hinge, and the picture settles into focus as the hinge reaches flat. Near closed, the cover
 * screen carries it on: it lights fully frosted where the inner screen hands over and clears as the phone
 * shuts (and frosts in on the way open). Home draws its own wallpaper and pages, so the frost runs over the
 * live screen: no screenshots and no accessibility service. It plays only on Home.
 */

/** Hinge angle treated as fully open: real hinges rest a few degrees short of 180°. */
internal const val GLASS_DOOR_FLAT = 172f

/** At or below this hinge angle Home is fully frosted (the inner screen lights somewhere below it). */
internal const val GLASS_DOOR_FULL = 90f

/** How frosted Home is at [hinge] degrees: 1 at [GLASS_DOOR_FULL] or less, easing to 0 at [GLASS_DOOR_FLAT]. */
internal fun glassDoorAmount(hinge: Float?): Float {
    if (hinge == null || hinge.isNaN()) return 0f
    val t = ((GLASS_DOOR_FLAT - hinge) / (GLASS_DOOR_FLAT - GLASS_DOOR_FULL)).coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}

/** A shut hinge idles a few degrees above 0°; below this the cover screen counts as closed. */
internal const val GLASS_DOOR_CLOSED = 8f

/** Hinge angle where the inner and cover screens hand over, until this phone's own is learned. */
internal const val GLASS_DOOR_SWAP_DEFAULT = 30f

/** Cover-screen frost at [hinge]: clear when shut, full at [swap], where the inner screen takes over. */
internal fun coverDoorAmount(hinge: Float?, swap: Float): Float {
    if (hinge == null || hinge.isNaN()) return 0f
    val top = swap.coerceIn(GLASS_DOOR_CLOSED + 4f, 150f)
    val t = ((hinge - GLASS_DOOR_CLOSED) / (top - GLASS_DOOR_CLOSED)).coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}

/** The hand-over angle from the last reading on one screen and the first on the other, if it is believable. */
internal fun handoverAngle(before: Float, after: Float): Float? = ((before + after) / 2f).takeIf { it in 10f..150f }

/** Which half of the inner screen frosts: the half that swings (left by default), the other one, or both. */
enum class GlassDoorSide(val label: String, val sign: Float) { LEFT("Left", -1f), BOTH("Both", 0f), RIGHT("Right", 1f) }

/**
 * Learns where this phone switches between its inner and cover screens: the last hinge reading on one and the
 * first on the other, moments apart. Kept across restarts; the readings are kept for the process, so they
 * survive Home being recreated on the other screen.
 */
internal object GlassDoorSwap {
    private const val PREFS = "glass_door"
    private const val KEY = "swapAngle"
    private var lastInner: Pair<Float, Long>? = null
    private var lastCover: Pair<Float, Long>? = null

    fun angle(context: android.content.Context): Float =
        context.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE).getFloat(KEY, GLASS_DOOR_SWAP_DEFAULT)

    fun observe(context: android.content.Context, angle: Float, inner: Boolean, at: Long) {
        val other = if (inner) lastCover else lastInner
        if (other != null && at - other.second < 2500L) {
            handoverAngle(other.first, angle)?.let {
                context.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE).edit().putFloat(KEY, it).apply()
            }
            if (inner) lastCover = null else lastInner = null
        }
        if (inner) lastInner = angle to at else lastCover = angle to at
    }
}

/** Whether this phone has a hinge sensor, which the glass door needs. */
internal fun hasHingeSensor(context: android.content.Context): Boolean =
    context.getSystemService(SensorManager::class.java)?.getDefaultSensor(Sensor.TYPE_HINGE_ANGLE) != null

/** A hinge that reports only a few stops (a Galaxy Z Fold reads 0°, 90° and 180°) is eased in time instead. */
internal fun coarseHinge(resolutionDegrees: Float): Boolean = resolutionDegrees >= 30f

/** Where the crease runs in the window: [splitsY] means a horizontal crease, [at] is its position in px. */
internal data class GlassDoorLine(val at: Float, val splitsY: Boolean)

/** The crease from the window's fold, or the middle of the window's long side when the fold is unknown. */
internal fun glassDoorLine(fold: FoldingFeature?, width: Float, height: Float): GlassDoorLine {
    if (fold != null) {
        val bounds = fold.bounds
        return if (fold.orientation == FoldingFeature.Orientation.HORIZONTAL) GlassDoorLine(bounds.exactCenterY(), true)
        else GlassDoorLine(bounds.exactCenterX(), false)
    }
    return if (height > width) GlassDoorLine(height / 2f, true) else GlassDoorLine(width / 2f, false)
}

/**
 * What drives the effect each frame: how frosted (0..1), where the crease is, whether this is the cover screen,
 * which half frosts on the inner screen, and the user's strength.
 */
@Stable
internal class GlassDoorState(amount: State<Float>, fold: State<FoldingFeature?>, cover: State<Boolean>,
    val side: GlassDoorSide, val strength: Float) {
    val amount by amount
    val fold by fold
    val cover by cover
}

/**
 * Follows the hinge while Home is resumed and [enabled]; null on phones without a hinge. On the inner screen
 * (a window with a fold) the frost clears toward flat; on the cover screen (no fold) it clears toward shut.
 * A pause partway (a half-open stand) clears the frost after a moment; moving the hinge again brings it back.
 */
@Composable
internal fun rememberGlassDoor(enabled: Boolean, strength: Float, side: GlassDoorSide): GlassDoorState? {
    val context = LocalContext.current
    val activity = context as? Activity
    val fold = remember { mutableStateOf<FoldingFeature?>(null) }
    val cover = remember { mutableStateOf(false) }
    var hasFold by remember { mutableStateOf(false) }
    if (activity != null) LaunchedEffect(activity) {
        WindowInfoTracker.getOrCreate(activity).windowLayoutInfo(activity).collect { info ->
            val feature = info.displayFeatures.filterIsInstance<FoldingFeature>().firstOrNull()
            fold.value = feature; hasFold = feature != null; cover.value = feature == null
        }
    }
    val sensors = remember { context.getSystemService(SensorManager::class.java) }
    val hinge = remember { sensors?.getDefaultSensor(Sensor.TYPE_HINGE_ANGLE) }
    val coarse = hinge != null && coarseHinge(hinge.resolution)
    var angle by remember { mutableStateOf<Float?>(null) }
    val active = enabled && hinge != null
    LifecycleResumeEffect(active) {
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) { angle = event.values[0] }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        if (active) sensors?.registerListener(listener, hinge, SensorManager.SENSOR_DELAY_GAME)
        onPauseOrDispose { sensors?.unregisterListener(listener); angle = null }
    }
    // Only real hinge movement keeps the frost up; sensor jitter of a degree or so does not.
    var anchor by remember { mutableStateOf<Float?>(null) }
    var lastMove by remember { mutableLongStateOf(0L) }
    LaunchedEffect(angle) {
        val now = angle ?: run { anchor = null; return@LaunchedEffect }
        GlassDoorSwap.observe(context, now, inner = hasFold, at = SystemClock.uptimeMillis())
        if (anchor.let { it == null || abs(now - it) >= 2f }) { anchor = now; lastMove = SystemClock.uptimeMillis() }
    }
    val target = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(lastMove, anchor, hasFold) {
        target.floatValue = if (hasFold) glassDoorAmount(anchor) else coverDoorAmount(anchor, GlassDoorSwap.angle(context))
        if (target.floatValue > 0f) { delay(900); target.floatValue = 0f }
    }
    val shown = animateFloatAsState(if (active) target.floatValue else 0f,
        if (coarse) tween(520, easing = FastOutSlowInEasing) else spring(stiffness = 900f), label = "glass door")
    if (!active) return null
    return remember(strength, side) { GlassDoorState(shown, fold, cover, side, strength) }
}

/** Draws this subtree (Home's root) through the glass door while [door] is frosted; untouched when clear. */
@Composable
internal fun Modifier.glassDoor(door: GlassDoorState?): Modifier {
    if (door == null) return this
    val density = LocalDensity.current
    val maxBlur = with(density) { 26.dp.toPx() }
    val shader = remember { if (Build.VERSION.SDK_INT >= 33) GlassDoorShader.create() else null }
    return graphicsLayer {
        val amount = door.amount
        if (amount < .01f || size.width <= 1f || size.height <= 1f) { renderEffect = null; return@graphicsLayer }
        // The cover screen is one pane hinged at the spine, its left edge, frosting toward the far edge.
        val line = if (door.cover) GlassDoorLine(0f, false) else glassDoorLine(door.fold, size.width, size.height)
        val side = if (door.cover) GlassDoorSide.RIGHT.sign else door.side.sign
        val strength = door.strength
        renderEffect = if (shader != null && Build.VERSION.SDK_INT >= 33)
            GlassDoorShader.effect(shader, size.width, size.height, line, side, amount, maxBlur * strength, strength)
        else glassDoorFallback(amount * strength, maxBlur)
    }
}

/** Android 12: no runtime shaders, so the whole screen blurs and dims evenly with the fold. */
private fun glassDoorFallback(amount: Float, maxBlur: Float): androidx.compose.ui.graphics.RenderEffect {
    val radius = (maxBlur * .55f * amount).coerceAtLeast(.5f)
    val dim = 1f - (.35f * amount).coerceIn(0f, .7f)
    val matrix = android.graphics.ColorMatrix().apply { setScale(dim, dim, dim, 1f) }
    return RenderEffect.createColorFilterEffect(android.graphics.ColorMatrixColorFilter(matrix),
        RenderEffect.createBlurEffect(radius, radius, android.graphics.Shader.TileMode.CLAMP)).asComposeRenderEffect()
}

@RequiresApi(33)
private object GlassDoorShader {
    // A half is a glass pane hinged at the crease; `side` picks which (-1 left/top, +1 right/bottom, 0 both). Per pixel: how far along its pane it sits (0 at the
    // crease, 1 at the outer edge) times how far the door is open sets the frost. The far side of the pane
    // recedes a little (content is gathered toward the crease and the middle), the picture is softened by a
    // rotated two-ring sample that reads as glass grain, and light is lost with the frost.
    private const val SOURCE = """
        uniform shader content;
        uniform float2 size;
        uniform float crease;
        uniform float splitsY;
        uniform float side;
        uniform float amount;
        uniform float blur;
        uniform float dim;
        uniform float recede;

        half4 main(float2 c) {
            float2 q = mix(c, c.yx, splitsY);
            float2 s = mix(size, size.yx, splitsY);
            float span = mix(s.x - crease, crease, step(q.x, crease));
            float along = clamp(abs(q.x - crease) / max(span, 1.0), 0.0, 1.0);
            float onFirst = step(q.x, crease);
            float frosted = mix(1.0, mix(onFirst, 1.0 - onFirst, step(0.0, side)), abs(side));
            float k = amount * along * frosted;
            float2 src = float2(crease + (q.x - crease) * (1.0 + recede * k),
                                s.y * 0.5 + (q.y - s.y * 0.5) * (1.0 + recede * 0.6 * k));
            src = clamp(src, float2(0.5), s - float2(0.5));
            float2 at = mix(src, src.yx, splitsY);
            float r = blur * pow(k, 1.3);
            half3 col = content.eval(at).rgb;
            if (r > 0.75) {
                float turn = fract(sin(dot(c, float2(91.7, 47.3))) * 24634.63) * 6.2831853;
                half3 sum = col;
                for (int i = 0; i < 12; i++) {
                    float fi = float(i);
                    float ring = mix(0.45, 1.0, step(5.5, fi));
                    float a = turn + fi * 1.0471976 + ring * 0.5236;
                    float2 off = float2(cos(a), sin(a)) * r * ring;
                    sum += content.eval(clamp(at + off, float2(0.5), size - float2(0.5))).rgb;
                }
                col = sum / 13.0;
            }
            return half4(col * half(1.0 - dim * k), 1.0);
        }
    """

    fun create(): RuntimeShader? = runCatching { RuntimeShader(SOURCE) }.getOrNull()

    fun effect(shader: RuntimeShader, width: Float, height: Float, line: GlassDoorLine, side: Float, amount: Float,
        blur: Float, strength: Float): androidx.compose.ui.graphics.RenderEffect {
        shader.setFloatUniform("size", width, height)
        shader.setFloatUniform("crease", line.at)
        shader.setFloatUniform("splitsY", if (line.splitsY) 1f else 0f)
        shader.setFloatUniform("side", side)
        shader.setFloatUniform("amount", amount.coerceIn(0f, 1f))
        shader.setFloatUniform("blur", blur)
        shader.setFloatUniform("dim", (.45f * strength).coerceIn(0f, .8f))
        shader.setFloatUniform("recede", (.10f * strength).coerceIn(0f, .2f))
        return RenderEffect.createRuntimeShaderEffect(shader, "content").asComposeRenderEffect()
    }
}
