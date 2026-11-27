package com.jake.duolauncher

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.border
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

internal enum class CustomizationPage { OVERVIEW, WALLPAPER, THEME, PRESETS, HOME, STAND, GESTURES, BACKUP, HELP }

@Composable
internal fun CustomizationSheet(state: LauncherState, initiallyWide: Boolean, model: LauncherModel,
    isDefaultHome: Boolean, page: CustomizationPage, onPage: (CustomizationPage) -> Unit,
    onMakeDefault: () -> Unit, onClose: () -> Unit, onEditPins: () -> Unit, onWidget: (Int) -> Unit,
    onAddWidget: (Int) -> Unit, onRemoveWidget: (Int) -> Unit, onWallpaperPreview: () -> Unit,
    onExportLayout: () -> Unit, onImportLayout: () -> Unit,
    appearance: AppearanceState, onAppearanceMode: (AppearanceMode) -> Unit,
    onAppearanceManual: (String, Double, Double) -> Unit, onAppearanceDeviceLocation: () -> Unit,
    onAppearanceClear: () -> Unit, backgrounds: LauncherBackgroundController, homePage: Int = 0,
    onShadeSetup: () -> Unit = {},
) {
    var wide by rememberSaveable { mutableStateOf(initiallyWide) }
    val title = when (page) {
        CustomizationPage.OVERVIEW -> "Make it yours"
        CustomizationPage.WALLPAPER -> "Wallpaper & appearance"
        CustomizationPage.THEME -> "Theme & icons"
        CustomizationPage.PRESETS -> "Look presets"
        CustomizationPage.STAND -> "Stand mode"
        CustomizationPage.HOME -> "Home layout"
        CustomizationPage.GESTURES -> "Gestures & search"
        CustomizationPage.BACKUP -> "Backup"
        CustomizationPage.HELP -> "Help & setup"
    }
    val bodyScroll = rememberScrollState()
    LaunchedEffect(page) { bodyScroll.scrollTo(0) }
    Column(Modifier.fillMaxWidth().fillMaxHeight(.92f).padding(horizontal = 20.dp).padding(bottom = 12.dp)) {
        Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
            if (page != CustomizationPage.OVERVIEW) IconButton(onClick = { onPage(CustomizationPage.OVERVIEW) },
                Modifier.testTag("customization-back")) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") }
            Text(title, Modifier.weight(1f), style = MaterialTheme.typography.headlineSmall)
            IconButton(onClick = onClose) { Icon(Icons.Rounded.Close, "Close customization") }
        }
        Column(Modifier.weight(1f).verticalScroll(bodyScroll).padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            when (page) {
                CustomizationPage.OVERVIEW -> {
                    if (!isDefaultHome) Button(onClick = onMakeDefault, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                        .testTag("default-home-settings")) { Text("Set as home app") }
                    if (state.canUndoEdit) OutlinedButton(onClick = { model.undoEdit(); onClose() },
                        Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Undo last layout change") }
                    HomeScreenPreviews(backgrounds.previewBitmap, state)
                    CustomizationDestination(Icons.Rounded.Wallpaper, "Wallpaper & appearance",
                        if (backgrounds.previewPending) "Photo ready to review" else "Background, colors, and light",
                        "customization-wallpaper") { onPage(CustomizationPage.WALLPAPER) }
                    CustomizationDestination(Icons.Rounded.Palette, "Theme & icons",
                        "Icon style, shape, icon packs and colors", "customization-theme") { onPage(CustomizationPage.THEME) }
                    CustomizationDestination(Icons.Rounded.Layers, "Look presets",
                        "Save looks and switch on a schedule", "customization-presets") { onPage(CustomizationPage.PRESETS) }
                    CustomizationDestination(Icons.Rounded.GridView, "Home layout",
                        "Icons, spacing, dock, and widgets", "customization-home") { onPage(CustomizationPage.HOME) }
                    CustomizationDestination(Icons.Rounded.Nightlight, "Stand mode",
                        "Clock and calendar when the phone stands", "customization-stand") { onPage(CustomizationPage.STAND) }
                    CustomizationDestination(Icons.Rounded.Search, "Gestures & search",
                        "Labels, status, and search behavior", "customization-gestures") { onPage(CustomizationPage.GESTURES) }
                    CustomizationDestination(Icons.Rounded.Save, "Backup",
                        "Save or restore this layout", "customization-backup") { onPage(CustomizationPage.BACKUP) }
                    CustomizationDestination(Icons.Rounded.HelpOutline, "Help & setup",
                        "Home app, widgets, gestures, and Discover", "customization-help") {
                        onPage(CustomizationPage.HELP)
                    }
                    if (isDefaultHome) TextButton(onClick = onMakeDefault, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                        .testTag("default-home-settings")) { Text("Change home app") }
                }
                CustomizationPage.WALLPAPER -> WallpaperAppearancePage(state, model, backgrounds, appearance, onAppearanceMode,
                    onAppearanceManual, onAppearanceDeviceLocation, onAppearanceClear, onWallpaperPreview)
                CustomizationPage.THEME -> ThemeSettingsPage()
                CustomizationPage.PRESETS -> LookPresetsPage(state, model, backgrounds)
                CustomizationPage.STAND -> StandSettingsPage()
                CustomizationPage.HOME -> HomeLayoutSettings(state, wide, { wide = it }, model, homePage,
                    onEditPins, onWidget, onAddWidget, onRemoveWidget)
                CustomizationPage.GESTURES -> {
                    SettingsSwitch("Show app names", state.labels, model::setLabels, "label-switch")
                    SettingsSwitch("Show status at upper right", state.verticalStatus, model::setVerticalStatus, "status-switch")
                    Text("Search button: tap to search apps in All apps; touch and hold to open Google. App search always runs on the device.", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    LibraryModeSetting()
                    BadgeSettings()
                    HiddenAppSettings(state.extras.hiddenApps.size)
                    Text("Swipe sideways anywhere on Home to change pages. Swipe down for notifications or quick settings.",
                        style = MaterialTheme.typography.bodyMedium)
                }
                CustomizationPage.BACKUP -> {
                    Text("Save the current Home layout, folders, widgets, and layout settings.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = onExportLayout, Modifier.weight(1f).heightIn(min = 48.dp).testTag("layout-export")) { Text("Save") }
                        Button(onClick = onImportLayout, Modifier.weight(1f).heightIn(min = 48.dp).testTag("layout-import")) { Text("Restore") }
                    }
                    Text("Restore shows a review before changing Home.", style = MaterialTheme.typography.bodySmall)
                    AutoBackupSettingsSection()
                }
                CustomizationPage.HELP -> LauncherHelp(
                    isDefaultHome = isDefaultHome,
                    onHomeSettings = onMakeDefault,
                    onAddWidget = { onAddWidget(homePage) },
                    onShadeSetup = onShadeSetup,
                )
            }
        }
    }
}

@Composable
private fun LauncherHelp(
    isDefaultHome: Boolean,
    onHomeSettings: () -> Unit,
    onAddWidget: () -> Unit,
    onShadeSetup: () -> Unit,
) {
    HelpSection(Icons.Rounded.Home, "Home app",
        if (isDefaultHome) "Duo is your Home app. You can switch launchers in Android’s Home settings."
        else "Choose Duo in Android’s Home settings to use it when you press Home.")
    Button(onClick = onHomeSettings, Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("help-home-settings")) {
        Text(if (isDefaultHome) "Change home app" else "Set Duo as Home")
    }
    HorizontalDivider(Modifier.padding(vertical = 4.dp))
    HelpSection(Icons.Rounded.TouchApp, "Customize any page",
        "Long-press empty space, then choose Customize launcher. If a page is full, long-press the slim area at its left edge.")
    HelpSection(Icons.Rounded.Widgets, "Widgets",
        "Add Android widgets to empty Home cells. Hold a widget to move or remove it.")
    OutlinedButton(onClick = onAddWidget, Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("help-add-widget")) {
        Text("Add widget to this page")
    }
    HorizontalDivider(Modifier.padding(vertical = 4.dp))
    HelpSection(Icons.Rounded.SwipeDown, "Notifications and quick settings",
        "Swipe down on Home. The first time, Duo explains Android’s optional Accessibility setting. The service only opens the system panels.")
    TextButton(onClick = onShadeSetup, Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("help-shade-setup")) {
        Text("Set up shade gestures")
    }
    HelpSection(Icons.Rounded.Explore, "Discover",
        "Swipe right from the first Home page. If Google can’t provide the feed, Duo keeps a Home return and recovery actions available.")
}

@Composable
private fun HelpSection(icon: ImageVector, title: String, detail: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.Top) {
        Icon(icon, null, Modifier.padding(top = 2.dp).size(22.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(detail, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable private fun CustomizationDestination(icon: ImageVector, title: String, detail: String, tag: String, onClick: () -> Unit) {
    Surface(onClick = onClick, modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp).testTag(tag),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .52f), shape = RoundedCornerShape(20.dp)) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary); Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) { Text(title, style = MaterialTheme.typography.titleMedium); Text(detail,
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            Icon(Icons.Rounded.ChevronRight, null)
        }
    }
}

@Composable private fun MiniHomePreview(stagedBitmap: android.graphics.Bitmap?, state: LauncherState,
    previewHeight: androidx.compose.ui.unit.Dp) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val backgroundRevision = LauncherBackgroundCache.revision.intValue
    val committedBitmap = remember(backgroundRevision) { cachedLauncherBackground(context) }
    val bitmap = stagedBitmap ?: committedBitmap
    val apps = remember(state.apps) { state.apps.associateBy { it.id } }
    val homeIcons = state.homeSlots.mapNotNull { id -> id?.let(apps::get) }.take(8)
    val dockIcons = state.dock.mapNotNull { id -> id?.let(apps::get) }.take(5)
    val scale = previewHeight.value * .632f / 250f
    fun unit(value: Float) = (value * scale).dp
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Box(Modifier.height(previewHeight).width(previewHeight * .632f).clip(RoundedCornerShape(unit(24f)))
            .testTag("customization-home-preview")) {
            DuneWallpaper()
            bitmap?.let { Image(it.asImageBitmap(), null, Modifier.matchParentSize(),
                contentScale = androidx.compose.ui.layout.ContentScale.Crop) }
            Column(Modifier.fillMaxSize().padding(start = unit(16f), top = unit(18f), end = unit(54f)),
                verticalArrangement = Arrangement.spacedBy(unit(10f))) {
                Box(Modifier.fillMaxWidth().height(unit(42f)).background(MaterialTheme.colorScheme.surface.copy(alpha = .38f), RoundedCornerShape(unit(12f))))
                homeIcons.chunked(4).forEach { row -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    row.forEach { app -> Image(app.icon.asImageBitmap(), null, Modifier.size(unit(24f)).clip(LocalIconShape.current)) }
                } }
            }
            Column(Modifier.align(Alignment.CenterEnd).padding(end = unit(10f)).width(unit(36f))
                .background(MaterialTheme.colorScheme.surface.copy(alpha = .42f), RoundedCornerShape(unit(18f)))
                .padding(vertical = unit(8f)), horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(unit(8f))) {
                dockIcons.forEach { app -> Image(app.icon.asImageBitmap(), null, Modifier.size(unit(22f)).clip(LocalIconShape.current)) }
            }
        }
    }
}

@Composable private fun HomeLayoutSettings(state: LauncherState, wide: Boolean, onWide: (Boolean) -> Unit,
    model: LauncherModel, homePage: Int, onEditPins: () -> Unit, onWidget: (Int) -> Unit,
    onAddWidget: (Int) -> Unit, onRemoveWidget: (Int) -> Unit) {
    val p = if (wide) state.expanded else state.compact
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(!wide, { onWide(false) }, label = { Text("Cover") })
        FilterChip(wide, { onWide(true) }, label = { Text("Inner") })
    }
    OutlinedButton(onClick = onEditPins, Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Choose Home apps") }
    CustomizationSlider("App icon size", "${p.iconSize.toInt()} dp", p.iconSize, 40f..68f) { model.setPreset(wide, p.copy(iconSize = it)) }
    CustomizationSlider("Space between rows", "${p.rowGap.toInt()} dp", p.rowGap, 0f..28f) { model.setPreset(wide, p.copy(rowGap = it)) }
    CustomizationSlider("Dock width", "${p.dockWidth.toInt()} dp", p.dockWidth, 56f..84f) { model.setPreset(wide, p.copy(dockWidth = it)) }
    if (!wide) {
        Text("Dock position", style = MaterialTheme.typography.titleSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(DockSide.BOTTOM to "Bottom", DockSide.LEFT to "Left", DockSide.RIGHT to "Right").forEach { (side, label) ->
                FilterChip(p.dockSide == side, { model.setPreset(false, p.copy(dockSide = side)) }, label = { Text(label) },
                    modifier = Modifier.testTag("dock-side-${side.name}"))
            }
        }
        Text("The unfolded screen keeps its dock on the right.", style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    SettingsSwitch("Align dock with app rows", p.dockAlignToGrid, { model.setPreset(wide, p.copy(dockAlignToGrid = it)) })
    if (!p.dockAlignToGrid) CustomizationSlider("Dock height on screen", "${(p.dockPosition * 100).toInt()}%", p.dockPosition, .25f.. .75f) { model.setPreset(wide, p.copy(dockPosition = it)) }
    TextButton(onClick = { model.setPreset(wide, LayoutPreset()) }, Modifier.fillMaxWidth()) { Text("Reset this layout") }
    HorizontalDivider(Modifier.padding(vertical = 6.dp))
    Text("Widgets · Page ${homePage + 1}", style = MaterialTheme.typography.titleMedium)
    state.widgetPlacements.filter { it.page == homePage || (wide && it.page == -1) }.forEach { placement ->
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(if (placement.page == -1) "Unfolded-only page" else "${placement.spanX} × ${placement.spanY} widget · row ${placement.row + 1}", Modifier.weight(1f))
            IconButton(onClick = { onRemoveWidget(placement.slot) }, modifier = Modifier.semantics { contentDescription = if (placement.page == -1) "Remove widget from Unfolded-only page" else "Remove widget" }) { Icon(Icons.Rounded.DeleteOutline, null) }
            TextButton(onClick = { onWidget(placement.slot) }) { Text("Replace") }
        }
    }
    TextButton(onClick = { onAddWidget(homePage) }, Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Add widget to this page") }
}

@Composable private fun SettingsSwitch(label: String, checked: Boolean, onChecked: (Boolean) -> Unit, tag: String? = null) {
    Row(Modifier.fillMaxWidth().heightIn(min = 52.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f)); Switch(checked, onChecked, Modifier.then(if (tag != null) Modifier.testTag(tag) else Modifier))
    }
}

@Composable private fun CustomizationSlider(label: String, valueLabel: String, value: Float,
    range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit) {
    Column { Row { Text(label, Modifier.weight(1f)); Text(valueLabel, color = MaterialTheme.colorScheme.primary) }
        Slider(value, onChange, valueRange = range, modifier = Modifier.semantics { contentDescription = label }) }
}

@Composable private fun LibraryModeSetting() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val categories by RecentApps.categoriesMode(context).collectAsState()
    SettingsSwitch("Group All apps by category", categories, { RecentApps.setCategoriesMode(context, it) }, "library-categories-switch")
    Text("Off shows one A–Z list. Searching always shows the A–Z list.", style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable private fun BadgeSettings() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val enabled by BadgeStore.enabled(context).collectAsState()
    var granted by remember { mutableStateOf(BadgeStore.accessGranted(context)) }
    androidx.lifecycle.compose.LifecycleResumeEffect(Unit) { granted = BadgeStore.accessGranted(context); onPauseOrDispose { } }
    HorizontalDivider(Modifier.padding(vertical = 6.dp))
    SettingsSwitch("Notification badges", enabled, { BadgeStore.setEnabled(context, it) }, "badge-switch")
    Text("Only the number of notifications per app is counted. Their content is never read or stored.",
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    if (enabled && !granted) OutlinedButton(onClick = {
        (context as? MainActivity)?.openSystemSettings(BadgeStore.accessSettingsIntent(context), "badge-access")
    }, Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("badge-access")) { Text("Allow notification access") }
}

@Composable private fun HiddenAppSettings(hiddenCount: Int) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val lock by PrivacyStore.lockHidden(context).collectAsState()
    val secure = remember { PrivacyStore.deviceSecure(context) }
    HorizontalDivider(Modifier.padding(vertical = 6.dp))
    Text("Hidden apps", style = MaterialTheme.typography.titleMedium)
    SettingsSwitch("Lock hidden apps", lock && secure, { PrivacyStore.setLockHidden(context, it) }, "lock-hidden-switch")
    Text(if (secure) "$hiddenCount hidden. Showing them in All apps asks for your fingerprint, face or screen lock."
        else "Set a screen lock (PIN, pattern or fingerprint) on this phone to lock hidden apps.",
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable private fun AutoBackupSettingsSection() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val settings by AutoBackupStore.settings(context).collectAsState()
    val files by AutoBackupStore.files.collectAsState()
    HorizontalDivider(Modifier.padding(vertical = 6.dp))
    Text("Automatic backup", style = MaterialTheme.typography.titleMedium)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        AutoBackupPolicy.entries.forEach { policy ->
            FilterChip(settings.policy == policy, { AutoBackupStore.update(context, settings.copy(policy = policy)) },
                label = { Text(policy.label) }, modifier = Modifier.testTag("autobackup-${policy.name}"))
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Keep", Modifier.padding(end = 8.dp))
        listOf(7, 30).forEach { keep ->
            FilterChip(settings.keep == keep, { AutoBackupStore.update(context, settings.copy(keep = keep)) },
                label = { Text("$keep") }, modifier = Modifier.padding(end = 8.dp))
        }
    }
    Text("Saved on this phone when the layout changes. They are removed with the app, so save a file before reinstalling.",
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    if (files.isEmpty()) Text("No automatic backups yet.", style = MaterialTheme.typography.bodyMedium)
    val format = remember { java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.MEDIUM, java.text.DateFormat.SHORT) }
    files.take(10).forEach { backup ->
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(format.format(java.util.Date(backup.savedAt)), Modifier.weight(1f))
            TextButton(onClick = {
                runCatching { backup.file.readText() }.getOrNull()?.let { (context as? MainActivity)?.backups?.previewRaw(it) }
            }, Modifier.testTag("autobackup-preview-${backup.savedAt}")) { Text("Preview") }
        }
    }
}

@Composable private fun StandSettingsPage() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val settings by StandStore.load(context).collectAsState()
    val monitor = remember { PostureMonitor(context) { isCharging(context) } }
    androidx.lifecycle.compose.LifecycleStartEffect(monitor) { monitor.start(); onStopOrDispose { monitor.stop() } }
    val reading by monitor.reading.collectAsState()
    fun update(transform: (StandSettings) -> StandSettings) = StandStore.update(context, transform)
    if (!monitor.available) Text("This phone has no hinge sensor, so Stand mode is for foldables only.",
        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Text("Shows a large clock, calendar, next alarm and battery when a foldable stands half-folded, or folded on its side while charging. Back closes it until you change position.",
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Text("When it turns on", style = MaterialTheme.typography.titleSmall)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        StandTrigger.entries.forEach { trigger ->
            FilterChip(settings.trigger == trigger, { update { it.copy(trigger = trigger) } }, label = { Text(trigger.label) },
                modifier = Modifier.testTag("stand-trigger-${trigger.name}"))
        }
    }
    Text("Positions", style = MaterialTheme.typography.titleSmall)
    listOf(StandPosture.BOOK, StandPosture.LAPTOP, StandPosture.COVER_SIDEWAYS, StandPosture.OPEN_STANDING).forEach { posture ->
        SettingsSwitch(posture.label + if (posture == StandPosture.COVER_SIDEWAYS || posture == StandPosture.OPEN_STANDING) " (charging)" else "",
            posture in settings.postures, { on -> update { s ->
                val next = if (on) s.postures + posture else s.postures - posture
                s.copy(postures = next.ifEmpty { s.postures })
            } }, "stand-posture-${posture.name}")
    }
    SettingsSwitch("Close when unplugged", settings.closeWhenUnplugged, { on -> update { it.copy(closeWhenUnplugged = on) } }, "stand-close-unplugged")
    SettingsSwitch("Dim to red when the room is dark", settings.dimAtNight, { on -> update { it.copy(dimAtNight = on) } }, "stand-night")
    SettingsSwitch("Keep the screen on while charging", settings.keepScreenOn, { on -> update { it.copy(keepScreenOn = on) } }, "stand-keep-on")
    HorizontalDivider(Modifier.padding(vertical = 6.dp))
    Text("Right now", style = MaterialTheme.typography.titleSmall)
    Text("Position: ${classifyPosture(reading).label} · hinge ${reading.hingeAngle?.let { "${it.toInt()}°" } ?: "n/a"}\n" +
        "Charging: ${if (reading.charging) "yes" else "no"} · room ${reading.lux?.let { "${it.toInt()} lx" } ?: "n/a"}",
        style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag("stand-diagnostics"))
}

/**
 * The settings overview's header: the folded (cover) and unfolded (inner) Home screens side by side,
 * drawn from the live layout over the current background. Phones without a hinge show one screen.
 */
@Composable internal fun HomeScreenPreviews(stagedBitmap: android.graphics.Bitmap?, state: LauncherState) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val backgroundRevision = LauncherBackgroundCache.revision.intValue
    val committed = remember(backgroundRevision) { cachedLauncherBackground(context) }
    val bitmap = stagedBitmap ?: committed
    val wallpaper by WallpaperStore.load(context).collectAsState()
    val device = WallpaperStore.device(context)
    val foldable = device.foldable
    val coverRevision = CoverBackground.revision.intValue
    val coverBitmap = if (wallpaper.coverPhoto) produceState(CoverBackground.peek(), coverRevision) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { CoverBackground.load(context.applicationContext) }
    }.value else null
    val apps = remember(state.apps, state.shortcuts) { (state.apps + state.shortcuts).associateBy { it.id } }
    val height = 196.dp
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp).testTag("customization-home-preview"),
        horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally)) {
        PreviewScreen(if (foldable) "Cover screen" else "Home screen", coverBitmap ?: bitmap, state, apps, height * device.coverAspect, height,
            effectiveDockSide(state.compact, expanded = false), halves = false, tag = "preview-cover")
        if (foldable) PreviewScreen("Inner screen", bitmap, state, apps, height * .96f, height,
            DockSide.RIGHT, halves = true, tag = "preview-inner")
    }
}

@Composable private fun PreviewScreen(label: String, bitmap: android.graphics.Bitmap?, state: LauncherState,
    apps: Map<String, AppEntry>, width: androidx.compose.ui.unit.Dp, height: androidx.compose.ui.unit.Dp,
    side: DockSide, halves: Boolean, tag: String) {
    val layout = state.layout
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(width, height).clip(RoundedCornerShape(14.dp)).testTag(tag)) {
            DuneWallpaper()
            bitmap?.let { Image(it.asImageBitmap(), null, Modifier.matchParentSize(),
                contentScale = androidx.compose.ui.layout.ContentScale.Crop) }
            // Each half of the unfolded screen is about as wide as the cover screen.
            val pane = if (halves) width / 2 else width
            val dockColumn = if (side == DockSide.BOTTOM) 0.dp else pane * .17f
            val grid = pane - dockColumn - pane * .1f
            val cell = grid / GRID_COLUMNS
            val top = height * .17f
            @Composable fun page(page: Int, left: androidx.compose.ui.unit.Dp) {
                layout.widgetPlacements.filter { it.page == page && it.row < GRID_ROWS }.forEach { w ->
                    Box(Modifier.offset(left + cell * w.column + 1.dp, top + cell * w.row + 1.dp)
                        .size(cell * w.spanX - 2.dp, cell * minOf(w.spanY, GRID_ROWS - w.row) - 2.dp)
                        .background(Color.Black.copy(alpha = .38f), RoundedCornerShape(cell * .28f)))
                }
                repeat(HOME_CELLS) { local ->
                    val id = layout.slotAt(homeCellIndex(page, local)) ?: return@repeat
                    val position = Modifier.offset(left + cell * (local % GRID_COLUMNS) + cell * .14f,
                        top + cell * (local / GRID_COLUMNS) + cell * .14f).size(cell * .72f)
                    val app = apps[id]
                    if (app != null) Image(app.icon.asImageBitmap(), null, position.clip(LocalIconShape.current))
                    else if (layout.folder(id) != null) Box(position.background(Color.White.copy(alpha = .45f), RoundedCornerShape(cell * .2f)))
                }
            }
            val gridLeft = if (side == DockSide.LEFT) dockColumn + pane * .05f else pane * .05f
            if (halves) page(-1, gridLeft)
            page(0, (if (halves) pane else 0.dp) + gridLeft)
            val dock = state.dock.mapNotNull { it?.let(apps::get) }.take(4)
            val dockIcon = if (side == DockSide.BOTTOM) cell * .66f else dockColumn * .62f
            if (dock.isNotEmpty()) {
                val shape = RoundedCornerShape(dockIcon * .5f)
                val glass = Modifier.background(Color.White.copy(alpha = .22f), shape)
                    .border(.5.dp, Color.White.copy(alpha = .4f), shape)
                if (side == DockSide.BOTTOM) Row(Modifier.align(Alignment.BottomCenter).padding(bottom = height * .07f)
                    .then(glass).padding(dockIcon * .18f), horizontalArrangement = Arrangement.spacedBy(dockIcon * .3f)) {
                    dock.forEach { Image(it.icon.asImageBitmap(), null, Modifier.size(dockIcon).clip(LocalIconShape.current)) }
                } else Column(Modifier.align(if (side == DockSide.LEFT) Alignment.TopStart else Alignment.TopEnd)
                    .padding(start = if (side == DockSide.LEFT) pane * .03f else 0.dp, end = if (side == DockSide.LEFT) 0.dp else pane * .03f)
                    .offset(y = top + cell * 2.1f).then(glass).padding(dockIcon * .18f),
                    verticalArrangement = Arrangement.spacedBy(dockIcon * .3f)) {
                    dock.forEach { Image(it.icon.asImageBitmap(), null, Modifier.size(dockIcon).clip(LocalIconShape.current)) }
                }
            }
        }
        Text(label, Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
