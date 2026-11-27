package com.jake.duolauncher

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.BatteryStd
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** Device readings shared with built-in cards without threading them through every Home layer. */
val LocalDeviceStatus = staticCompositionLocalOf { DeviceStatus() }

data class HandAngles(val hour: Float, val minute: Float, val second: Float)

/** Clockwise degrees from 12 o'clock. Minute and hour hands advance continuously, not in one-minute jumps. */
fun clockHandAngles(hour: Int, minute: Int, second: Int, millis: Int): HandAngles {
    val seconds = second + millis / 1000f
    val minutes = minute + seconds / 60f
    val hours = (hour % 12) + minutes / 60f
    return HandAngles(hours * 30f, minutes * 6f, seconds * 6f)
}

/**
 * Wall-clock milliseconds for animated clocks. While the card is on screen and the activity is
 * visible it updates every frame (smooth sweep); off screen it ticks once a second; stopped, it rests.
 * Visible, not resumed: Live Discover keeps a transparent window over Home, which pauses Home while
 * it is still in plain view.
 */
@Composable
fun rememberClockMillis(visible: Boolean): Long {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(lifecycle, visible) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            if (visible) while (true) withFrameMillis { now = System.currentTimeMillis() }
            else while (true) { now = System.currentTimeMillis(); delay(1000 - now % 1000) }
        }
    }
    return now
}

/** True while any part of this node is inside the window; off-screen pager pages report empty bounds. */
fun Modifier.trackVisibility(onChange: (Boolean) -> Unit) = onGloballyPositioned { coordinates ->
    val bounds = coordinates.boundsInWindow()
    onChange(bounds.width > 1f && bounds.height > 1f)
}

@Composable
internal fun AnalogClockCard(onClick: () -> Unit) {
    var visible by remember { mutableStateOf(true) }
    val millis = rememberClockMillis(visible)
    val accent = Color(ThemeStore.state.collectAsState().value.accent())
    val time = LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), ZoneId.systemDefault())
    Surface(Modifier.fillMaxSize().clip(RoundedCornerShape(24.dp)).glassDepth(RoundedCornerShape(24.dp), Glass.copy(alpha = .24f))
        .clickable(onClick = onClick)
        .trackVisibility { visible = it }
        .semantics { contentDescription = "Analog clock, ${time.format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))}" },
        color = Color.Transparent, shape = RoundedCornerShape(24.dp)) {
        Canvas(Modifier.fillMaxSize().padding(10.dp)) {
            val angles = clockHandAngles(time.hour, time.minute, time.second, (millis % 1000).toInt())
            drawDial(angles, accent)
        }
    }
}

/** Shared by the Home card and Stand mode so both faces move identically. */
fun DrawScope.drawDial(angles: HandAngles, accent: Color, face: Color = Color.White, ink: Color = Color(0xFF15171A)) {
    val radius = size.minDimension / 2f
    val center = Offset(size.width / 2f, size.height / 2f)
    drawCircle(face, radius, center)
    for (tick in 0 until 60) {
        val major = tick % 5 == 0
        rotate(tick * 6f, center) {
            drawLine(ink.copy(alpha = if (major) .9f else .35f), Offset(center.x, center.y - radius * .92f),
                Offset(center.x, center.y - radius * if (major) .78f else .86f),
                strokeWidth = radius * if (major) .045f else .02f, cap = StrokeCap.Round)
        }
    }
    fun hand(angle: Float, length: Float, width: Float, color: Color, tail: Float = .12f) = rotate(angle, center) {
        drawLine(color, Offset(center.x, center.y + radius * tail), Offset(center.x, center.y - radius * length),
            strokeWidth = radius * width, cap = StrokeCap.Round)
    }
    hand(angles.hour, .5f, .075f, ink)
    hand(angles.minute, .74f, .055f, ink)
    hand(angles.second, .84f, .02f, accent, tail = .2f)
    drawCircle(accent, radius * .05f, center)
}

@Composable
internal fun BatteryCard(onClick: () -> Unit) {
    val status = LocalDeviceStatus.current
    val level = status.battery
    GlassCard(onClick = onClick) {
        Icon(if (status.charging) Icons.Rounded.BatteryChargingFull else Icons.Rounded.BatteryStd,
            "Battery widget; tap to replace", tint = Color.White, modifier = Modifier.size(22.dp))
        Text(level?.let { "$it%" } ?: "--", color = Color.White, fontWeight = FontWeight.Light, fontSize = 34.sp, maxLines = 1)
        Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp))) {
            Box(Modifier.matchParentSize().padding(0.dp)) { Surface(Modifier.fillMaxSize(), color = Color.White.copy(alpha = .2f)) {} }
            Surface(Modifier.fillMaxHeight().fillMaxWidth((level ?: 0) / 100f),
                color = if ((level ?: 100) <= 15 && !status.charging) Color(0xFFFF6B5E) else Color(0xFF63D47E)) {}
        }
        Text(when (status.plug) {
            PowerSource.WIRELESS -> "Wireless charging"; PowerSource.NONE -> if (status.charging) "Charging" else "On battery"
            else -> "Charging"
        }, color = Color.White.copy(alpha = .8f), fontSize = 11.sp, maxLines = 1)
    }
}

@Composable
internal fun AlarmCard(onClick: () -> Unit) {
    val next = LocalDeviceStatus.current.nextAlarm
    val context = LocalContext.current
    val pattern = if (android.text.format.DateFormat.is24HourFormat(context)) "HH:mm" else "h:mm a"
    val time = next?.let { LocalDateTime.ofInstant(Instant.ofEpochMilli(it), ZoneId.systemDefault()) }
    GlassCard(onClick = onClick) {
        Icon(Icons.Rounded.Alarm, "Next alarm widget; tap to replace", tint = Color.White, modifier = Modifier.size(20.dp))
        Text(time?.format(DateTimeFormatter.ofPattern(pattern)) ?: "--:--", color = Color.White,
            fontWeight = FontWeight.Light, fontSize = 30.sp, maxLines = 1)
        Text(time?.format(DateTimeFormatter.ofPattern("EEE")) ?: "No alarm", color = Color.White.copy(alpha = .8f), fontSize = 11.sp)
    }
}

/** User-facing names of launcher-drawn cards, in widget-picker order. */
val BUILTIN_WIDGET_NAMES = linkedMapOf(
    CLOCK_WIDGET to "Clock",
    ANALOG_CLOCK_WIDGET to "Analog clock",
    DATE_WIDGET to "Date",
    BATTERY_WIDGET to "Battery",
    ALARM_WIDGET to "Next alarm",
    MEMO_WIDGET to "Memo",
    CHECKLIST_WIDGET to "Checklist",
    INFO_WIDGET to "Widget panel",
)

/** Built-in cards offered in the widget picker (the legacy "Widget panel" stays available for old layouts). */
val PICKABLE_BUILTIN_WIDGETS = setOf(CLOCK_WIDGET, ANALOG_CLOCK_WIDGET, DATE_WIDGET, BATTERY_WIDGET, ALARM_WIDGET,
    MEMO_WIDGET, CHECKLIST_WIDGET, INFO_WIDGET)
