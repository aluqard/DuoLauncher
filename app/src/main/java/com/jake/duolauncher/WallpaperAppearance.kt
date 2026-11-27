package com.jake.duolauncher

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.net.Uri
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/** The device the launcher fits its previews and foldable behavior to. */
enum class DeviceFit(val label: String, val foldable: Boolean, val coverAspect: Float) {
    FOLD7("Galaxy Z Fold7", true, .43f),
    FOLD8("Galaxy Z Fold8", true, .43f),
    FOLD8_ULTRA("Galaxy Z Fold8 Ultra", true, .43f),
    PHONE("Regular phone", false, .46f),
    OTHER("Other foldable", true, .43f),
}

/**
 * Best guess from the hardware: known Samsung model codes, otherwise any hinge sensor means "some
 * foldable" (null: not detected) and no hinge means a regular phone.
 */
internal fun detectDeviceFit(manufacturer: String, model: String, hasHinge: Boolean): DeviceFit? = when {
    manufacturer.equals("samsung", true) && model.uppercase().startsWith("SM-F966") -> DeviceFit.FOLD7
    hasHinge -> null
    else -> DeviceFit.PHONE
}

/** Built-in backgrounds shown when no photo is chosen. */
enum class BuiltInScene(val label: String) { NIGHT("Starry night"), DUNES("Duo dunes") }

data class WallpaperSettings(
    /** Show a separate photo on the folded (cover) screen. */
    val coverPhoto: Boolean = false,
    /** Shift the launcher background slightly against the device's tilt. */
    val motion: Boolean = true,
    /** Soft shadow under glass panels (dock, search button, edit buttons). */
    val glassShadow: Boolean = false,
    /** Chosen device, or null to use [detectDeviceFit]. */
    val device: DeviceFit? = null,
    /** Background drawn when no photo is chosen. */
    val scene: BuiltInScene = BuiltInScene.NIGHT,
    /** Frost Home like panes of glass while a book-style foldable opens. */
    val glassDoor: Boolean = true,
    /** How strong that frost is, 0.5..1.5. */
    val glassDoorStrength: Float = 1f,
    /** Which half of the inner screen frosts. */
    val glassDoorSide: GlassDoorSide = GlassDoorSide.LEFT,
)

internal fun encodeWallpaperSettings(s: WallpaperSettings): String = JSONObject().put("coverPhoto", s.coverPhoto)
    .put("motion", s.motion).put("glassShadow", s.glassShadow).put("device", s.device?.name ?: JSONObject.NULL)
    .put("scene", s.scene.name).put("glassDoor", s.glassDoor).put("glassDoorStrength", s.glassDoorStrength.toDouble())
    .put("glassDoorSide", s.glassDoorSide.name).toString()

internal fun decodeWallpaperSettings(raw: String?): WallpaperSettings = runCatching {
    if (raw.isNullOrBlank()) return WallpaperSettings()
    val j = JSONObject(raw)
    WallpaperSettings(j.optBoolean("coverPhoto", false), j.optBoolean("motion", true), j.optBoolean("glassShadow", false),
        if (j.isNull("device")) null else DeviceFit.entries.firstOrNull { it.name == j.optString("device") },
        BuiltInScene.entries.firstOrNull { it.name == j.optString("scene") } ?: BuiltInScene.NIGHT,
        j.optBoolean("glassDoor", true), j.optDouble("glassDoorStrength", 1.0).toFloat().coerceIn(.5f, 1.5f),
        GlassDoorSide.entries.firstOrNull { it.name == j.optString("glassDoorSide") } ?: GlassDoorSide.LEFT)
}.getOrDefault(WallpaperSettings())

object WallpaperStore {
    private const val PREFS = "wallpaper"
    private val mutable = MutableStateFlow(WallpaperSettings())
    private var loaded = false
    val state: StateFlow<WallpaperSettings> = mutable.asStateFlow()

    fun load(context: Context): StateFlow<WallpaperSettings> {
        if (!loaded) {
            loaded = true
            mutable.value = decodeWallpaperSettings(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("settings", null))
        }
        return state
    }

    fun update(context: Context, transform: (WallpaperSettings) -> WallpaperSettings) {
        load(context); mutable.update(transform)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("settings", encodeWallpaperSettings(mutable.value)).apply()
    }

    fun detected(context: Context): DeviceFit? = detectDeviceFit(Build.MANUFACTURER, Build.MODEL,
        context.getSystemService(SensorManager::class.java)?.getDefaultSensor(Sensor.TYPE_HINGE_ANGLE) != null)

    /** The chosen device, else the detected one, else "Other foldable". */
    fun device(context: Context): DeviceFit = load(context).value.device ?: detected(context) ?: DeviceFit.OTHER
}

/** The folded screen's own photo, kept beside the main launcher background. */
object CoverBackground {
    private const val FILE = "launcher-background-cover.jpg"
    private const val MAX_EDGE = 2048
    val revision = mutableIntStateOf(0)
    @Volatile private var cached: Bitmap? = null

    private fun file(context: Context) = File(context.filesDir, FILE)
    fun exists(context: Context) = file(context).isFile
    fun peek(): Bitmap? = cached?.takeUnless { it.isRecycled }

    fun load(context: Context): Bitmap? = peek() ?: runCatching { BitmapFactory.decodeFile(file(context).absolutePath) }
        .getOrNull()?.also { cached = it }

    /** Decodes [uri] at most 2048px on its long edge and stores it. Runs on a background thread. */
    fun save(context: Context, uri: Uri): Result<Unit> = runCatching {
        val bitmap = ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, info, _ ->
            val scale = minOf(1f, MAX_EDGE.toFloat() / maxOf(info.size.width, info.size.height))
            decoder.setTargetSize(maxOf(1, (info.size.width * scale).toInt()), maxOf(1, (info.size.height * scale).toInt()))
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }
        val temp = File(context.filesDir, "$FILE.tmp")
        FileOutputStream(temp).use { out ->
            require(bitmap.compress(Bitmap.CompressFormat.JPEG, 92, out)) { "The photo could not be saved." }
            out.fd.sync()
        }
        Files.move(temp.toPath(), file(context).toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        cached = bitmap
        revision.intValue++
    }

    fun clear(context: Context) { file(context).delete(); cached = null; revision.intValue++ }
}

/**
 * Device tilt relative to how it has recently been held, each axis in -1..1. Listens only while the
 * activity is visible. The resting angle drifts toward the current one, so the effect fades when still.
 */
@Composable
internal fun rememberTilt(): State<Offset> {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val tilt = remember { mutableStateOf(Offset.Zero) }
    DisposableEffect(lifecycle) {
        val sensors = context.getSystemService(SensorManager::class.java)
        val sensor = sensors?.getDefaultSensor(Sensor.TYPE_GRAVITY) ?: sensors?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        var rest: FloatArray? = null
        val smooth = FloatArray(2)
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                val x = event.values[0] / SensorManager.GRAVITY_EARTH
                val y = event.values[1] / SensorManager.GRAVITY_EARTH
                val base = rest ?: floatArrayOf(x, y).also { rest = it }
                base[0] += (x - base[0]) * .01f; base[1] += (y - base[1]) * .01f
                smooth[0] += ((x - base[0]) - smooth[0]) * .2f; smooth[1] += ((y - base[1]) - smooth[1]) * .2f
                tilt.value = Offset((smooth[0] * 3f).coerceIn(-1f, 1f), (smooth[1] * 3f).coerceIn(-1f, 1f))
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        fun start() { if (sensor != null) sensors.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI) }
        fun stop() { sensors?.unregisterListener(listener); rest = null; tilt.value = Offset.Zero }
        val observer = LifecycleEventObserver { _, event ->
            when (event) { Lifecycle.Event.ON_START -> start(); Lifecycle.Event.ON_STOP -> stop(); else -> Unit }
        }
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) start()
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer); stop() }
    }
    return tilt
}
