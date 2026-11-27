package com.jake.duolauncher

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Home edit mode, entered by long-pressing empty Home space. [ids] is null outside edit mode and
 * holds the selected app and folder ids inside it. Shared so tiles can draw their selection circle.
 */
object SelectionMode {
    var ids by mutableStateOf<Set<String>?>(null)
    var sizePanel by mutableStateOf(false)
    val active get() = ids != null
    fun enter() { if (ids == null) ids = emptySet() }
    fun exit() { ids = null; sizePanel = false }
    fun toggle(id: String) { ids = ids?.let { if (id in it) it - id else it + id } }
}

internal val EditBlue = Color(0xFF1E88FF)
private val EditGlass = Color(0xFF1B2029)
private val EditPanel = Color(0xFF15181E)

/** Selection circle at an app or folder icon's upper right while editing: empty until selected, then a blue check. */
@Composable
internal fun SelectionMark(id: String, modifier: Modifier = Modifier) {
    val ids = SelectionMode.ids ?: return
    val selected = id in ids
    Box(modifier.size(22.dp).background(if (selected) EditBlue else Color.Black.copy(alpha = .35f), CircleShape)
        .border(1.5.dp, Color.White.copy(alpha = if (selected) 1f else .85f), CircleShape)
        .testTag(if (selected) "selected-$id" else "selectable-$id"), contentAlignment = Alignment.Center) {
        if (selected) Icon(Icons.Rounded.Check, null, tint = Color.White, modifier = Modifier.size(14.dp))
    }
}

@Composable
private fun EditPill(label: String?, tag: String, onClick: () -> Unit, icon: ImageVector? = null, description: String? = null,
    filled: Boolean = false, highlighted: Boolean = false) {
    val content = when { filled -> Color(0xFF14171C); highlighted -> Color(0xFF7DBBFF); else -> Color.White }
    Surface(onClick = onClick, modifier = Modifier.outerShadow(glassShadow(), CircleShape).height(44.dp).then(if (label == null) Modifier.width(44.dp) else Modifier).testTag(tag)
        .then(if (description != null) Modifier.semantics { contentDescription = description } else Modifier),
        shape = CircleShape, color = when { filled -> Color.White; highlighted -> EditBlue.copy(alpha = .32f); else -> EditGlass.copy(alpha = .72f) },
        contentColor = content, border = BorderStroke(1.dp, if (highlighted) EditBlue else Color.White.copy(alpha = .32f))) {
        if (label == null) Box(contentAlignment = Alignment.Center) { if (icon != null) Icon(icon, null, Modifier.size(20.dp)) }
        else Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) { Icon(icon, null, Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)) }
            Text(label, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
        }
    }
}

@Composable
private fun EditCircle(icon: ImageVector, description: String, tag: String, onClick: () -> Unit) {
    Surface(onClick = onClick, modifier = Modifier.outerShadow(glassShadow(), CircleShape).size(44.dp).testTag(tag), shape = CircleShape,
        color = EditGlass.copy(alpha = .72f), contentColor = Color.White, border = BorderStroke(1.dp, Color.White.copy(alpha = .32f))) {
        Box(contentAlignment = Alignment.Center) { Icon(icon, description, Modifier.size(20.dp)) }
    }
}

/** Edit mode's top bar: Widgets, Settings, Look presets, Undo (after a layout change), Size and Done. */
@Composable
internal fun EditToolbar(canUndo: Boolean, onWidgets: () -> Unit, onSettings: () -> Unit, onPresets: () -> Unit,
    onUndo: () -> Unit, modifier: Modifier = Modifier) {
    // On phone widths Widgets and Size become round icon buttons so every control fits on one line.
    BoxWithConstraints(modifier.fillMaxWidth()) {
    val compact = maxWidth < 520.dp
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp).testTag("edit-toolbar"), verticalAlignment = Alignment.CenterVertically) {
        Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically) {
            EditPill(if (compact) null else "Widgets", "edit-widgets", onWidgets, Icons.Rounded.Add, "Add widget")
            EditPill("Settings", "edit-settings", onSettings, Icons.Rounded.Tune, "Wallpaper & launcher settings", filled = true)
            EditCircle(Icons.Rounded.Layers, "Look presets", "edit-presets", onPresets)
            if (canUndo) EditCircle(Icons.AutoMirrored.Rounded.Undo, "Undo last layout change", "edit-undo", onUndo)
            EditPill(if (compact) null else "Size", "edit-size", { SelectionMode.sizePanel = !SelectionMode.sizePanel }, Icons.Rounded.Straighten,
                "Adjust spacing and size", highlighted = SelectionMode.sizePanel)
        }
        Spacer(Modifier.width(8.dp))
        EditPill("Done", "edit-done", { SelectionMode.exit() })
    }
    }
}

/** The pill that replaces the page dots while editing with nothing selected. */
@Composable
internal fun EditPagesButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(onClick = onClick, modifier = modifier.height(40.dp).testTag("edit-pages"), shape = CircleShape,
        color = EditGlass.copy(alpha = .72f), contentColor = Color.White, border = BorderStroke(1.dp, Color.White.copy(alpha = .4f))) {
        Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).background(Color.White, CircleShape)); Spacer(Modifier.width(10.dp))
            Text("Edit Pages", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun PanelAction(icon: ImageVector, label: String, tag: String, modifier: Modifier, enabled: Boolean = true,
    tint: Color = Color.White, onClick: () -> Unit) {
    val color = if (enabled) tint else Color.White.copy(alpha = .35f)
    Column(modifier.clip(RoundedCornerShape(14.dp)).clickable(enabled = enabled, onClick = onClick)
        .padding(horizontal = 8.dp, vertical = 6.dp).testTag(tag), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, null, tint = color, modifier = Modifier.size(24.dp))
        Text(label, color = color, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp), maxLines = 2,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center, lineHeight = 15.sp)
    }
}

/** Bulk actions for the selected apps and folders. Move to page opens a row of page choices. */
@Composable
internal fun SelectionPanel(homePages: Int, model: LauncherModel, modifier: Modifier = Modifier, onMessage: (String) -> Unit) {
    val ids = SelectionMode.ids ?: return
    var pageMenu by remember { mutableStateOf(false) }
    val ordered = ids.toList()
    Surface(modifier.widthIn(max = 560.dp).testTag("selection-toolbar"), shape = RoundedCornerShape(28.dp), color = EditPanel.copy(alpha = .95f),
        contentColor = Color.White, shadowElevation = 8.dp) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("${ids.size} selected", fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            if (pageMenu) Row(Modifier.padding(top = 10.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                (0..homePages).forEach { page ->
                    Surface(onClick = {
                        pageMenu = false
                        val moved = model.selectionMove(ordered, page)
                        if (moved < ordered.size) onMessage("$moved moved; the page has no room for the rest.")
                        SelectionMode.ids = emptySet()
                    }, shape = CircleShape, color = Color.White.copy(alpha = .12f), border = BorderStroke(1.dp, Color.White.copy(alpha = .3f)),
                        modifier = Modifier.testTag("selection-move-page-$page")) {
                        Text(if (page == homePages) "New page" else "Page ${page + 1}", Modifier.padding(horizontal = 16.dp, vertical = 10.dp))
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                val each = Modifier.weight(1f)
                PanelAction(Icons.Rounded.CreateNewFolder, "Make folder", "selection-folder", each,
                    enabled = ordered.count { !isReservedFolderId(it) } >= 2) {
                    if (model.selectionFolder(ordered)) SelectionMode.ids = emptySet()
                }
                PanelAction(Icons.Rounded.GridView, "Move to page", "selection-move", each) { pageMenu = !pageMenu }
                PanelAction(Icons.Rounded.RemoveCircleOutline, "Remove from Home", "selection-remove", each,
                    enabled = ordered.any { !isReservedFolderId(it) }, tint = Color(0xFFFF6B6B)) {
                    model.selectionRemove(ordered); SelectionMode.ids = emptySet()
                }
                PanelAction(Icons.Rounded.Close, "Deselect", "selection-deselect", each) { SelectionMode.ids = emptySet() }
            }
        }
    }
}

private enum class SizeSetting(val label: String) {
    ICON("Icon size"), ROWS("Row spacing"), TOP("Top margin"), DOCK("Dock width"), HEIGHT("Height on screen")
}

/** Live layout sizes for the current screen shape. Changes save as the slider moves; Default restores one value. */
@Composable
internal fun SizePanel(preset: LayoutPreset, expanded: Boolean, bottomDock: Boolean, autoTopMargin: Float,
    model: LauncherModel, modifier: Modifier = Modifier) {
    var setting by remember { mutableStateOf(SizeSetting.ICON) }
    val settings = SizeSetting.entries.filter { !bottomDock || (it != SizeSetting.DOCK && it != SizeSetting.HEIGHT) }
    val defaults = LayoutPreset()
    fun save(value: LayoutPreset) = model.setPreset(expanded, value)
    Surface(modifier.testTag("size-panel"), shape = RoundedCornerShape(28.dp), color = EditPanel.copy(alpha = .95f),
        contentColor = Color.White, shadowElevation = 8.dp) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    settings.forEach { item ->
                        val on = item == setting
                        Surface(onClick = { setting = item }, shape = CircleShape, color = if (on) EditBlue else Color.White.copy(alpha = .1f),
                            modifier = Modifier.testTag("size-tab-${item.name}")) {
                            Text(item.label, Modifier.padding(horizontal = 16.dp, vertical = 10.dp), fontSize = 15.sp,
                                fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal, maxLines = 1)
                        }
                    }
                }
                IconButton({ SelectionMode.sizePanel = false }, Modifier.testTag("size-close")) { Icon(Icons.Rounded.Close, "Close") }
            }
            val (value, range, label) = when (setting) {
                SizeSetting.ICON -> Triple(preset.iconSize, 40f..68f, "${preset.iconSize.toInt()}dp")
                SizeSetting.ROWS -> Triple(preset.rowGap, 0f..28f, "${preset.rowGap.toInt()}dp")
                SizeSetting.TOP -> if (preset.topMargin < 0f) Triple(autoTopMargin, 8f..160f, "Auto · ${autoTopMargin.toInt()}dp")
                    else Triple(preset.topMargin, 8f..160f, "${preset.topMargin.toInt()}dp")
                SizeSetting.DOCK -> Triple(preset.dockWidth, 56f..84f, "${preset.dockWidth.toInt()}dp")
                SizeSetting.HEIGHT -> Triple(preset.dockPosition, .25f..0.75f,
                    if (preset.dockAlignToGrid) "Aligned to rows" else "${(preset.dockPosition * 100).toInt()}%")
            }
            Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Slider(value.coerceIn(range), { v ->
                    save(when (setting) {
                        SizeSetting.ICON -> preset.copy(iconSize = v)
                        SizeSetting.ROWS -> preset.copy(rowGap = v)
                        SizeSetting.TOP -> preset.copy(topMargin = v)
                        SizeSetting.DOCK -> preset.copy(dockWidth = v)
                        SizeSetting.HEIGHT -> preset.copy(dockPosition = v, dockAlignToGrid = false)
                    })
                }, Modifier.weight(1f).semantics { contentDescription = setting.label }.testTag("size-slider"), valueRange = range,
                    colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = EditBlue, inactiveTrackColor = Color.White.copy(alpha = .2f)))
                Text(label, Modifier.padding(horizontal = 12.dp).widthIn(min = 64.dp), fontSize = 16.sp, maxLines = 1)
                OutlinedButton({
                    save(when (setting) {
                        SizeSetting.ICON -> preset.copy(iconSize = defaults.iconSize)
                        SizeSetting.ROWS -> preset.copy(rowGap = defaults.rowGap)
                        SizeSetting.TOP -> preset.copy(topMargin = defaults.topMargin)
                        SizeSetting.DOCK -> preset.copy(dockWidth = defaults.dockWidth)
                        SizeSetting.HEIGHT -> preset.copy(dockPosition = defaults.dockPosition, dockAlignToGrid = true)
                    })
                }, border = BorderStroke(1.dp, Color.White.copy(alpha = .4f)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White), modifier = Modifier.testTag("size-default")) { Text("Default") }
            }
        }
    }
}

/** Marks a page added in the page editor; it starts empty. */
const val NEW_PAGE = -1

/**
 * Full-screen page overview: page cards in order, the house badge marks (and sets) the main page,
 * arrows move a page, × deletes it, and New page adds an empty one. Done applies; Back discards.
 */
@Composable
internal fun PageEditorOverlay(state: LauncherState, appsById: Map<String, AppEntry>, model: LauncherModel, onDismiss: () -> Unit) {
    var order by remember(state.homePages) { mutableStateOf((0 until state.homePages).toList()) }
    var main by remember { mutableIntStateOf(state.extras.mainPage.coerceIn(0, state.homePages - 1)) }
    var nextNew by remember { mutableIntStateOf(NEW_PAGE) }
    var confirmDelete by remember { mutableStateOf<Int?>(null) }
    val layout = state.layout
    fun itemsOn(page: Int) = if (page < 0) 0 else (0 until HOME_CELLS).count { layout.slotAt(homeCellIndex(page, it)) != null } +
        layout.widgetPlacements.count { it.page == page }
    fun delete(page: Int) { order = order - page; if (main == page) main = order.first() }
    // A full-screen window so the overview also covers the system bar areas; Home blurs behind it where supported.
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss, properties = androidx.compose.ui.window.DialogProperties(
        usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
    val view = androidx.compose.ui.platform.LocalView.current
    SideEffect {
        (view.parent as? androidx.compose.ui.window.DialogWindowProvider)?.window?.let { window ->
            window.setDimAmount(0f)
            window.addFlags(android.view.WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
            window.attributes = window.attributes.apply { blurBehindRadius = 60 }
            androidx.core.view.WindowCompat.getInsetsController(window, window.decorView)
                .hide(androidx.core.view.WindowInsetsCompat.Type.statusBars())
        }
    }
    Box(Modifier.fillMaxSize().background(Color(0xD90B0E13)).testTag("page-editor")) {
        Text("Edit Pages", Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 20.dp), color = Color.White,
            fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
        Box(Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(top = 8.dp, end = 16.dp)) {
            EditPill("Done", "page-editor-done", { model.reorderPages(order, main); onDismiss() })
        }
        Row(Modifier.padding(top = 96.dp).statusBarsPadding().horizontalScroll(rememberScrollState()).padding(horizontal = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            order.forEachIndexed { position, page ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box {
                        PageThumbnail(page, layout, appsById, selected = main == page, Modifier.padding(top = 14.dp))
                        Surface(onClick = { main = page }, modifier = Modifier.align(Alignment.TopCenter).size(30.dp).testTag("page-main-$position"),
                            shape = CircleShape, color = if (main == page) Color.White else EditGlass,
                            contentColor = if (main == page) Color(0xFF14171C) else Color.White,
                            border = BorderStroke(1.dp, Color.White.copy(alpha = .6f))) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(if (main == page) Icons.Rounded.Home else Icons.Outlined.Home,
                                    if (main == page) "Main page" else "Set as main page", Modifier.size(18.dp))
                            }
                        }
                        if (order.size > 1) Surface(onClick = { if (itemsOn(page) == 0) delete(page) else confirmDelete = page },
                            modifier = Modifier.align(Alignment.TopEnd).padding(top = 4.dp).size(26.dp).testTag("page-delete-$position"),
                            shape = CircleShape, color = Color(0xFF3A3F48), contentColor = Color.White) {
                            Box(contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Close, "Delete page", Modifier.size(16.dp)) }
                        }
                    }
                    Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconButton(enabled = position > 0, onClick = { order = order.toMutableList().apply { add(position - 1, removeAt(position)) } },
                            modifier = Modifier.testTag("page-forward-$position")) {
                            Icon(Icons.Rounded.ChevronLeft, "Forward", tint = Color.White.copy(alpha = if (position > 0) 1f else .3f))
                        }
                        Text("${position + 1}", color = Color.White, fontSize = 16.sp)
                        IconButton(enabled = position < order.lastIndex, onClick = { order = order.toMutableList().apply { add(position + 1, removeAt(position)) } },
                            modifier = Modifier.testTag("page-back-$position")) {
                            Icon(Icons.Rounded.ChevronRight, "Back", tint = Color.White.copy(alpha = if (position < order.lastIndex) 1f else .3f))
                        }
                    }
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Surface(onClick = { order = order + nextNew; nextNew-- }, modifier = Modifier.padding(top = 14.dp)
                    .size(THUMB_WIDTH.dp, THUMB_HEIGHT.dp).testTag("page-new"), shape = RoundedCornerShape(22.dp),
                    color = Color.White.copy(alpha = .06f), contentColor = Color.White, border = BorderStroke(1.dp, Color.White.copy(alpha = .3f))) {
                    Box(contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Add, null, Modifier.size(36.dp)) }
                }
                Text("New page", color = Color.White, fontSize = 16.sp, modifier = Modifier.padding(top = 20.dp))
            }
        }
        if (order.last() < 0 || itemsOn(order.last()) == 0 && order.last() >= 0 && order.size > 1)
            Text("An empty last page is removed when you leave.", Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(24.dp),
                color = Color.White.copy(alpha = .7f), fontSize = 13.sp)
    }
    confirmDelete?.let { page ->
        AlertDialog(onDismissRequest = { confirmDelete = null }, title = { Text("Delete this page?") },
            text = { Text("Its apps leave Home but stay in All apps. Its widgets are removed.") },
            confirmButton = { TextButton({ delete(page); confirmDelete = null }) { Text("Delete") } },
            dismissButton = { TextButton({ confirmDelete = null }) { Text("Cancel") } })
    }
    }
}

private const val THUMB_WIDTH = 150
private const val THUMB_HEIGHT = 236

/** A miniature Home page: widgets as grey blocks, apps as small icons, folders as glass squares. */
@Composable
private fun PageThumbnail(page: Int, layout: HomeLayout, appsById: Map<String, AppEntry>, selected: Boolean, modifier: Modifier = Modifier) {
    Box(modifier.size(THUMB_WIDTH.dp, THUMB_HEIGHT.dp).background(Color.White.copy(alpha = .08f), RoundedCornerShape(22.dp))
        .border(if (selected) 3.dp else 1.dp, Color.White.copy(alpha = if (selected) .95f else .3f), RoundedCornerShape(22.dp))
        .padding(12.dp)) {
        if (page < 0) return@Box
        val cellW = (THUMB_WIDTH - 24) / GRID_COLUMNS.toFloat()
        val cellH = (THUMB_HEIGHT - 24) / GRID_ROWS.toFloat()
        layout.widgetPlacements.filter { it.page == page }.forEach { w ->
            Box(Modifier.offset((w.column * cellW + 2).dp, (w.row * cellH + 2).dp).size((w.spanX * cellW - 4).dp, (w.spanY * cellH - 4).dp)
                .background(Color.White.copy(alpha = .3f), RoundedCornerShape(10.dp)))
        }
        repeat(HOME_CELLS) { local ->
            val id = layout.slotAt(homeCellIndex(page, local)) ?: return@repeat
            val x = (local % GRID_COLUMNS) * cellW; val y = (local / GRID_COLUMNS) * cellH
            val icon = (minOf(cellW, cellH) - 8).dp
            val position = Modifier.offset((x + (cellW - minOf(cellW, cellH) + 8) / 2).dp, (y + 4).dp).size(icon)
            val app = appsById[id]
            if (app != null) Image(app.icon.asImageBitmap(), null, position.clip(LocalIconShape.current))
            else if (layout.folder(id) != null) Box(position.background(Color.White.copy(alpha = .35f), RoundedCornerShape(6.dp)))
        }
    }
}

/** Round glass button for the widget placement bar. */
@Composable
internal fun PlacementCircle(icon: ImageVector, description: String, tag: String, onClick: () -> Unit) =
    EditCircle(icon, description, tag, onClick)

/** Glass pill holding the placement bar's page stepper. */
@Composable
internal fun PlacementPill(content: @Composable RowScope.() -> Unit) {
    Surface(Modifier.height(44.dp), shape = CircleShape, color = EditGlass.copy(alpha = .72f), contentColor = Color.White,
        border = BorderStroke(1.dp, Color.White.copy(alpha = .32f))) {
        Row(Modifier.padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically, content = content)
    }
}

/** Placement bar action: glass for Cancel, solid blue for Add. */
@Composable
internal fun PlacementButton(label: String, tag: String, primary: Boolean, enabled: Boolean = true, onClick: () -> Unit) {
    Surface(onClick = onClick, enabled = enabled, modifier = Modifier.height(44.dp).testTag(tag), shape = CircleShape,
        color = if (primary) EditBlue.copy(alpha = if (enabled) 1f else .4f) else EditGlass.copy(alpha = .72f),
        contentColor = Color.White.copy(alpha = if (enabled) 1f else .6f),
        border = if (primary) null else BorderStroke(1.dp, Color.White.copy(alpha = .32f))) {
        Box(Modifier.padding(horizontal = 20.dp), contentAlignment = Alignment.Center) {
            Text(label, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

/** Shadow depth for glass controls when "Glass shadow" is on. */
@Composable
internal fun glassShadow() = if (WallpaperStore.state.collectAsState().value.glassShadow) 8.dp else 0.dp
