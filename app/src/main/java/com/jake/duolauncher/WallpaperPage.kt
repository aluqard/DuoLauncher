package com.jake.duolauncher

import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Uppercase caption above a settings group. */
@Composable
internal fun GroupHeader(text: String) {
    Text(text.uppercase(), Modifier.padding(start = 12.dp, top = 14.dp, bottom = 2.dp), fontSize = 12.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant, letterSpacing = .6.sp)
}

/** Small explanation under a settings group. */
@Composable
internal fun GroupFooter(text: String) {
    Text(text, Modifier.padding(horizontal = 12.dp), style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** Rounded card holding rows separated by thin lines. */
@Composable
internal fun SettingsGroup(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh),
        content = content)
}

@Composable
private fun GroupDivider() = HorizontalDivider(Modifier.padding(start = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .5f))

/** A tappable row: blue for actions, plain with an optional value and chevron for pickers. */
@Composable
private fun GroupRow(label: String, tag: String, action: Boolean = false, value: String? = null, chevron: Boolean = false,
    checked: Boolean = false, enabled: Boolean = true, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(enabled = enabled, onClick = onClick).heightIn(min = 50.dp)
        .padding(horizontal = 16.dp, vertical = 12.dp).testTag(tag), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), fontSize = 17.sp,
            color = when { !enabled -> MaterialTheme.colorScheme.onSurfaceVariant; action -> Color(0xFF2F8CFF); else -> MaterialTheme.colorScheme.onSurface })
        if (value != null) Text(value, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 15.sp)
        if (chevron) Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        if (checked) Icon(Icons.Rounded.Check, "Selected", tint = Color(0xFF2F8CFF), modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun GroupSwitch(label: String, checked: Boolean, tag: String, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onChange(!checked) }.heightIn(min = 50.dp).padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), fontSize = 17.sp)
        Switch(checked, onChange, Modifier.testTag(tag), colors = SwitchDefaults.colors(checkedTrackColor = Color(0xFF34C759),
            checkedThumbColor = Color.White, checkedBorderColor = Color.Transparent))
    }
}

/** A selectable tile showing four sample icons, used for icon shape and icon design. */
@Composable
private fun IconChoiceTile(label: String, selected: Boolean, tag: String, icons: List<Bitmap?>, modifier: Modifier, onClick: () -> Unit) {
    val accent = Color(0xFF2F8CFF)
    Column(modifier.clip(RoundedCornerShape(14.dp))
        .background(if (selected) accent.copy(alpha = .22f) else MaterialTheme.colorScheme.surfaceContainerHighest)
        .border(if (selected) 1.5.dp else 0.dp, if (selected) accent else Color.Transparent, RoundedCornerShape(14.dp))
        .clickable(onClick = onClick).padding(vertical = 12.dp).testTag(tag), horizontalAlignment = Alignment.CenterHorizontally) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            icons.chunked(2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    row.forEach { bitmap ->
                        if (bitmap != null) Image(bitmap.asImageBitmap(), null, Modifier.size(22.dp))
                        else Box(Modifier.size(22.dp).border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(6.dp)))
                    }
                }
            }
        }
        Text(label, Modifier.padding(top = 8.dp, start = 4.dp, end = 4.dp), fontSize = 13.sp, maxLines = 1,
            color = if (selected) accent else MaterialTheme.colorScheme.onSurface, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
    }
}

private val SHAPE_CHOICES = listOf(IconShape.SYSTEM to "System", IconShape.ROUNDED_SQUARE to "Rounded square",
    IconShape.SQUIRCLE to "Squircle", IconShape.CIRCLE to "Circle")

/** Renders up to four of the user's Home apps with [theme], for the shape and design tiles. */
@Composable
private fun sampleIcons(state: LauncherState, theme: ThemeSettings, dark: Boolean): List<Bitmap?> {
    val context = LocalContext.current
    val sample = remember(state.apps, state.homeSlots) {
        val byId = state.apps.associateBy { it.id }
        (state.homeSlots.mapNotNull { it?.let(byId::get) } + state.apps).distinctBy { it.id }.filter { it.shortcutId == null }.take(4)
    }
    return produceState(List<Bitmap?>(4) { null }, sample, theme, dark) {
        value = withContext(Dispatchers.Default) {
            val launcherApps = context.getSystemService(android.content.pm.LauncherApps::class.java)
            sample.map { app ->
                runCatching {
                    val info = launcherApps.resolveActivity(android.content.Intent().setComponent(app.component), app.user)
                    IconRenderer.render(info.getIcon(0), theme, dark)
                }.getOrNull()
            } + List(maxOf(0, 4 - sample.size)) { null }
        }
    }.value
}

/**
 * Wallpaper & appearance: screen previews, the launcher background (with an optional folded-screen
 * photo), app icon shape and design, background motion, glass shadow, light/dark appearance,
 * the device the launcher fits itself to, and the Android wallpaper.
 */
@Composable
internal fun WallpaperAppearancePage(state: LauncherState, model: LauncherModel, backgrounds: LauncherBackgroundController,
    appearance: AppearanceState, onAppearanceMode: (AppearanceMode) -> Unit, onAppearanceManual: (String, Double, Double) -> Unit,
    onAppearanceDeviceLocation: () -> Unit, onAppearanceClear: () -> Unit, onWallpaperPreview: () -> Unit) {
    val context = LocalContext.current
    val activity = context as? MainActivity
    val scope = rememberCoroutineScope()
    val settings by WallpaperStore.load(context).collectAsState()
    val theme by ThemeStore.load(context).collectAsState()
    val device = WallpaperStore.device(context)
    val detected = remember { WallpaperStore.detected(context) }
    var coverMessage by remember { mutableStateOf<String?>(null) }
    var choosePack by remember { mutableStateOf(false) }
    var chooseDevice by remember { mutableStateOf(false) }
    var refreshed by remember { mutableStateOf(false) }
    val coverRevision = CoverBackground.revision.intValue
    val hasCover = remember(coverRevision) { CoverBackground.exists(context) }
    val coverPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        activity?.let { LiveDiscover.setExternalResultPending(it, "main", "cover-photo", false) }
        if (uri != null) scope.launch {
            coverMessage = withContext(Dispatchers.IO) { CoverBackground.save(context.applicationContext, uri) }
                .fold({ "Folded screen photo updated." }, { it.message ?: "That photo could not be used." })
        }
    }
    fun updateTheme(transform: (ThemeSettings) -> ThemeSettings) = ThemeStore.update(context, transform)

    HomeScreenPreviews(backgrounds.previewBitmap, state)

    GroupHeader("Launcher background")
    SettingsGroup {
        GroupRow(if (backgrounds.previewPending) "Choose a different photo" else "Choose photo", "background-choose", action = true,
            enabled = !backgrounds.loading, onClick = backgrounds::choosePhoto)
        if (backgrounds.previewPending) {
            GroupDivider()
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = backgrounds::cancelPreview, Modifier.weight(1f).heightIn(min = 44.dp)
                    .testTag("background-preview-cancel")) { Text("Cancel") }
                Button(onClick = backgrounds::applyPreview, enabled = backgrounds.previewBitmap != null,
                    modifier = Modifier.weight(1f).heightIn(min = 44.dp).testTag("background-preview-apply")) { Text("Apply") }
            }
        }
        if (backgrounds.photoSelected && !backgrounds.previewPending) {
            GroupDivider()
            GroupRow("Use Duo dunes", "background-reset", action = true, onClick = backgrounds::reset)
        }
        if (device.foldable) {
            GroupDivider()
            GroupSwitch("Different photo when folded", settings.coverPhoto, "background-cover-switch") { on ->
                WallpaperStore.update(context) { it.copy(coverPhoto = on) }
            }
            if (settings.coverPhoto) {
                GroupDivider()
                GroupRow(if (hasCover) "Choose a different folded photo" else "Choose folded photo", "background-cover-choose", action = true) {
                    activity?.let { LiveDiscover.setExternalResultPending(it, "main", "cover-photo", true) }
                    coverPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }
                if (hasCover) {
                    GroupDivider()
                    GroupRow("Remove folded photo", "background-cover-remove", action = true) { CoverBackground.clear(context) }
                }
            }
        }
    }
    if (backgrounds.loading) LinearProgressIndicator(Modifier.fillMaxWidth().testTag("background-loading"))
    (backgrounds.errorMessage ?: backgrounds.successMessage ?: coverMessage)?.let { message ->
        TextButton(onClick = { backgrounds.clearMessage(); coverMessage = null }, Modifier.fillMaxWidth().testTag("background-message")) { Text(message) }
    }
    GroupFooter(if (device.foldable && settings.coverPhoto) "The folded screen shows its own photo; the unfolded screen keeps the main one."
        else "The photo behind the launcher Home Screen.")

    GroupHeader("App icons")
    SettingsGroup {
        Text("Shape", Modifier.padding(start = 16.dp, top = 12.dp), fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SHAPE_CHOICES.forEach { (shape, label) ->
                IconChoiceTile(label, theme.iconShape == shape, "icon-shape-${shape.name}",
                    sampleIcons(state, theme.copy(iconShape = shape), appearance.dark), Modifier.weight(1f)) {
                    updateTheme { it.copy(iconShape = shape) }
                }
            }
        }
        GroupDivider()
        Text("Icon design", Modifier.padding(start = 16.dp, top = 12.dp), fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            IconChoiceTile("Original icons", theme.iconPack == null && theme.iconStyle == IconStyle.ORIGINAL, "icon-design-original",
                sampleIcons(state, theme.copy(iconStyle = IconStyle.ORIGINAL, iconPack = null), appearance.dark), Modifier.weight(1f)) {
                updateTheme { it.copy(iconStyle = IconStyle.ORIGINAL, iconPack = null) }
            }
            IconChoiceTile("Uniform tiles", theme.iconPack == null && theme.iconStyle == IconStyle.GLYPH, "icon-design-tiles",
                sampleIcons(state, theme.copy(iconStyle = IconStyle.GLYPH, iconPack = null), appearance.dark), Modifier.weight(1f)) {
                updateTheme { it.copy(iconStyle = IconStyle.GLYPH, iconPack = null) }
            }
            IconChoiceTile(theme.iconPack?.let { pkg -> installedIconPacks(context).firstOrNull { it.packageName == pkg }?.label } ?: "Icon pack",
                theme.iconPack != null, "icon-design-pack", List(4) { null }, Modifier.weight(1f)) { choosePack = true }
        }
    }
    GroupFooter("Shape gives every app icon the same outline.")
    SettingsGroup {
        GroupRow("Refresh icons", "icons-refresh", action = true) { model.refreshIcons(); refreshed = true }
    }
    GroupFooter(if (refreshed) "Icons were redrawn." else "If icons stay the same after changing a system theme or icon pack, tap here.")

    GroupHeader("Motion")
    SettingsGroup {
        GroupSwitch("Move background when tilted", settings.motion, "background-motion-switch") { on ->
            WallpaperStore.update(context) { it.copy(motion = on) }
        }
    }
    GroupFooter("Tilt the phone and the background shifts slightly the other way so icons seem to float. Turn it off to stop using the motion sensor and save battery.")

    GroupHeader("Glass")
    SettingsGroup {
        GroupSwitch("Glass shadow", settings.glassShadow, "glass-shadow-switch") { on ->
            WallpaperStore.update(context) { it.copy(glassShadow = on) }
        }
    }
    GroupFooter("Adds a soft shadow outside glass panels such as the dock, search button and edit buttons. Turn it on to lift the glass on a dark background.")

    GroupHeader("Appearance")
    AppearanceSettings(appearance, onAppearanceMode, onAppearanceManual, onAppearanceDeviceLocation, onAppearanceClear)

    GroupHeader("Device fit")
    SettingsGroup {
        GroupRow("Device", "device-fit", value = device.label + if (settings.device == null && detected != null) " · Auto" else "",
            chevron = true) { chooseDevice = true }
    }
    GroupFooter(when {
        settings.device != null -> "Previews and foldable features follow the device you chose."
        detected == DeviceFit.PHONE -> "Detected a regular phone. If it's a foldable, choose your model."
        detected != null -> "Detected ${detected.label}."
        else -> "Couldn't detect your device. Choose it so the previews and foldable features match it."
    })

    GroupHeader("Android wallpaper")
    SettingsGroup {
        GroupRow("Preview Android wallpaper", "wallpaper-preview", action = true, onClick = onWallpaperPreview)
    }
    GroupFooter("Changes the phone's own wallpaper, separate from the launcher background.")

    if (chooseDevice) AlertDialog(onDismissRequest = { chooseDevice = false }, title = { Text("Device") },
        text = {
            Column {
                DeviceFit.entries.forEachIndexed { index, fit ->
                    if (index > 0) HorizontalDivider()
                    GroupRow(fit.label, "device-fit-${fit.name}", checked = device == fit) {
                        WallpaperStore.update(context) { it.copy(device = if (fit == detected) null else fit) }
                        chooseDevice = false
                    }
                }
            }
        },
        confirmButton = { Button({ chooseDevice = false }, Modifier.fillMaxWidth()) { Text("Close") } })

    if (choosePack) {
        val packs = remember { installedIconPacks(context) }
        AlertDialog(onDismissRequest = { choosePack = false }, title = { Text("Icon pack") },
            text = {
                Column {
                    if (packs.isEmpty()) Text("No icon packs are installed. Icon packs made for Nova, ADW or similar launchers appear here once installed.")
                    packs.forEachIndexed { index, pack ->
                        if (index > 0) HorizontalDivider()
                        GroupRow(pack.label, "icon-pack-${pack.packageName}", checked = theme.iconPack == pack.packageName) {
                            updateTheme { it.copy(iconPack = pack.packageName) }; choosePack = false
                        }
                    }
                }
            },
            confirmButton = { Button({ choosePack = false }, Modifier.fillMaxWidth()) { Text("Close") } })
    }
}
