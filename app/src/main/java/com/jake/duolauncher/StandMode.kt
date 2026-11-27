package com.jake.duolauncher

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.json.JSONObject
import kotlin.math.abs

/** Positions in which Stand mode can show. */
enum class StandPosture(val label: String) {
    NONE("None"),
    /** Half-folded with the hinge upright, like an open book: both halves are used. */
    BOOK("Half-folded like a book"),
    /** Half-folded with the hinge level, like a laptop: the upper half is used. */
    LAPTOP("Half-folded like a laptop"),
    /** Folded, lying on its long side (on a charging stand): the cover screen in landscape. */
    COVER_SIDEWAYS("Folded, on its side"),
    /** Fully open and standing upright (on a stand): both halves. */
    OPEN_STANDING("Open, standing"),
}

enum class StandTrigger(val label: String) { OFF("Off"), ALWAYS("Always"), CHARGING("Only while charging") }

data class StandSettings(
    val trigger: StandTrigger = StandTrigger.ALWAYS,
    val postures: Set<StandPosture> = setOf(StandPosture.BOOK, StandPosture.LAPTOP, StandPosture.COVER_SIDEWAYS),
    val closeWhenUnplugged: Boolean = true,
    val dimAtNight: Boolean = true,
    val keepScreenOn: Boolean = true,
)

/** One reading of the sensors that decide Stand mode. Gravity is in device coordinates (m/s²). */
data class PostureReading(
    val hingeAngle: Float?,
    val gravityX: Float,
    val gravityY: Float,
    val gravityZ: Float,
    val charging: Boolean,
    val lux: Float? = null,
)

/**
 * Classifies a reading. Half-folded postures (30°–150°) are deliberate and need no charger.
 * Folded-on-its-side and open-standing look like ordinary use when hand-held, so they also require charging.
 * Lying flat (gravity mostly through the screen) never counts.
 */
fun classifyPosture(r: PostureReading): StandPosture {
    val hinge = r.hingeAngle ?: return StandPosture.NONE
    val ax = abs(r.gravityX); val ay = abs(r.gravityY); val az = abs(r.gravityZ)
    val total = ax + ay + az
    if (total < 3f) return StandPosture.NONE
    val flat = az > maxOf(ax, ay)
    return when {
        hinge in 30f..150f -> when {
            flat -> StandPosture.NONE
            ay >= ax -> StandPosture.BOOK
            else -> StandPosture.LAPTOP
        }
        hinge < 30f -> if (!flat && ax > ay && r.charging) StandPosture.COVER_SIDEWAYS else StandPosture.NONE
        else -> if (!flat && ay > ax && r.charging) StandPosture.OPEN_STANDING else StandPosture.NONE
    }
}

/** Whether the classified posture should show Stand mode under [settings]. */
fun shouldShowStand(posture: StandPosture, settings: StandSettings, charging: Boolean): Boolean =
    posture != StandPosture.NONE && posture in settings.postures && when (settings.trigger) {
        StandTrigger.OFF -> false
        StandTrigger.ALWAYS -> true
        StandTrigger.CHARGING -> charging
    }

/** Room darkness for the night look: dark below 5 lx; without a light sensor, 22:00–06:00. */
fun isNight(lux: Float?, hour: Int): Boolean = lux?.let { it < 5f } ?: (hour >= 22 || hour < 6)

internal fun encodeStandSettings(s: StandSettings) = JSONObject().put("trigger", s.trigger.name)
    .put("postures", org.json.JSONArray(s.postures.map { it.name })).put("closeWhenUnplugged", s.closeWhenUnplugged)
    .put("dimAtNight", s.dimAtNight).put("keepScreenOn", s.keepScreenOn).toString()

internal fun decodeStandSettings(raw: String?): StandSettings = runCatching {
    if (raw.isNullOrBlank()) return StandSettings()
    val j = JSONObject(raw)
    val postures = j.getJSONArray("postures").let { a -> List(a.length()) { a.getString(it) } }
        .mapNotNull { name -> StandPosture.entries.firstOrNull { it.name == name } }.filter { it != StandPosture.NONE }.toSet()
    StandSettings(StandTrigger.entries.firstOrNull { it.name == j.getString("trigger") } ?: StandTrigger.ALWAYS,
        postures.ifEmpty { StandSettings().postures }, j.optBoolean("closeWhenUnplugged", true),
        j.optBoolean("dimAtNight", true), j.optBoolean("keepScreenOn", true))
}.getOrDefault(StandSettings())

object StandStore {
    private const val PREFS = "stand"
    private val settings = MutableStateFlow(StandSettings())
    private var loaded = false

    fun load(context: Context): StateFlow<StandSettings> {
        if (!loaded) { loaded = true; settings.value = decodeStandSettings(context.getSharedPreferences(PREFS, 0).getString("settings", null)) }
        return settings.asStateFlow()
    }

    fun update(context: Context, transform: (StandSettings) -> StandSettings) {
        load(context); settings.update(transform)
        context.getSharedPreferences(PREFS, 0).edit().putString("settings", encodeStandSettings(settings.value)).apply()
    }
}

/**
 * Listens to the hinge angle, accelerometer and ambient light only while started. Devices without a
 * hinge sensor never report a posture, so Stand mode stays off on ordinary phones.
 */
class PostureMonitor(context: Context, private val charging: () -> Boolean) : SensorEventListener {
    private val sensors = context.getSystemService(SensorManager::class.java)
    private val hinge = sensors?.getDefaultSensor(Sensor.TYPE_HINGE_ANGLE)
    private val accel = sensors?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val light = sensors?.getDefaultSensor(Sensor.TYPE_LIGHT)
    private val mutable = MutableStateFlow(PostureReading(null, 0f, 0f, 0f, false))
    val reading: StateFlow<PostureReading> = mutable.asStateFlow()
    val available: Boolean get() = hinge != null && accel != null
    private val gravity = FloatArray(3)

    fun start() {
        if (!available) return
        sensors?.registerListener(this, hinge, SensorManager.SENSOR_DELAY_NORMAL)
        sensors?.registerListener(this, accel, SensorManager.SENSOR_DELAY_NORMAL)
        light?.let { sensors?.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL) }
    }

    fun stop() { sensors?.unregisterListener(this) }

    override fun onSensorChanged(event: SensorEvent) {
        when (event.sensor.type) {
            Sensor.TYPE_HINGE_ANGLE -> mutable.update { it.copy(hingeAngle = event.values[0], charging = charging()) }
            Sensor.TYPE_ACCELEROMETER -> {
                // Low-pass filter keeps gravity and drops hand shake.
                for (i in 0..2) gravity[i] = gravity[i] * .8f + event.values[i] * .2f
                mutable.update { it.copy(gravityX = gravity[0], gravityY = gravity[1], gravityZ = gravity[2], charging = charging()) }
            }
            Sensor.TYPE_LIGHT -> mutable.update { it.copy(lux = event.values[0]) }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
}
