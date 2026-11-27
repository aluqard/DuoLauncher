package com.jake.duolauncher

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.roundToInt
import kotlin.math.sign

/**
 * Rubber-band stretch past the first or last page. [offset] is in pixels: positive when pulled
 * right past the first page, negative when pulled left past the last one.
 */
class JellyStretch {
    var offset by mutableFloatStateOf(0f)
        private set
    var width = 1f

    /** Maps raw overscroll [pixels] to a stretch that grows ever more slowly toward 14% of the width. */
    fun pull(pixels: Float) { offset = rubberBand(pixels, width * .14f) }

    suspend fun release() {
        if (offset == 0f) return
        animate(offset, 0f, animationSpec = spring(dampingRatio = .38f, stiffness = Spring.StiffnessMediumLow)) { value, _ -> offset = value }
        offset = 0f
    }

    /** Stretches the whole screen from the edge opposite the pull, with a slight vertical squash. */
    fun apply(scope: GraphicsLayerScope) = with(scope) {
        val f = offset / width.coerceAtLeast(1f)
        scaleX = 1f + abs(f)
        scaleY = 1f - abs(f) * .25f
        transformOrigin = TransformOrigin(if (f > 0f) 0f else 1f, .5f)
    }
}

internal fun rubberBand(pixels: Float, limit: Float): Float =
    if (limit <= 0f) 0f else sign(pixels) * limit * (1f - exp(-abs(pixels) / (limit * 2.2f)))

/** iPhone-style page dots: one per Home page, the current one solid white. Tap a dot to go to its page. */
@Composable
internal fun PageDots(pages: Int, current: Int, newPage: Int?, onPage: (Int) -> Unit,
    onDiscover: (() -> Unit)?, onLibrary: () -> Unit, modifier: Modifier = Modifier) {
    val dot = if (pages > 10) 5.dp else 7.dp
    val actions = buildList {
        onDiscover?.let { add(CustomAccessibilityAction("Discover") { it(); true }) }
        add(CustomAccessibilityAction("All apps page") { onLibrary(); true })
    }
    Row(modifier.testTag("page-indicator").semantics {
        contentDescription = if (current in 0 until pages) "Page ${current + 1} of $pages" else "$pages pages"
        customActions = actions
    }, verticalAlignment = Alignment.CenterVertically) {
        repeat(pages) { index ->
            val selected = index == current
            Box(Modifier.size(width = dot + 10.dp, height = 28.dp)
                .clickable(remember { MutableInteractionSource() }, null) { onPage(index) }
                .semantics { contentDescription = if (index == newPage) "New home page" else "Home page ${index + 1}" },
                contentAlignment = Alignment.Center) {
                Box(Modifier.size(dot).background(Color.White.copy(alpha = if (selected) 1f else if (index == newPage) .2f else .38f), CircleShape))
            }
        }
    }
}

/** A row in the widget popover menu. */
private data class MenuItem(val label: String, val icon: ImageVector, val tag: String, val danger: Boolean = false, val action: () -> Unit)

/**
 * Popover shown when a widget is held: the widget gets a white outline and the menu opens below it
 * (or above when there is no room). Tapping anywhere else closes it.
 */
@Composable
internal fun WidgetMenu(bounds: Rect, canConfigure: Boolean, onSize: () -> Unit, onConfigure: () -> Unit,
    onEditHome: () -> Unit, onReplace: () -> Unit, onRemove: () -> Unit, onDismiss: () -> Unit) {
    val density = LocalDensity.current
    val groups = listOfNotNull(
        listOfNotNull(MenuItem("Size & position", Icons.Rounded.OpenInFull, "widget-menu-size", action = onSize),
            if (canConfigure) MenuItem("Widget settings", Icons.Rounded.Tune, "widget-menu-configure", action = onConfigure) else null,
            MenuItem("Edit Home Screen", Icons.Rounded.Apps, "widget-menu-edit", action = onEditHome)),
        listOf(MenuItem("Replace widget", Icons.Rounded.FindReplace, "widget-menu-replace", action = onReplace)),
        listOf(MenuItem("Remove widget", Icons.Rounded.RemoveCircleOutline, "widget-menu-remove", danger = true, action = onRemove)),
    )
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .28f))
        .clickable(remember { MutableInteractionSource() }, null, onClick = onDismiss).testTag("widget-menu-scrim")) {
        Box(Modifier.offset { IntOffset(bounds.left.roundToInt(), bounds.top.roundToInt()) }
            .size(with(density) { bounds.width.toDp() }, with(density) { bounds.height.toDp() })
            .border(3.dp, Color.White, RoundedCornerShape(26.dp)))
        val gap = with(density) { 14.dp.toPx() }
        Surface(Modifier.layout { measurable, constraints ->
            val placeable = measurable.measure(Constraints(maxWidth = constraints.maxWidth))
            val below = bounds.bottom + gap
            val y = if (below + placeable.height <= constraints.maxHeight) below
                else (bounds.top - gap - placeable.height).coerceAtLeast(gap)
            val x = (bounds.center.x - placeable.width / 2f).coerceIn(gap, (constraints.maxWidth - placeable.width - gap).coerceAtLeast(gap))
            layout(constraints.maxWidth, constraints.maxHeight) { placeable.place(x.roundToInt(), y.roundToInt()) }
        }.width(300.dp).testTag("widget-menu").semantics { contentDescription = "Widget options" },
            shape = RoundedCornerShape(26.dp), color = Color(0xF21C1D21), contentColor = Color.White, shadowElevation = 10.dp) {
            Column {
                groups.forEachIndexed { g, items ->
                    if (g > 0) Box(Modifier.fillMaxWidth().height(8.dp).background(Color.Black.copy(alpha = .35f)))
                    items.forEachIndexed { i, item ->
                        if (i > 0) HorizontalDivider(Modifier.padding(horizontal = 18.dp), color = Color.White.copy(alpha = .1f))
                        Row(Modifier.fillMaxWidth().clickable(onClick = item.action).heightIn(min = 56.dp)
                            .padding(horizontal = 18.dp, vertical = 12.dp).testTag(item.tag), verticalAlignment = Alignment.CenterVertically) {
                            val color = if (item.danger) Color(0xFFFF5A52) else Color.White
                            Text(item.label, Modifier.weight(1f), color = color, fontSize = 18.sp)
                            Icon(item.icon, null, tint = color, modifier = Modifier.size(22.dp))
                        }
                    }
                }
            }
        }
    }
}

/** Curved corner handle drawn over a resizing widget's lower-right corner. */
@Composable
internal fun ResizeCornerHandle(valid: Boolean, modifier: Modifier = Modifier) {
    val color = if (valid) Color.White else Color(0xFFFF6B6B)
    Canvas(modifier) {
        // The canvas's centre sits on the outline's corner; the arc follows the corner's curve.
        val radius = 24.dp.toPx() + 2.dp.toPx()
        val center = Offset(size.width / 2f - 24.dp.toPx(), size.height / 2f - 24.dp.toPx())
        drawArc(color, startAngle = -8f, sweepAngle = 106f, useCenter = false,
            topLeft = center - Offset(radius, radius), size = Size(radius * 2, radius * 2),
            style = Stroke(width = 5.dp.toPx(), cap = StrokeCap.Round))
    }
}

/** "Cancel · W × H · Done" pill under a resizing widget. */
@Composable
internal fun ResizePill(width: Int, height: Int, valid: Boolean, onCancel: () -> Unit, onDone: () -> Unit, modifier: Modifier = Modifier) {
    Surface(modifier.testTag("widget-resize-pill"), shape = CircleShape, color = Color(0xE61C1D21), contentColor = Color.White,
        border = BorderStroke(1.dp, Color.White.copy(alpha = .14f)), shadowElevation = 6.dp) {
        Row(Modifier.padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onCancel, colors = ButtonDefaults.textButtonColors(contentColor = Color.White)) {
                Text("Cancel", fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            }
            Text("$width × $height", color = Color.White.copy(alpha = .7f), fontSize = 17.sp, modifier = Modifier.padding(horizontal = 6.dp))
            TextButton(onDone, enabled = valid, colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFF2F8CFF),
                disabledContentColor = Color.White.copy(alpha = .35f))) {
                Text("Done", fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}


/**
 * A soft shadow drawn only outside [shape]. Android's elevation shadow also lies under the shape, so it shows
 * through translucent glass (between dock icons, inside the search button); this one leaves the glass clear.
 */
internal fun Modifier.outerShadow(elevation: androidx.compose.ui.unit.Dp, shape: androidx.compose.ui.graphics.Shape,
    color: Color = Color.Black.copy(alpha = .32f)): Modifier =
    if (elevation <= 0.dp) this else drawWithCache {
        val path = androidx.compose.ui.graphics.Path().apply { addOutline(shape.createOutline(size, layoutDirection, this@drawWithCache)) }
        val lift = elevation.toPx()
        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            this.color = android.graphics.Color.BLACK
            setShadowLayer(lift * 1.1f, 0f, lift * .35f, color.toArgb())
        }
        onDrawBehind {
            clipPath(path, androidx.compose.ui.graphics.ClipOp.Difference) {
                drawContext.canvas.nativeCanvas.drawPath(path.asAndroidPath(), paint)
            }
        }
    }
