package com.jake.duolauncher

import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.content.pm.LauncherApps
import android.os.Process
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.widget.RemoteViews
import android.appwidget.AppWidgetManager
import androidx.compose.foundation.Image
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridItemSpanScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.LinkedHashMap

internal data class WidgetCatalogEntry(
    val provider: AppWidgetProviderInfo,
    val providerLabel: String,
    val appLabel: String,
    val description: String,
    val userSerial: Long = 0,
    val profileLabel: String = "Personal",
    val isWork: Boolean = false,
)

internal data class WidgetPickerSession(
    val provider: AppWidgetProviderInfo?,
    val slot: Int,
    val span: WidgetSpan,
    val pointer: Offset,
    val dragging: Boolean,
    val targetIndex: Int? = null,
    val candidate: WidgetPlacement? = null,
    val builtinId: Int? = null,
)

internal fun widgetCatalog(context: Context, providers: List<AppWidgetProviderInfo>, profile: AppProfile): List<WidgetCatalogEntry> {
    val pm = context.packageManager
    val launcherApps = context.getSystemService(LauncherApps::class.java)
    return providers.map { provider ->
        val packageName = provider.provider.packageName
        val appLabel = runCatching {
            launcherApps.getApplicationInfo(packageName, 0, provider.profile).loadLabel(pm).toString()
        }.recoverCatching {
            if (provider.profile != Process.myUserHandle()) throw it
            pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
        }
            .getOrDefault(packageName)
        val providerLabel = provider.loadLabel(pm).toString()
        val description = if (android.os.Build.VERSION.SDK_INT >= 31)
            runCatching { provider.loadDescription(context)?.toString().orEmpty() }.getOrDefault("") else ""
        WidgetCatalogEntry(provider, providerLabel, appLabel, description, profile.userSerial, profile.label, profile.isWork)
    }.sortedWith(compareBy({ it.appLabel.lowercase() }, { it.providerLabel.lowercase() }))
}

private sealed interface CatalogPreview {
    data class Remote(val views: RemoteViews) : CatalogPreview
    data class Picture(val bitmap: Bitmap) : CatalogPreview
    data object Missing : CatalogPreview
}

private object WidgetPreviewCache {
    private const val MAX_BYTES = 16 * 1024 * 1024
    private val values = object : LinkedHashMap<String, CatalogPreview>(16, .75f, true) {}
    private var bytes = 0
    @Synchronized fun get(key: String) = values[key]
    @Synchronized fun put(key: String, value: CatalogPreview) {
        values.remove(key)?.let { bytes -= it.cost }
        values[key] = value; bytes += value.cost
        val iterator = values.entries.iterator()
        while (bytes > MAX_BYTES && iterator.hasNext()) { bytes -= iterator.next().value.cost; iterator.remove() }
    }
    private val CatalogPreview.cost get() = when (this) {
        is CatalogPreview.Picture -> bitmap.allocationByteCount
        is CatalogPreview.Remote -> 64 * 1024
        CatalogPreview.Missing -> 1
    }
}

private suspend fun loadWidgetPreview(context: Context, provider: AppWidgetProviderInfo, span: WidgetSpan): CatalogPreview {
    val density = context.resources.displayMetrics.densityDpi
    val key = "${provider.provider.flattenToString()}|${provider.profile.hashCode()}|$density|${span.width}x${span.height}"
    WidgetPreviewCache.get(key)?.let { return it }
    val loaded = withContext(Dispatchers.IO) {
        val manager = AppWidgetManager.getInstance(context)
        var remote: RemoteViews? = null
        if (android.os.Build.VERSION.SDK_INT >= 35 &&
            provider.generatedPreviewCategories and AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN != 0) {
            remote = runCatching { manager.getWidgetPreview(provider.provider, provider.profile,
                AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN) }.getOrNull()
        }
        if (remote == null && android.os.Build.VERSION.SDK_INT >= 31 && provider.previewLayout != 0)
            remote = runCatching { RemoteViews(provider.provider.packageName, provider.previewLayout) }.getOrNull()
        remote?.let(CatalogPreview::Remote) ?: run {
            val drawable = runCatching { provider.loadPreviewImage(context, density) }.getOrNull()
                ?: runCatching { provider.loadIcon(context, density) }.getOrNull()
            drawable
        }
    }
    // Drawable.draw may touch theme/view state. Convert it on Compose's UI thread; only resource
    // discovery and generated-preview IPC belong on the worker dispatcher.
    val result = when (loaded) {
        is CatalogPreview -> loaded
        is Drawable -> CatalogPreview.Picture(loaded.catalogBitmap())
        else -> CatalogPreview.Missing
    }
    WidgetPreviewCache.put(key, result)
    return result
}

private fun Drawable.catalogBitmap(): Bitmap {
    val sourceWidth = intrinsicWidth.takeIf { it > 0 } ?: 144
    val sourceHeight = intrinsicHeight.takeIf { it > 0 } ?: 144
    val scale = minOf(1f, 720f / sourceWidth, 480f / sourceHeight)
    val w = (sourceWidth * scale).toInt().coerceAtLeast(1)
    val h = (sourceHeight * scale).toInt().coerceAtLeast(1)
    return Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also { bitmap ->
        val canvas = android.graphics.Canvas(bitmap)
        setBounds(0, 0, w, h)
        draw(canvas)
    }
}

@Composable
internal fun WidgetProviderPreview(entry: WidgetCatalogEntry, span: WidgetSpan, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val preview by produceState<CatalogPreview?>(null, entry.provider, span, context.resources.displayMetrics.densityDpi) {
        value = loadWidgetPreview(context, entry.provider, span)
    }
    Box(modifier.background(Glass.copy(alpha = .38f)), contentAlignment = Alignment.Center) {
        when (val value = preview) {
            is CatalogPreview.Remote -> AndroidView(factory = { previewContext ->
                object : android.widget.FrameLayout(previewContext) {
                    override fun dispatchTouchEvent(event: android.view.MotionEvent?): Boolean = false
                }.apply {
                    importantForAccessibility = android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
                    isFocusable = false
                    isClickable = false
                    runCatching { addView(value.views.apply(previewContext, this)) }
                        .onFailure { addView(android.widget.TextView(previewContext).apply { text = entry.providerLabel }) }
                }
            }, modifier = Modifier.fillMaxSize().padding(6.dp))
            is CatalogPreview.Picture -> Image(value.bitmap.asImageBitmap(), null, Modifier.fillMaxSize().padding(8.dp),
                contentScale = ContentScale.Fit)
            CatalogPreview.Missing -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(entry.providerLabel, style = MaterialTheme.typography.bodySmall)
                Text("${span.width} × ${span.height}", style = MaterialTheme.typography.labelSmall)
            }
            null -> CircularProgressIndicator(Modifier.size(26.dp), strokeWidth = 3.dp)
        }
    }
}

private val PickerSheet = Color(0xFF1E1F22)
private val PickerField = Color(0xFF34353A)
private val PickerCard = Color(0xFF2A2B2F)
private val PickerMuted = Color.White.copy(alpha = .62f)

/** Material icon standing in for each built-in widget on its picker tile. */
private fun builtinWidgetIcon(id: Int) = when (id) {
    CLOCK_WIDGET -> Icons.Rounded.Schedule
    ANALOG_CLOCK_WIDGET -> Icons.Rounded.Watch
    DATE_WIDGET -> Icons.Rounded.CalendarToday
    BATTERY_WIDGET -> Icons.Rounded.BatteryFull
    ALARM_WIDGET -> Icons.Rounded.Alarm
    MEMO_WIDGET -> Icons.Rounded.EditNote
    CHECKLIST_WIDGET -> Icons.Rounded.Checklist
    else -> Icons.Rounded.Dashboard
}

/**
 * The widget catalog as a dark bottom sheet over Home: a search pill with a close button, the
 * launcher's own widgets as square tiles, then each app's widgets as preview cards. Tap shows where it
 * goes on Home; long-press drags it there. Swiping the handle down or tapping above the sheet closes it.
 */
@Composable
internal fun VisualWidgetPicker(
    entries: List<WidgetCatalogEntry>?,
    profiles: List<AppProfile>,
    selectedProfile: AppProfile,
    onSelectProfile: (AppProfile) -> Unit,
    onTurnOnWork: (Long) -> Unit,
    hiddenForDrag: Boolean,
    footprint: (AppWidgetProviderInfo) -> WidgetSpan?,
    onBack: () -> Unit,
    onTap: (AppWidgetProviderInfo) -> Unit,
    onBuiltin: (Int) -> Unit,
    onDragStart: (AppWidgetProviderInfo, Offset) -> Unit,
    onDrag: (Offset) -> Unit,
    onDrop: () -> Unit,
    onCancelDrag: () -> Unit,
    onBuiltinDragStart: (Int, Offset) -> Unit = { _, _ -> },
    onClose: () -> Unit = onBack,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val latestDragStart by rememberUpdatedState(onDragStart)
    val latestBuiltinDragStart by rememberUpdatedState(onBuiltinDragStart)
    val latestDrag by rememberUpdatedState(onDrag)
    val latestDrop by rememberUpdatedState(onDrop)
    val latestCancelDrag by rememberUpdatedState(onCancelDrag)
    val latestClose by rememberUpdatedState(onClose)
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val density = LocalDensity.current
    val words = query.trim().lowercase()
    val filtered = remember(entries, words) { entries.orEmpty().filter { entry ->
        words.isEmpty() || listOf(entry.appLabel, entry.providerLabel, entry.description,
            entry.provider.provider.packageName).any { it.lowercase().contains(words) }
    } }
    val builtins = remember(words, selectedProfile.isPersonal) {
        if (!selectedProfile.isPersonal) emptyList()
        else BUILTIN_WIDGET_NAMES.toList().filter { (id, label) -> id in PICKABLE_BUILTIN_WIDGETS && label.lowercase().contains(words) }
    }
    var sheetOffset by remember { mutableFloatStateOf(0f) }
    fun dismissKeyboard() { focusManager.clearFocus(); keyboard?.hide() }
    Box(Modifier.fillMaxSize().alpha(if (hiddenForDrag) 0f else 1f)
        .then(if (hiddenForDrag) Modifier.clearAndSetSemantics { }.focusProperties { canFocus = false } else Modifier)
        .testTag("visual-widget-picker")) {
        // Home stays visible above the sheet; tapping it closes the catalog.
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .35f)).pointerInput(Unit) {
            detectTapGestures { latestClose() }
        })
        Surface(Modifier.fillMaxSize().statusBarsPadding().padding(top = 40.dp)
            .offset { IntOffset(0, sheetOffset.roundToInt()) },
            shape = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp), color = PickerSheet, contentColor = Color.White) {
            Column(Modifier.fillMaxSize().navigationBarsPadding().padding(horizontal = 18.dp)) {
                Box(Modifier.fillMaxWidth().height(30.dp).testTag("widget-picker-handle").pointerInput(Unit) {
                    detectVerticalDragGestures(
                        onDragEnd = { if (sheetOffset > with(density) { 120.dp.toPx() }) latestClose(); sheetOffset = 0f },
                        onDragCancel = { sheetOffset = 0f },
                        onVerticalDrag = { change, dy -> change.consume(); sheetOffset = (sheetOffset + dy).coerceAtLeast(0f) })
                }, contentAlignment = Alignment.Center) {
                    Box(Modifier.size(width = 40.dp, height = 5.dp).background(Color.White.copy(alpha = .3f), CircleShape))
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextField(query, { query = it }, Modifier.weight(1f).heightIn(min = 52.dp).testTag("widget-catalog-search"),
                        singleLine = true, shape = CircleShape, placeholder = { Text("Search widgets", color = PickerMuted) },
                        leadingIcon = { Icon(Icons.Rounded.Search, null, tint = PickerMuted) },
                        trailingIcon = { if (query.isNotEmpty()) IconButton({ query = "" }) { Icon(Icons.Rounded.Close, "Clear search", tint = PickerMuted) } },
                        colors = TextFieldDefaults.colors(focusedContainerColor = PickerField, unfocusedContainerColor = PickerField,
                            focusedTextColor = Color.White, unfocusedTextColor = Color.White, cursorColor = Color.White,
                            focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent))
                    Spacer(Modifier.width(12.dp))
                    Surface(onClick = onClose, modifier = Modifier.size(44.dp).testTag("widget-picker-close"), shape = CircleShape,
                        color = PickerField, contentColor = Color.White) {
                        Box(contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Close, "Close", Modifier.size(22.dp)) }
                    }
                }
                Text("Tap to add, or long-press and drag to where you want it", color = PickerMuted,
                    style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = 4.dp, top = 12.dp, bottom = 4.dp))
                if (profiles.any { it.isWork }) Row(Modifier.fillMaxWidth().padding(top = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    profiles.forEach { profile ->
                        FilterChip(selected = profile.userSerial == selectedProfile.userSerial,
                            onClick = { onSelectProfile(profile) }, label = { Text(profile.label) })
                    }
                }
                if (!selectedProfile.available || !selectedProfile.unlocked || selectedProfile.quiet) {
                    Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(if (selectedProfile.quiet) "${selectedProfile.label} apps are paused"
                            else "${selectedProfile.label} profile is unavailable")
                        if (selectedProfile.isWork) Button(onClick = { onTurnOnWork(selectedProfile.userSerial) },
                            modifier = Modifier.padding(top = 12.dp)) { Text("Turn on") }
                    }
                }
                BoxWithConstraints(Modifier.fillMaxSize()) {
                    val columns = if (maxWidth >= 600.dp) 3 else 2
                    val full: LazyGridItemSpanScope.() -> GridItemSpan = { GridItemSpan(maxLineSpan) }
                    LazyVerticalGrid(GridCells.Fixed(columns), Modifier.fillMaxSize().testTag("widget-catalog-list"),
                        horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp),
                        contentPadding = PaddingValues(top = 12.dp, bottom = 28.dp)) {
                        if (builtins.isNotEmpty()) {
                            item("duo-widgets", span = full) { PickerHeader("Duo Launcher") }
                            builtins.forEach { (id, label) ->
                                item("builtin-$id") {
                                    var origin by remember { mutableStateOf(Offset.Zero) }
                                    // The tile and its name are one target: tap places, long-press drags.
                                    Column(Modifier.clip(RoundedCornerShape(28.dp))
                                        .onGloballyPositioned { origin = it.boundsInRoot().topLeft }
                                        .pointerInput(id) {
                                            detectDragGesturesAfterLongPress(
                                                onDragStart = { point -> dismissKeyboard(); latestBuiltinDragStart(id, origin + point) },
                                                onDrag = { change, _ -> change.consume(); latestDrag(origin + change.position) },
                                                onDragEnd = { latestDrop() }, onDragCancel = { latestCancelDrag() })
                                        }
                                        .clickable { dismissKeyboard(); onBuiltin(id) }, horizontalAlignment = Alignment.CenterHorizontally) {
                                        Box(Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(28.dp))
                                            .background(Brush.verticalGradient(listOf(Color(0xFF47484D), Color(0xFF2E2F33))))
                                            .border(1.dp, Color.White.copy(alpha = .08f), RoundedCornerShape(28.dp))
                                            .testTag("widget-builtin-$id"), contentAlignment = Alignment.Center) {
                                            Icon(builtinWidgetIcon(id), null, Modifier.size(40.dp), tint = Color.White)
                                        }
                                        Text(label, Modifier.padding(top = 10.dp), fontWeight = FontWeight.SemiBold,
                                            style = MaterialTheme.typography.titleMedium, maxLines = 1)
                                        Text("2 × 2", color = PickerMuted, style = MaterialTheme.typography.bodyMedium)
                                    }
                                }
                            }
                        }
                        if (entries == null) item("catalog-loading", span = full) {
                            Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(color = Color.White)
                            }
                        }
                        filtered.groupBy { it.appLabel }.forEach { (app, group) ->
                            item("header-$app", span = full) { PickerHeader(app) }
                            group.forEach { entry ->
                                item(entry.provider.provider.flattenToString() + "@" + entry.userSerial, span = full) {
                                    PickerProviderCard(entry, footprint(entry.provider),
                                        onTap = { dismissKeyboard(); onTap(entry.provider) },
                                        onDragStart = { point -> dismissKeyboard(); latestDragStart(entry.provider, point) },
                                        onDrag = { latestDrag(it) }, onDrop = { latestDrop() }, onCancel = { latestCancelDrag() })
                                }
                            }
                        }
                        if (entries != null && filtered.isEmpty() && builtins.isEmpty()) item("no-widgets", span = full) {
                            Text("No widgets found", Modifier.padding(20.dp), color = PickerMuted)
                        }
                    }
                }
            }
        }
    }
}

/** One app widget: its preview in a grey frame, then name, description and a size chip. */
@Composable
private fun PickerProviderCard(entry: WidgetCatalogEntry, span: WidgetSpan?, onTap: () -> Unit,
    onDragStart: (Offset) -> Unit, onDrag: (Offset) -> Unit, onDrop: () -> Unit, onCancel: () -> Unit) {
    var origin by remember { mutableStateOf(Offset.Zero) }
    val suffix = if (entry.isWork) "-profile-${entry.userSerial}" else ""
    Surface(modifier = Modifier.fillMaxWidth()
        .testTag("widget-provider-${entry.provider.provider.flattenToString()}$suffix")
        .onGloballyPositioned { origin = it.boundsInRoot().topLeft }
        .pointerInput(entry.provider) {
            detectDragGesturesAfterLongPress(
                onDragStart = { point -> onDragStart(origin + point) },
                onDrag = { change, _ -> change.consume(); onDrag(origin + change.position) },
                onDragEnd = onDrop, onDragCancel = onCancel)
        }.clickable(enabled = span != null, onClick = onTap),
        color = PickerCard, contentColor = Color.White, shape = RoundedCornerShape(32.dp)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            val ratio = span?.let { it.width.toFloat() / it.height } ?: 1.5f
            BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                val previewWidth = minOf(maxWidth - 28.dp, 210.dp * ratio)
                val previewHeight = previewWidth / ratio
                // A grey frame around the preview, like a widget sitting on a page.
                Box(Modifier.background(Color(0xFF6C6F73), RoundedCornerShape(30.dp)).padding(14.dp)) {
                    Box(Modifier.width(previewWidth).height(previewHeight).clip(RoundedCornerShape(18.dp))
                        .testTag("widget-preview-${entry.provider.provider.flattenToString()}$suffix")) {
                        WidgetProviderPreview(entry, span ?: WidgetSpan(2, 2), Modifier.fillMaxSize())
                    }
                }
            }
            Row(Modifier.padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(entry.providerLabel, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleLarge,
                    textAlign = TextAlign.Center)
                if (entry.isWork) AssistChip(onClick = {}, label = { Text(entry.profileLabel) }, modifier = Modifier.padding(start = 8.dp))
            }
            if (entry.description.isNotBlank()) Text(entry.description, color = PickerMuted, style = MaterialTheme.typography.bodyLarge,
                maxLines = 2, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 6.dp))
            Surface(Modifier.padding(top = 12.dp), shape = CircleShape, color = Color.White.copy(alpha = .12f),
                contentColor = if (span == null) Color(0xFFFF8A80) else Color.White) {
                Text(span?.let { "${it.width} × ${it.height}" } ?: "Doesn’t fit this layout",
                    Modifier.padding(horizontal = 16.dp, vertical = 8.dp), style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

@Composable
private fun PickerHeader(title: String) {
    Text(title, Modifier.padding(start = 4.dp, top = 12.dp), fontWeight = FontWeight.Bold,
        style = MaterialTheme.typography.headlineSmall, color = Color.White)
}
