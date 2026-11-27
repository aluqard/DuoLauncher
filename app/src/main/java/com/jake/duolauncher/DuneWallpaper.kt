package com.jake.duolauncher

import android.service.wallpaper.WallpaperService
import android.view.SurfaceHolder
import android.content.SharedPreferences
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection

@Composable
internal fun DuneWallpaper(modifier: Modifier = Modifier.fillMaxSize(), live: Boolean = false) {
    val palette = LocalDuoPalette.current
    val context = LocalContext.current.applicationContext
    val revision = LauncherBackgroundCache.revision.intValue
    val initial = remember(revision) { LauncherBackgroundCache.bitmap?.takeUnless { it.isRecycled } }
    val photo = produceState(initialValue = initial, key1 = context, key2 = revision) {
        value = withContext(Dispatchers.IO) { loadLauncherBackground(context) }
    }.value
    val settings by WallpaperStore.load(context).collectAsState()
    if (!live) { Canvas(modifier) { drawLauncherBackground(photo?.asImageBitmap(), palette.dark, settings.scene) }; return }
    // Home's own background: the folded screen may show its own photo, and the image can drift with tilt.
    val folded = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp < 650
    val coverRevision = CoverBackground.revision.intValue
    val cover = if (folded && settings.coverPhoto) produceState(CoverBackground.peek(), coverRevision) {
        value = withContext(Dispatchers.IO) { CoverBackground.load(context) }
    }.value else null
    val shown = cover ?: photo
    val tilt = if (settings.motion) rememberTilt() else null
    val shift = with(androidx.compose.ui.platform.LocalDensity.current) { 16.dp.toPx() }
    Canvas(modifier.graphicsLayer {
        val t = tilt?.value ?: return@graphicsLayer
        // Enlarge just enough that the shifted image never shows an edge.
        val zoom = 1f + 2f * shift / size.minDimension.coerceAtLeast(1f)
        scaleX = zoom; scaleY = zoom
        translationX = t.x * shift; translationY = -t.y * shift
    }) { drawLauncherBackground(shown?.asImageBitmap(), palette.dark, settings.scene) }
}

internal fun DrawScope.drawLauncherBackground(photo: ImageBitmap?, dark: Boolean = false,
    scene: BuiltInScene = WallpaperStore.state.value.scene) {
    if (photo == null || photo.width <= 0 || photo.height <= 0) {
        when (scene) {
            BuiltInScene.NIGHT -> drawStarryNight()
            BuiltInScene.DUNES -> drawDunes(dark)
        }
        return
    }
    val destinationWidth = size.width.toInt().coerceAtLeast(1)
    val destinationHeight = size.height.toInt().coerceAtLeast(1)
    val sourceAspect = photo.width.toFloat() / photo.height
    val destinationAspect = destinationWidth.toFloat() / destinationHeight
    val sourceWidth: Int
    val sourceHeight: Int
    if (sourceAspect > destinationAspect) {
        sourceHeight = photo.height
        sourceWidth = (sourceHeight * destinationAspect).toInt().coerceIn(1, photo.width)
    } else {
        sourceWidth = photo.width
        sourceHeight = (sourceWidth / destinationAspect).toInt().coerceIn(1, photo.height)
    }
    drawImage(
        image = photo,
        srcOffset = IntOffset((photo.width - sourceWidth) / 2, (photo.height - sourceHeight) / 2),
        srcSize = IntSize(sourceWidth, sourceHeight),
        dstSize = IntSize(destinationWidth, destinationHeight),
    )
}

/** Stars as x and y (fractions of the screen), size and brightness; fixed, so the sky is the same every time. */
private val nightStars: List<FloatArray> by lazy {
    val random = java.util.Random(0x5EED_2026)
    List(460) {
        // Denser overhead, thinning toward the horizon glow.
        val y = random.nextFloat().let { it * it } * .74f
        floatArrayOf(random.nextFloat(), y, .45f + random.nextFloat().let { it * it * it } * 1.7f, .3f + random.nextFloat() * .7f)
    }
}

/** A clear night over the dunes: deep sky full of stars, a last warm glow on the horizon, dark dune ridges. */
internal fun DrawScope.drawStarryNight() {
    val w = size.width; val h = size.height
    drawRect(Brush.verticalGradient(0f to Color(0xFF03050B), .36f to Color(0xFF09101F), .56f to Color(0xFF191A2A),
        .68f to Color(0xFF3E2A2D), .78f to Color(0xFF6A3C2B), 1f to Color(0xFF6A3C2B)))
    drawRect(Brush.radialGradient(listOf(Color(0x40C77A42), Color.Transparent),
        center = androidx.compose.ui.geometry.Offset(w * .32f, h * .76f), radius = maxOf(w, h) * .55f))
    val unit = size.minDimension / 900f
    nightStars.forEach { (x, y, radius, brightness) ->
        val nearHorizon = (y / .74f).coerceIn(0f, 1f)
        val center = androidx.compose.ui.geometry.Offset(x * w, y * h)
        val alpha = brightness * (1f - .7f * nearHorizon)
        if (radius > 1.7f) {
            // A soft glow that fades out, not a flat disc.
            val glow = radius * unit * 3.2f
            drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = alpha * .3f), Color.Transparent), center, glow), glow, center)
        }
        drawCircle(Color.White.copy(alpha = alpha), radius * unit, center)
    }
    fun ridge(y: Float, crest: Float, shift: Float, color: Color) {
        val path = Path().apply {
            moveTo(0f, h * y)
            cubicTo(w * (.22f + shift), h * (y - crest), w * (.58f + shift), h * (y + crest * .55f), w, h * (y - crest * .45f))
            lineTo(w, h); lineTo(0f, h); close()
        }
        drawPath(path, color)
        val rim = Path().apply {
            moveTo(0f, h * y)
            cubicTo(w * (.22f + shift), h * (y - crest), w * (.58f + shift), h * (y + crest * .55f), w, h * (y - crest * .45f))
        }
        drawPath(rim, Color(0x2EE3A271), style = androidx.compose.ui.graphics.drawscope.Stroke(1.6f * unit))
    }
    ridge(.70f, .09f, .06f, Color(0xFF1E1519))
    ridge(.79f, .12f, -.04f, Color(0xFF130E12))
    ridge(.89f, .08f, .10f, Color(0xFF09080B))
}

internal fun DrawScope.drawDunes(dark: Boolean = false) {
        val w = size.width; val h = size.height
        drawRect(Brush.verticalGradient(if (dark) listOf(Color(0xFF132832), Color(0xFF263E49), Color(0xFF463F35))
            else listOf(Color(0xFF41687E), Color(0xFF94ADB5), Color(0xFFD8CEB6))))
        fun dune(y: Float, crest: Float, color: Color) {
            val path = Path().apply {
                moveTo(0f, h * y)
                cubicTo(w * .3f, h * (y - crest), w * .6f, h * (y + crest), w, h * (y - crest * .35f))
                lineTo(w, h); lineTo(0f, h); close()
            }
            drawPath(path, color)
        }
        dune(.57f, .17f, if (dark) Color(0xFF5B5040) else Color(0xFFC9B38E))
        dune(.72f, .12f, if (dark) Color(0xFF453D32) else Color(0xFFA49373))
        dune(.85f, .19f, if (dark) Color(0xFF302C26) else Color(0xFF84775F))
        for (n in 0..28) {
            val y = h * (.84f + n * .011f)
            val path = Path().apply {
                moveTo(0f, y)
                cubicTo(w * .35f, y - h * .17f, w * .65f, y + h * .05f, w, y - h * .06f)
            }
            drawPath(path, Color.White.copy(alpha = .045f), style = androidx.compose.ui.graphics.drawscope.Stroke(1.3f))
        }
}

/** A static scene rendered on surface changes: no animation loop or background polling. */
class DuneWallpaperService : WallpaperService() {
    override fun onCreateEngine(): Engine = DuneEngine()

    inner class DuneEngine : Engine(), SharedPreferences.OnSharedPreferenceChangeListener {
        private val painter = CanvasDrawScope()
        private val appearance = AppearanceStore(this@DuneWallpaperService)
        private val appearancePrefs = getSharedPreferences("appearance", MODE_PRIVATE)
        private val backgroundPrefs = launcherBackgroundPreferences(this@DuneWallpaperService)
        private val wallpaperPrefs = getSharedPreferences("wallpaper", MODE_PRIVATE)
        private val loader = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        private var photo: ImageBitmap? = cachedLauncherBackground(this@DuneWallpaperService)?.asImageBitmap()
        private var photoLoad = 0
        private var photoLoading = false
        private var photoFailed = false
        private var visible = false
        private var timeReceiverRegistered = false
        private val timeReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (visible) { appearance.reloadFromPreferences(systemDark()); render(surfaceHolder) }
            }
        }
        private fun registerTimeReceiver() {
            if (timeReceiverRegistered) return
            ContextCompat.registerReceiver(this@DuneWallpaperService, timeReceiver, IntentFilter().apply {
                addAction(Intent.ACTION_TIME_TICK)
                addAction(Intent.ACTION_TIME_CHANGED)
                addAction(Intent.ACTION_TIMEZONE_CHANGED)
                addAction(Intent.ACTION_DATE_CHANGED)
                addAction(Intent.ACTION_CONFIGURATION_CHANGED)
            }, ContextCompat.RECEIVER_NOT_EXPORTED)
            timeReceiverRegistered = true
        }
        private fun unregisterTimeReceiver() {
            if (!timeReceiverRegistered) return
            unregisterReceiver(timeReceiver)
            timeReceiverRegistered = false
        }
        override fun onSurfaceCreated(holder: SurfaceHolder) { super.onSurfaceCreated(holder); appearance.reloadFromPreferences(systemDark()); render(holder) }
        override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            super.onSurfaceChanged(holder, format, width, height); appearance.reloadFromPreferences(systemDark()); render(holder)
        }
        override fun onVisibilityChanged(visible: Boolean) {
            this.visible = visible
            if (visible) {
                photoLoad++
                photoLoading = false
                photoFailed = false
                photo = cachedLauncherBackground(this@DuneWallpaperService)?.asImageBitmap()
                appearancePrefs.registerOnSharedPreferenceChangeListener(this)
                backgroundPrefs.registerOnSharedPreferenceChangeListener(this)
                wallpaperPrefs.registerOnSharedPreferenceChangeListener(this)
                registerTimeReceiver()
                appearance.reloadFromPreferences(systemDark()); render(surfaceHolder)
            } else {
                appearancePrefs.unregisterOnSharedPreferenceChangeListener(this)
                backgroundPrefs.unregisterOnSharedPreferenceChangeListener(this)
                wallpaperPrefs.unregisterOnSharedPreferenceChangeListener(this)
                unregisterTimeReceiver()
            }
        }
        override fun onDestroy() {
            photoLoad++
            loader.cancel()
            unregisterTimeReceiver()
            appearancePrefs.unregisterOnSharedPreferenceChangeListener(this)
            backgroundPrefs.unregisterOnSharedPreferenceChangeListener(this)
            wallpaperPrefs.unregisterOnSharedPreferenceChangeListener(this)
            super.onDestroy()
        }
        override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
            if (sharedPreferences === backgroundPrefs) {
                photoLoad++
                photo = cachedLauncherBackground(this@DuneWallpaperService)?.asImageBitmap()
                photoLoading = false
                photoFailed = false
            }
            if (visible) { appearance.reloadFromPreferences(systemDark()); render(surfaceHolder) }
        }
        private fun systemDark() = resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK ==
            android.content.res.Configuration.UI_MODE_NIGHT_YES
        private fun render(holder: SurfaceHolder) {
            if (!holder.surface.isValid) return
            if (launcherBackgroundEnabled(this@DuneWallpaperService) && photo == null && !photoFailed) {
                if (photoLoading) return
                photoLoading = true
                val request = ++photoLoad
                loader.launch {
                    val loaded = withContext(Dispatchers.IO) { loadLauncherBackground(this@DuneWallpaperService) }
                    if (request == photoLoad) {
                        photoLoading = false
                        photo = loaded?.asImageBitmap()
                        photoFailed = loaded == null
                        if (visible) render(holder)
                    }
                }
                return
            }
            if (!launcherBackgroundEnabled(this@DuneWallpaperService)) { photo = null; photoFailed = false }
            val canvas = try { holder.lockCanvas() } catch (_: IllegalArgumentException) { null } ?: return
            try {
                painter.draw(Density(resources.displayMetrics.density), LayoutDirection.Ltr,
                    androidx.compose.ui.graphics.Canvas(canvas), Size(canvas.width.toFloat(), canvas.height.toFloat())) {
                        drawLauncherBackground(photo, appearance.state.dark,
                            WallpaperStore.load(this@DuneWallpaperService).value.scene)
                    }
            } finally { holder.unlockCanvasAndPost(canvas) }
        }
    }
}
