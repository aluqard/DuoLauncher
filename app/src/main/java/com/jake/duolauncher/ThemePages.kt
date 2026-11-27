package com.jake.duolauncher

import android.app.TimePickerDialog
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ThemeSettingsPage() {
    val context = LocalContext.current
    val theme by ThemeStore.load(context).collectAsState()
    val packs = remember { installedIconPacks(context) }
    fun update(transform: (ThemeSettings) -> ThemeSettings) = ThemeStore.update(context, transform)
    Text("Icon style", style = MaterialTheme.typography.titleMedium)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(theme.iconStyle == IconStyle.ORIGINAL, { update { it.copy(iconStyle = IconStyle.ORIGINAL) } },
            label = { Text("Original") }, modifier = Modifier.testTag("icon-style-original"))
        FilterChip(theme.iconStyle == IconStyle.GLYPH, { update { it.copy(iconStyle = IconStyle.GLYPH, iconShape = IconShape.SQUIRCLE) } },
            label = { Text("Uniform tiles") }, modifier = Modifier.testTag("icon-style-glyph"))
    }
    Text("Uniform tiles draw every app's own icon as a full continuous-corner tile; older icons sit on a plate.",
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Text("Shape", style = MaterialTheme.typography.titleSmall)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(IconShape.ROUNDED_SQUARE to "Rounded square", IconShape.SQUIRCLE to "Squircle", IconShape.CIRCLE to "Circle").forEach { (shape, label) ->
            FilterChip(theme.iconShape == shape, { update { it.copy(iconShape = shape) } }, label = { Text(label) },
                modifier = Modifier.testTag("icon-shape-${shape.name}"))
        }
    }
    Text("Tone", style = MaterialTheme.typography.titleSmall)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(IconTone.LIGHT to "Color", IconTone.DARK to "Dark", IconTone.TINTED to "Tinted").forEach { (tone, label) ->
            FilterChip(theme.iconTone == tone, { update { it.copy(iconTone = tone) } }, label = { Text(label) },
                modifier = Modifier.testTag("icon-tone-${tone.name}"))
        }
    }
    Row(Modifier.fillMaxWidth().heightIn(min = 52.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("Dark icons at night", Modifier.weight(1f))
        Switch(theme.darkIconsAtNight, { on -> update { it.copy(darkIconsAtNight = on) } }, Modifier.testTag("dark-icons-switch"))
    }
    HorizontalDivider(Modifier.padding(vertical = 6.dp))
    Text("Icon pack", style = MaterialTheme.typography.titleMedium)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(theme.iconPack == null, { update { it.copy(iconPack = null) } }, label = { Text("None") })
        packs.forEach { pack ->
            FilterChip(theme.iconPack == pack.packageName, { update { it.copy(iconPack = pack.packageName) } }, label = { Text(pack.label) },
                modifier = Modifier.testTag("icon-pack-${pack.packageName}"))
        }
    }
    if (packs.isEmpty()) Text("No icon packs installed. Packs made for Nova, ADW or similar launchers work here.",
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    HorizontalDivider(Modifier.padding(vertical = 6.dp))
    Text("Colors", style = MaterialTheme.typography.titleMedium)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        COLOR_THEMES.forEach { color ->
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { update { it.copy(colorTheme = color.id) } }
                .testTag("color-theme-${color.id}")) {
                Box(Modifier.size(40.dp).clip(CircleShape).background(Color(color.glass))
                    .border(if (theme.colorTheme == color.id) 3.dp else 1.dp, if (theme.colorTheme == color.id) MaterialTheme.colorScheme.primary
                        else Color.White.copy(alpha = .5f), CircleShape), contentAlignment = Alignment.Center) {
                    Box(Modifier.size(16.dp).clip(CircleShape).background(Color(color.accent)))
                }
                Text(color.name, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
    Text("Accent", style = MaterialTheme.typography.titleSmall)
    val swatches = listOf(0xFF4C8DFF, 0xFF34C759, 0xFFFF9F0A, 0xFFFF375F, 0xFFAF52DE, 0xFF5AC8FA, 0xFFFFD60A, 0xFF8E8E93).map { it.toInt() }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        swatches.forEach { argb ->
            Box(Modifier.size(32.dp).clip(CircleShape).background(Color(argb))
                .border(if (theme.colorTheme == "custom" && theme.customAccent == argb) 3.dp else 0.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                .clickable { update { it.copy(colorTheme = "custom", customAccent = argb, customGlass = it.glass()) } })
        }
    }
    Column {
        Row { Text("Glass opacity", Modifier.weight(1f)); Text("${(theme.glassOpacity * 100).toInt()}%", color = MaterialTheme.colorScheme.primary) }
        Slider(theme.glassOpacity, { value -> update { it.copy(glassOpacity = value) } }, valueRange = .2f..1f,
            modifier = Modifier.testTag("glass-opacity"))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun LookPresetsPage(state: LauncherState, model: LauncherModel, backgrounds: LauncherBackgroundController) {
    val context = LocalContext.current
    val presets by LookPresetStore.presets(context).collectAsState()
    var naming by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    Text("Save the wallpaper, icon style, colors and sizes as a look, then switch between looks or let them change on a schedule.",
        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Button({ name = "Look ${presets.size + 1}"; naming = true }, Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("preset-save")) {
        Text("Save current look") }
    presets.forEach { preset ->
        Surface(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .5f), shape = RoundedCornerShape(18.dp),
            modifier = Modifier.fillMaxWidth().testTag("preset-${preset.id}")) {
            Column(Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(preset.name, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                    TextButton({ applyLookPreset(context, preset, model, backgrounds) }, Modifier.testTag("preset-apply-${preset.id}")) { Text("Apply") }
                    TextButton({ LookPresetStore.delete(context, preset.id) }) { Text("Delete") }
                }
                Text(buildString {
                    append(if (preset.hasPhoto) "Photo" else "Dunes"); append(" · ")
                    append(if (preset.theme.iconStyle == IconStyle.GLYPH) "Uniform tiles" else "Original icons")
                    preset.theme.iconPack?.let { append(" · pack") }
                }, style = MaterialTheme.typography.bodySmall)
                preset.schedules.forEachIndexed { index, schedule ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("%s · %02d:%02d".format(schedule.days.sorted().joinToString(" ") { dayLabel(it) },
                            schedule.minuteOfDay / 60, schedule.minuteOfDay % 60), Modifier.weight(1f))
                        TextButton({ LookPresetStore.put(context, preset.copy(schedules = preset.schedules.filterIndexed { i, _ -> i != index })) }) { Text("Remove") }
                    }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Weekdays" to WEEKDAYS, "Weekends" to WEEKEND, "Every day" to (1..7).toSet()).forEach { (label, days) ->
                        AssistChip(onClick = {
                            TimePickerDialog(context, { _, hour, minute ->
                                LookPresetStore.put(context, preset.copy(schedules = preset.schedules + PresetSchedule(days, hour * 60 + minute)))
                            }, 7, 0, android.text.format.DateFormat.is24HourFormat(context)).show()
                        }, label = { Text("+ $label") })
                    }
                }
            }
        }
    }
    if (presets.any { it.schedules.isNotEmpty() }) Text("Scheduled looks switch at the chosen times. Changing the look yourself lasts until the next scheduled time.",
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    if (naming) AlertDialog(onDismissRequest = { naming = false }, title = { Text("Name this look") },
        text = { OutlinedTextField(name, { name = it.take(30) }, singleLine = true, modifier = Modifier.testTag("preset-name")) },
        confirmButton = { TextButton({
            LookPresetStore.put(context, captureLookPreset(context, name.trim(), state, backgrounds)); naming = false
        }, Modifier.testTag("preset-name-save")) { Text("Save") } },
        dismissButton = { TextButton({ naming = false }) { Text("Cancel") } })
}

/** Lets the user pick an icon for one app from the selected (or any installed) icon pack. */
@Composable
internal fun IconChoiceSection(app: AppEntry, current: String?, onChoose: (String?) -> Unit) {
    val context = LocalContext.current
    val theme by ThemeStore.load(context).collectAsState()
    val packs = remember { installedIconPacks(context) }
    var packName by remember { mutableStateOf(theme.iconPack ?: packs.firstOrNull()?.packageName) }
    var query by remember { mutableStateOf(app.originalLabel.lowercase().filter(Char::isLetterOrDigit).take(8)) }
    Text("Icon", style = MaterialTheme.typography.titleSmall)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(current == null, { onChoose(null) }, label = { Text("Theme default") })
        FilterChip(current == "original", { onChoose("original") }, label = { Text("App's own") })
    }
    if (packs.isEmpty()) return
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        packs.forEach { pack -> FilterChip(packName == pack.packageName, { packName = pack.packageName }, label = { Text(pack.label) }) }
    }
    OutlinedTextField(query, { query = it }, label = { Text("Search pack icons") }, singleLine = true, modifier = Modifier.fillMaxWidth())
    val icons by produceState<List<Pair<String, android.graphics.Bitmap>>>(emptyList(), packName, query) {
        value = withContext(Dispatchers.IO) {
            val pack = IconPacks.load(context, packName) ?: return@withContext emptyList()
            pack.drawableNames.filter { it.contains(query.trim(), true) }.take(40).mapNotNull { name ->
                pack.drawableNamed(name)?.let { name to it.toBitmap(96, 96) }
            }
        }
    }
    LazyVerticalGrid(GridCells.Adaptive(56.dp), Modifier.heightIn(max = 220.dp)) {
        items(icons, key = { it.first }) { (name, bitmap) ->
            val ref = "pack:$packName/$name"
            Image(bitmap.asImageBitmap(), name, Modifier.padding(4.dp).size(48.dp).clip(RoundedCornerShape(12.dp))
                .border(if (current == ref) 3.dp else 0.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp))
                .clickable { onChoose(ref) })
        }
    }
}
