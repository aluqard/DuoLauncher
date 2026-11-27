package com.jake.duolauncher

/** Jake's reference measures 76px dock artwork against 106px home artwork. */
fun dockIconSize(homeIconSize: Float) = homeIconSize * (76f / 106f)

const val AUTO_TOP_MARGIN = -1f

/** Glass around a side dock's icon column: the saved dock width is the column, the bar is this much wider. */
const val SIDE_DOCK_EDGE = 27f

/** Width of a side dock's glass bar for a saved dock width. */
fun sideDockBarWidth(dockWidth: Float) = dockWidth + SIDE_DOCK_EDGE

data class LayoutPreset(
    val iconSize: Float = 66f,
    val rowGap: Float = 8f,
    val dockWidth: Float = 68f,
    val dockPosition: Float = 0.56f,
    val dockAlignToGrid: Boolean = true,
    val dockSide: DockSide = DockSide.RIGHT,
    /** Space above the first Home row in dp; negative centers the grid vertically (the original behavior). */
    val topMargin: Float = AUTO_TOP_MARGIN,
) {
    fun sanitized() = copy(
        topMargin = if (topMargin < 0f) AUTO_TOP_MARGIN else topMargin.coerceIn(8f, 160f),
        iconSize = iconSize.coerceIn(40f, 68f),
        rowGap = rowGap.coerceIn(0f, 28f),
        dockWidth = dockWidth.coerceIn(56f, 84f),
        dockPosition = dockPosition.coerceIn(0.25f, 0.75f),
    )
}

data class HomeGeometry(
    val expanded: Boolean,
    val homeWidth: Float,
    val gridWidth: Float,
    val iconSize: Float,
    val rowHeight: Float,
    val widgetHeight: Float,
    val contentTop: Float,
    val dockTop: Float,
    val dockHeight: Float,
    val dockRowHeight: Float,
    /** Edge holding the dock. The unfolded (expanded) layout always keeps the original right dock. */
    val dockSide: DockSide = DockSide.RIGHT,
    /** Height of a bottom dock bar; 0 for side docks. */
    val bottomDockHeight: Float = 0f,
    /** Width of a side dock's glass bar; 0 for a bottom dock. */
    val dockWidth: Float = 0f,
    /** Size of the dock's app icons. */
    val dockIconSize: Float = 0f,
)

/** The dock edge actually used: side docks other than RIGHT apply to the folded/phone layout only. */
fun effectiveDockSide(preset: LayoutPreset, expanded: Boolean) = if (expanded) DockSide.RIGHT else preset.dockSide

/** Advance old defaults without changing individually tuned values. */
fun upgradePreset(preset: LayoutPreset, schema: Int, expanded: Boolean): LayoutPreset = when {
    schema < 2 -> preset.copy(
        iconSize = if (preset.iconSize == if (expanded) 58f else 54f) 66f else preset.iconSize,
        rowGap = if (preset.rowGap == 12f) 8f else preset.rowGap,
        dockWidth = if (preset.dockWidth == 64f) 68f else preset.dockWidth,
    )
    schema == 2 && preset.iconSize == 60f -> preset.copy(iconSize = 66f)
    else -> preset
}

fun homeGeometry(width: Float, height: Float, preset: LayoutPreset, labels: Boolean, statusHeight: Float = 0f, labelHeight: Float = 20f, inLibrary: Boolean = false, homeBottomSpace: Float = 44f): HomeGeometry {
    val p = preset.sanitized()
    val expanded = width >= 650f
    val homeWidth = if (expanded) minOf(460f, width * 0.56f) else width
    val side = effectiveDockSide(p, expanded)
    val dockBar = if (side == DockSide.BOTTOM) 0f else sideDockBarWidth(p.dockWidth)
    val gridWidth = (homeWidth - dockBar - 44f).coerceAtLeast(192f)
    val icon = minOf(p.iconSize, (gridWidth / 4f - 10f).coerceAtLeast(32f))
    // Keep the same icon rhythm when labels are hidden; allow larger system text to fit.
    val row = maxOf(48f, icon + if (labels) maxOf(20f, labelHeight) else 20f) + p.rowGap
    val widget = minOf(176f, gridWidth / 2f - 5f).coerceAtLeast(88f)
    // A bottom dock is one row of dock-sized icons in a glass bar above the page controls.
    val bottomDock = if (side == DockSide.BOTTOM) dockIconSize(icon) + 28f else 0f
    val contentTop = if (p.topMargin >= 0f) p.topMargin
        else ((height - widget - 18f - 4f * row - homeBottomSpace - bottomDock) / 2f).coerceIn(16f, 72f)
    // Search reclaims the redundant bottom controls' space for all four dock apps.
    // Extremely short windows still scroll rather than reduce touch targets below 48dp.
    val topLimit = maxOf(8f, statusHeight)
    val bottomReserve = if (inLibrary) 12f else 124f
    // A side dock holds four home-size icons, one slot per app row.
    val desiredHeight = 4f * row
    val dockHeight = minOf(desiredHeight, (height - topLimit - bottomReserve).coerceAtLeast(76f))
    val dockRowHeight = if (side == DockSide.BOTTOM) (gridWidth / 4f).coerceAtLeast(48f) else (dockHeight / 4f).coerceIn(48f, maxOf(48f, row))
    val dockIcon = if (side == DockSide.BOTTOM) dockIconSize(icon) else minOf(icon, p.dockWidth, dockRowHeight - 6f)
    // Slots center their icons, so an aligned dock starts that far above the first app row and each
    // dock icon lines up with an app row's icon.
    val slotInset = (dockRowHeight - dockIcon) / 2f
    // Use the home position as the anchor, so removing library buttons does not
    // move a low-positioned dock on ordinary page swipes. Move up only to fit.
    val homeDockHeight = minOf(desiredHeight, (height - topLimit - 124f).coerceAtLeast(76f))
    val homeDockTop = (if (p.dockAlignToGrid) contentTop + widget + 18f - slotInset else height * p.dockPosition - homeDockHeight / 2f)
        .coerceIn(topLimit, maxOf(topLimit, height - homeDockHeight - 124f))
    val dockTop = homeDockTop.coerceIn(topLimit, maxOf(topLimit, height - dockHeight - bottomReserve))
    return HomeGeometry(expanded, homeWidth, gridWidth, icon, row, widget, contentTop, dockTop, dockHeight, dockRowHeight,
        side, bottomDock, dockBar, dockIcon)
}

/** Keep stored order stable across installs, removals and configuration changes. */
fun reconcileOrder(saved: List<String>, installed: List<String>): List<String> {
    val present = installed.toSet()
    return (saved.filter { it in present } + installed).distinct()
}

/** Installing an app must never create a home-screen pin. */
fun reconcilePins(saved: List<String>, installed: List<String>): List<String> {
    val available = installed.toSet()
    return saved.filter { it in available }.distinct()
}

fun migrateHomePins(legacy: List<String>, installed: List<String>, suggested: List<String>): List<String> {
    val surviving = reconcilePins(legacy, installed)
    val oldSet = surviving.toSet()
    val wasReordered = surviving.isNotEmpty() && surviving != installed.filter { it in oldSet }
    return if (wasReordered) surviving.take(16) else reconcilePins(suggested, installed).take(16)
}

fun homePageCount(cellCount: Int) = maxOf(1, (cellCount + HOME_CELLS - 1) / HOME_CELLS)

fun moveApp(order: List<String>, id: String, offset: Int): List<String> {
    val from = order.indexOf(id)
    if (from < 0) return order
    val to = (from + offset).coerceIn(0, order.lastIndex)
    return order.toMutableList().apply { add(to, removeAt(from)) }
}
