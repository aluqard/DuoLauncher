@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.jake.duolauncher

import android.appwidget.AppWidgetProviderInfo
import android.os.UserManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.FormatListBulleted
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateIntOffsetAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.LocalIndication
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.TextStyle
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import kotlin.math.roundToInt
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@Composable
internal fun DockAppColumn(
    savedDock: List<String?>,
    previewDock: List<String?>,
    appsById: Map<String, AppEntry>,
    rowHeight: Float,
    iconSize: Float,
    drag: HomeDragState,
    target: DropTarget?,
    onLaunch: (AppEntry, android.graphics.Rect?) -> Unit,
    onChoose: (Int) -> Unit,
    /** Lay the slots out left to right (bottom dock) instead of top to bottom. [rowHeight] is then the slot width. */
    horizontal: Boolean = false,
) {
    fun slot(index: Int) = if (horizontal) Modifier.fillMaxHeight().width(rowHeight.dp).offset(x = (rowHeight * index).dp)
        else Modifier.fillMaxWidth().height(rowHeight.dp).offset(y = (rowHeight * index).dp)
    val draggedId = drag.source?.appId
    val dockTarget = (target as? DropTarget.Dock)?.index
    val source = drag.source?.target as? DropTarget.Dock
    val draggedPreviewIndex = previewDock.indexOf(draggedId)
    val hiddenIndex = when {
        !drag.active || !drag.moved -> null
        dockTarget != null -> draggedPreviewIndex.takeIf { it >= 0 }
        source != null && target !is DropTarget.Home -> draggedPreviewIndex.takeIf { it >= 0 }
        else -> null
    }
    val dimDragged = drag.active && !drag.moved && source != null
    val launchBounds = remember(savedDock.size) { List(savedDock.size) { android.graphics.Rect() } }
    val interactions = remember(savedDock.size) { List(savedDock.size) { MutableInteractionSource() } }
    val slotScales = savedDock.indices.map { index ->
        val pressed by interactions[index].collectIsPressedAsState()
        val scale by animateFloatAsState(if (pressed) .92f else 1f, label = "dock press $index")
        scale
    }
    val density = LocalDensity.current
    val rowHeightPx = with(density) { rowHeight.dp.toPx() }
    Box(if (horizontal) Modifier.fillMaxHeight().width((rowHeight * savedDock.size).dp)
        else Modifier.fillMaxWidth().height((rowHeight * savedDock.size).dp)) {
        savedDock.indices.forEach { index ->
            val cell = DropTarget.Dock(index)
            val savedApp = appsById[savedDock[index]]
            val previewId = previewDock.getOrNull(index)
            val highlighted = drag.active && target == cell
            val gap = hiddenIndex == index
            Box(slot(index)
                .background(if (highlighted) Color.White.copy(alpha = .3f) else Color.Transparent, RoundedCornerShape(16.dp)),
                contentAlignment = Alignment.Center) {
                when {
                    gap -> Box(Modifier.size(iconSize.dp).testTag("drag-gap-dock-$index")
                        .background(Glass.copy(alpha = .16f), RoundedCornerShape(14.dp))
                        .border(2.dp, Color.White.copy(alpha = .55f), RoundedCornerShape(14.dp)))
                    previewId == null -> Icon(Icons.Rounded.Add, null, tint = Color.White, modifier = Modifier.size(24.dp))
                }
            }
            Box(slot(index)
                .testTag("dock-slot-$index").dropRegion(drag, cell, savedApp?.id)
                .semantics(mergeDescendants = true) { contentDescription = savedApp?.label ?: "Choose dock app ${index + 1}" }
                .combinedClickable(interactionSource = interactions[index], indication = LocalIndication.current, role = Role.Button, onClick = {
                    if (savedApp != null) onLaunch(savedApp, launchBounds[index]) else onChoose(index)
                }, onLongClick = null)
                .semantics { onLongClick("Choose dock app") { onChoose(index); true } })
        }

        val ids = (savedDock + previewDock).filterNotNull().distinct()
        ids.forEach { id ->
            val savedIndex = savedDock.indexOf(id)
            val previewIndex = previewDock.indexOf(id)
            val renderIndex = previewIndex.takeIf { it >= 0 } ?: savedIndex.takeIf { it >= 0 } ?: return@forEach
            val app = appsById[id] ?: return@forEach
            key(id) {
                val animatedOffset by animateIntOffsetAsState(
                    if (horizontal) IntOffset((renderIndex * rowHeightPx).roundToInt(), 0)
                    else IntOffset(0, (renderIndex * rowHeightPx).roundToInt()), label = "dock insertion $id")
                val visible = previewIndex >= 0 && renderIndex != hiddenIndex
                val opacity by animateFloatAsState(
                    if (!visible) 0f else if (dimDragged && id == draggedId) .28f else 1f,
                    label = "dock insertion visibility $id",
                )
                Box(Modifier.offset { animatedOffset }.then(if (horizontal) Modifier.fillMaxHeight().width(rowHeight.dp)
                    else Modifier.fillMaxWidth().height(rowHeight.dp)).alpha(opacity)
                    .testTag("dock-app-$id"), contentAlignment = Alignment.Center) {
                    Box {
                        Image(app.icon.asImageBitmap(), null, Modifier.size(iconSize.dp).testTag("dock-icon-$id")
                            .onGloballyPositioned { if (savedIndex >= 0) launchBounds[savedIndex].set(it.boundsInWindow().toAndroidBounds()) }
                            .graphicsLayer { scaleX = slotScales[renderIndex]; scaleY = slotScales[renderIndex] }
                            .clip(LocalIconShape.current))
                        BadgeCount(LocalBadges.current.countFor(app), Modifier.align(Alignment.TopEnd).offset(5.dp, (-4).dp))
                    }
                }
            }
        }
    }
}
