package com.jake.duolauncher

import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/** Remembers a posture the user closed by hand, so Stand mode does not reopen until the posture changes. */
object StandSession {
    @Volatile var suppressed: StandPosture? = null
    @Volatile var showing = false
}

internal fun isCharging(context: android.content.Context): Boolean {
    val battery = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) ?: return false
    return battery.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0
}

/** Full-screen clock, calendar and status shown while a foldable stands in a supported posture. */
class StandActivity : ComponentActivity() {
    private lateinit var monitor: PostureMonitor
    private lateinit var status: DeviceStatusMonitor
    private var posture by mutableStateOf(StandPosture.NONE)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        StandSession.showing = true
        posture = StandPosture.entries.firstOrNull { it.name == intent.getStringExtra(EXTRA_POSTURE) } ?: StandPosture.BOOK
        monitor = PostureMonitor(this) { isCharging(this) }
        status = DeviceStatusMonitor(this).also { lifecycle.addObserver(it) }
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowCompat.getInsetsController(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }
        val settings = StandStore.load(this)
        setContent {
            val reading by monitor.reading.collectAsStateWithLifecycle()
            val device by status.state.collectAsStateWithLifecycle()
            val standSettings by settings.collectAsStateWithLifecycle()
            var hour by remember { mutableIntStateOf(LocalDateTime.now().hour) }
            LaunchedEffect(Unit) { while (true) { delay(60_000); hour = LocalDateTime.now().hour } }
            val night = standSettings.dimAtNight && isNight(reading.lux, hour)
            LaunchedEffect(night) {
                window.attributes = window.attributes.apply { screenBrightness = if (night) .05f else WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE }
            }
            LaunchedEffect(standSettings.keepScreenOn, device.charging) {
                if (standSettings.keepScreenOn && device.charging) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
            BackHandler { StandSession.suppressed = posture; finish() }
            // Laptop and on-its-side layouts are landscape regardless of the Home app's orientation lock.
            LaunchedEffect(posture) {
                requestedOrientation = when (posture) {
                    StandPosture.LAPTOP, StandPosture.COVER_SIDEWAYS -> android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                    else -> android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                }
            }
            CompositionLocalProvider(LocalDeviceStatus provides device) {
                StandScreen(posture, night)
            }
        }
        lifecycleScope.launch {
            // Close once the posture has stopped qualifying for a moment (unfolded, laid flat, unplugged...).
            // Sensors report tiny changes continuously; react only when the decision changes, so the
            // closing delay is not restarted by every new reading.
            monitor.reading.map { reading ->
                val current = classifyPosture(reading)
                val keep = reading.hingeAngle == null || shouldShowStand(current, settings.value, reading.charging) ||
                    (!settings.value.closeWhenUnplugged && current == StandPosture.NONE && posture == StandPosture.COVER_SIDEWAYS &&
                        reading.hingeAngle < 30f)
                keep to current
            }.distinctUntilChanged().collectLatest { (keep, current) ->
                if (keep) { if (current != StandPosture.NONE) posture = current; return@collectLatest }
                delay(1200)
                finish()
            }
        }
    }

    override fun onResume() { super.onResume(); monitor.start() }
    override fun onPause() { monitor.stop(); super.onPause() }
    override fun onDestroy() { StandSession.showing = false; super.onDestroy() }

    companion object { const val EXTRA_POSTURE = "duo.stand.posture" }
}

private val NightRed = Color(0xFFE5533D)

@Composable
private fun StandScreen(posture: StandPosture, night: Boolean) {
    val ink = if (night) NightRed else Color(0xFFF4F1EA)
    val accent = if (night) NightRed else Color(ThemeStore.state.collectAsState().value.accent())
    Box(Modifier.fillMaxSize().background(Color.Black).testTag("stand-${posture.name.lowercase()}")) {
        when (posture) {
            StandPosture.LAPTOP -> Column(Modifier.fillMaxSize()) {
                ClockBoards(Modifier.weight(1f).fillMaxWidth(), ink, accent, night)
                InfoRow(Modifier.weight(1f).fillMaxWidth().padding(32.dp), ink, accent, large = true)
            }
            StandPosture.COVER_SIDEWAYS -> Row(Modifier.fillMaxSize().padding(24.dp), verticalAlignment = Alignment.CenterVertically) {
                DateBlock(Modifier.weight(1f), ink, accent)
                ClockBoards(Modifier.weight(2f).fillMaxHeight(), ink, accent, night)
                InfoRow(Modifier.weight(1f), ink, accent, large = false, vertical = true)
            }
            else -> Row(Modifier.fillMaxSize()) {
                ClockBoards(Modifier.weight(1f).fillMaxHeight(), ink, accent, night)
                Column(Modifier.weight(1f).fillMaxHeight().padding(32.dp), verticalArrangement = Arrangement.SpaceEvenly) {
                    DateBlock(Modifier, ink, accent)
                    MonthGrid(ink, accent)
                    InfoRow(Modifier.fillMaxWidth(), ink, accent, large = false)
                }
            }
        }
    }
}

/** Clock faces; swipe up or down to change. */
@Composable
private fun ClockBoards(modifier: Modifier, ink: Color, accent: Color, night: Boolean) {
    val pager = rememberPagerState { 3 }
    var visible by remember { mutableStateOf(true) }
    val millis = rememberClockMillis(visible)
    val time = LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), ZoneId.systemDefault())
    val angles = clockHandAngles(time.hour, time.minute, time.second, (millis % 1000).toInt())
    Box(modifier.trackVisibility { visible = it }) {
        VerticalPager(pager, Modifier.fillMaxSize().testTag("stand-faces")) { face ->
            Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                when (face) {
                    0 -> Canvas(Modifier.fillMaxSize()) { drawDial(angles, accent, face = Color(0xFF101010), ink = ink) }
                    1 -> Text(time.format(DateTimeFormatter.ofPattern("HH\nmm")), color = ink, fontSize = 120.sp, lineHeight = 116.sp,
                        fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                    else -> Canvas(Modifier.fillMaxSize()) {
                        val r = size.minDimension / 2f; val c = Offset(size.width / 2f, size.height / 2f)
                        for (hour in 0 until 12) rotate(hour * 30f, c) {
                            drawLine(ink, Offset(c.x, c.y - r), Offset(c.x, c.y - r * .86f), strokeWidth = r * .03f, cap = StrokeCap.Round)
                        }
                        rotate(angles.hour, c) { drawLine(ink, c, Offset(c.x, c.y - r * .5f), strokeWidth = r * .05f, cap = StrokeCap.Round) }
                        rotate(angles.minute, c) { drawLine(ink, c, Offset(c.x, c.y - r * .78f), strokeWidth = r * .035f, cap = StrokeCap.Round) }
                        rotate(angles.second, c) { drawLine(accent, c, Offset(c.x, c.y - r * .86f), strokeWidth = r * .012f) }
                    }
                }
            }
        }
        Column(Modifier.align(Alignment.CenterEnd).padding(end = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            repeat(3) { Box(Modifier.size(6.dp).background(ink.copy(alpha = if (it == pager.currentPage) 1f else .3f), CircleShape)) }
        }
    }
}

@Composable
private fun DateBlock(modifier: Modifier, ink: Color, accent: Color) {
    val today = LocalDate.now()
    Column(modifier) {
        Text(today.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault()), color = accent, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
        Text(today.dayOfMonth.toString(), color = ink, fontSize = 72.sp, fontWeight = FontWeight.Bold, lineHeight = 74.sp)
    }
}

@Composable
private fun MonthGrid(ink: Color, accent: Color) {
    val today = LocalDate.now()
    val first = today.withDayOfMonth(1)
    val lead = first.dayOfWeek.value % 7 // Sunday-first columns
    Column {
        Text(today.format(DateTimeFormatter.ofPattern("MMMM yyyy")), color = ink.copy(alpha = .7f), fontSize = 16.sp)
        Spacer(Modifier.height(8.dp))
        Row { listOf("S", "M", "T", "W", "T", "F", "S").forEach { Text(it, Modifier.weight(1f), color = ink.copy(alpha = .6f), textAlign = TextAlign.Center) } }
        (0 until 6).forEach { week ->
            Row {
                (0 until 7).forEach { day ->
                    val number = week * 7 + day - lead + 1
                    val valid = number in 1..today.lengthOfMonth()
                    Box(Modifier.weight(1f).height(30.dp), contentAlignment = Alignment.Center) {
                        if (valid) {
                            if (number == today.dayOfMonth) Box(Modifier.size(28.dp).background(accent, CircleShape))
                            Text(number.toString(), color = if (number == today.dayOfMonth) Color.Black else ink, fontSize = 14.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun InfoRow(modifier: Modifier, ink: Color, accent: Color, large: Boolean, vertical: Boolean = false) {
    val status = LocalDeviceStatus.current
    val alarm = status.nextAlarm?.let { LocalDateTime.ofInstant(Instant.ofEpochMilli(it), ZoneId.systemDefault()) }
    val size = if (large) 56.sp else 32.sp
    @Composable fun item(label: String, value: String, color: Color) = Column {
        Text(label, color = ink.copy(alpha = .6f), fontSize = 14.sp)
        Text(value, color = color, fontSize = size, fontWeight = FontWeight.SemiBold)
    }
    if (vertical) Column(modifier, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item("Next alarm", alarm?.format(DateTimeFormatter.ofPattern("HH:mm")) ?: "--:--", ink)
        item("Battery", status.battery?.let { "$it%" } ?: "--", if (status.charging) Color(0xFF63D47E) else ink)
    } else Row(modifier, horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
        if (large) DateBlock(Modifier, ink, accent)
        item("Next alarm", alarm?.format(DateTimeFormatter.ofPattern("HH:mm")) ?: "--:--", ink)
        item("Battery", status.battery?.let { "$it%" } ?: "--", if (status.charging) Color(0xFF63D47E) else ink)
    }
}
