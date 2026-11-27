@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.jake.duolauncher

import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.ui.semantics.clearAndSetSemantics
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
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.foundation.indication
import androidx.compose.ui.layout.layout
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

internal val Ink: Color
    @Composable get() = LocalDuoPalette.current.ink
internal val Glass: Color
    @Composable get() = LocalDuoPalette.current.glass

private fun findFreeWidgetIndex(layout: HomeLayout, page: Int, spanX: Int, spanY: Int): Int? {
    val blocked = layout.widgetPlacements.flatMapTo(mutableSetOf()) { it.coveredIndices() }
    for (row in 0..GRID_ROWS - spanY) for (column in 0..GRID_COLUMNS - spanX) {
        val cells = buildList {
            repeat(spanY) { y -> repeat(spanX) { x -> add(homeCellIndex(page, (row + y) * GRID_COLUMNS + column + x)) } }
        }
        if (cells.none { it in blocked || layout.slotAt(it) != null }) return cells.first()
    }
    return null
}

private const val WIDGET_NO_ROOM_MESSAGE = "There isn’t room for this size. Choose another page or move an item first."

@Composable
fun DuoTheme(dark: Boolean = false, content: @Composable () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val theme by remember(context) { ThemeStore.load(context.applicationContext) }.collectAsState()
    val palette = themedPalette(if (dark) DarkDuoPalette else LightDuoPalette, theme)
    val accent = if (theme.colorTheme == "duo") null else Color(theme.accent())
    CompositionLocalProvider(LocalDuoPalette provides palette, LocalIconShape provides composeIconShape(theme.iconShape)) {
        MaterialTheme(colorScheme = if (dark) darkColorScheme(primary = accent ?: Color(0xFF9BC5D7), onPrimary = Color(0xFF12303D),
            surface = Color(0xFF17272E), onSurface = palette.ink, secondary = Color(0xFFD1BE98),
            secondaryContainer = Color(0xFF314852), onSecondaryContainer = palette.ink)
        else lightColorScheme(primary = accent ?: Color(0xFF30596D), onPrimary = Color.White,
            surface = Color(0xFFF4F7F8), onSurface = palette.ink, secondary = Color(0xFF84775F),
            secondaryContainer = Color(0xFFDCE8ED), onSecondaryContainer = palette.ink), content = content)
    }
}


@Composable
fun LauncherScreen(
    state: LauncherState, model: LauncherModel, widgets: WidgetController, homeRequests: Int,
    onLaunch: (AppEntry) -> Unit, onMakeDefault: () -> Unit, onAppInfo: (AppEntry) -> Unit,
    isDefaultHome: Boolean, deviceStatus: DeviceStatus, onStatusMode: (Boolean) -> Unit, onWallpaperPreview: () -> Unit,
    onDiscover: () -> Unit = {}, searchRequests: Int = 0,
    onLaunchFrom: (AppEntry, android.graphics.Rect?) -> Unit = { app, _ -> onLaunch(app) },
    onGoogleSearch: (android.graphics.Rect?) -> Boolean = { false },
    appearance: AppearanceState = AppearanceState(),
    onAppearanceMode: (AppearanceMode) -> Unit = {},
    onAppearanceManual: (String, Double, Double) -> Unit = { _, _, _ -> },
    onAppearanceDeviceLocation: () -> Unit = {},
    onAppearanceClear: () -> Unit = {},
    showFirstRun: Boolean = false,
    onFinishFirstRun: () -> Unit = {},
    onShadeSetup: () -> Unit = {},
    onUninstall: (AppEntry) -> Unit = {},
    mainPageRequests: Int = 0,
) {
    var sheet by rememberSaveable { mutableStateOf("") }
    var dockSlot by rememberSaveable { mutableIntStateOf(0) }
    var widgetSlot by rememberSaveable { mutableIntStateOf(0) }
    var widgetTargetIndex by rememberSaveable { mutableIntStateOf(Int.MIN_VALUE) }
    var widgetExactTarget by rememberSaveable { mutableStateOf(false) }
    var widgetPackage by rememberSaveable { mutableStateOf<String?>(null) }
    var widgetProfileSerial by rememberSaveable { mutableStateOf<Long?>(null) }
    var widgetSession by remember { mutableStateOf<WidgetPickerSession?>(null) }
    var widgetPlacementMessage by remember { mutableStateOf<String?>(null) }
    var resizeSlot by remember { mutableStateOf<Int?>(null) }
    var widgetMenuSlot by remember { mutableStateOf<Int?>(null) }
    val jelly = remember { JellyStretch() }
    var layoutGeometry by remember { mutableStateOf<HomeGeometry?>(null) }
    var resizeWidth by rememberSaveable { mutableIntStateOf(1) }
    var resizeHeight by rememberSaveable { mutableIntStateOf(1) }
    var resizeConstraints by remember { mutableStateOf<WidgetSpanConstraints?>(null) }
    var resizePitchX by remember { mutableFloatStateOf(1f) }
    var resizePitchY by remember { mutableFloatStateOf(1f) }
    var resizeTopPitch by remember { mutableFloatStateOf(1f) }
    var resizeAppPitch by remember { mutableFloatStateOf(1f) }
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    var appMoveMenu by rememberSaveable { mutableStateOf(false) }
    var customizationPage by rememberSaveable { mutableStateOf(CustomizationPage.OVERVIEW) }
    LaunchedEffect(selectedId) { if (selectedId == null) appMoveMenu = false }
    LaunchedEffect(sheet) { if (sheet.isEmpty()) customizationPage = CustomizationPage.OVERVIEW }
    var openFolderId by rememberSaveable { mutableStateOf<String?>(null) }
    var createFolderFirstId by rememberSaveable { mutableStateOf<String?>(null) }
    var editAppId by rememberSaveable { mutableStateOf<String?>(null) }
    var categoryAppId by rememberSaveable { mutableStateOf<String?>(null) }
    var pageEditor by rememberSaveable { mutableStateOf(false) }
    var selectionMessage by remember { mutableStateOf<String?>(null) }
    // While selecting, taps toggle selection instead of launching or opening folders.
    val launchOrSelect: (AppEntry, android.graphics.Rect?) -> Unit = { app, bounds ->
        if (SelectionMode.active) SelectionMode.toggle(app.id) else onLaunchFrom(app, bounds) }
    val folderOrSelect: (String) -> Unit = { id -> if (SelectionMode.active) SelectionMode.toggle(id) else openFolderId = id }
    val shownApps = remember(state.apps, state.extras.hiddenApps) { state.apps.filter { it.id !in state.extras.hiddenApps } }
    var savedPage by rememberSaveable { mutableIntStateOf(0) }
    var lastHomePage by rememberSaveable { mutableIntStateOf(0) }
    var libraryQuery by rememberSaveable { mutableStateOf("") }
    // Home's search button: app search rises over the wallpaper while Home fades out, without leaving the page.
    var homeSearchOpen by rememberSaveable { mutableStateOf(false) }
    var homeSearchQuery by rememberSaveable { mutableStateOf("") }
    val homeSearchProgress by animateFloatAsState(if (homeSearchOpen) 1f else 0f,
        tween(if (homeSearchOpen) 300 else 260, easing = FastOutSlowInEasing), label = "home-search")
    val openHomeSearch = {
        if (!homeSearchOpen) homeSearchQuery = ""
        SelectionMode.exit(); homeSearchOpen = true
    }
    var pinQuery by rememberSaveable { mutableStateOf("") }
    val launcherActivity = androidx.activity.compose.LocalActivity.current as MainActivity
    val launcherRootView = LocalView.current.rootView
    DisposableEffect(sheet == "widgets") {
        val active = sheet == "widgets"
        if (active) LiveDiscover.setExternalResultPending(launcherActivity, "main", "widget-picker", true)
        onDispose { if (active) LiveDiscover.setExternalResultPending(launcherActivity, "main", "widget-picker", false) }
    }
    val appsById = remember(state.apps, state.shortcuts) { (state.apps + state.shortcuts).associateBy { it.id } }
    val drag = remember { HomeDragState() }
    val folderOwnsInput = openFolderId != null || drag.source?.folderId != null
    DisposableEffect(folderOwnsInput) {
        if (folderOwnsInput) LiveDiscover.setExternalResultPending(launcherActivity, "main", "folder-panel", true)
        onDispose { if (folderOwnsInput) LiveDiscover.setExternalResultPending(launcherActivity, "main", "folder-panel", false) }
    }
    val haptic = LocalHapticFeedback.current
    // Discover opens from Home only when turned on in Gestures & search; otherwise Home has no page before it.
    val discoverByGesture by DiscoverSwipeSetting.enabled(androidx.compose.ui.platform.LocalContext.current).collectAsState()
    val homePages = state.homePages
    val pendingNewPage = widgets.pendingPlacement?.page == homePages
    val visibleHomePages = homePages + if (drag.active || widgetSession != null || pendingNewPage) 1 else 0
    var expandedWorkspace by remember { mutableStateOf(false) }
    val firstHome = if (DiscoverBounds.available && discoverByGesture) 1 else 0
    val pageCount = visibleHomePages + 1
    // Physical page numbers shift when the Discover page comes or goes, so the pager starts over then.
    val nativePager = key(firstHome) {
        rememberPagerState(initialPage = savedPage.coerceIn(-firstHome, pageCount - 1) + firstHome, pageCount = { pageCount + firstHome })
    }
    val pager = remember(nativePager) { LauncherPager(nativePager, firstHome) }
    fun leaveTemporaryWidgetPage() {
        val persistedPages = model.state.value.homePages
        if (pager.currentPage >= persistedPages)
            pager.requestScrollToPage((persistedPages - 1).coerceAtLeast(0))
    }
    var priorPendingPlacement by remember { mutableStateOf<WidgetPlacement?>(null) }
    LaunchedEffect(widgets.pendingPlacement, state.layout) {
        val pending = widgets.pendingPlacement
        if (pending != null) priorPendingPlacement = pending
        else priorPendingPlacement?.let { prior ->
            if (model.placement(prior.slot) == null && prior.page >= homePages) leaveTemporaryWidgetPage()
            priorPendingPlacement = null
        }
    }
    val pageGestures = remember(nativePager) { PageGestureLimits(nativePager) }
    SideEffect { pageGestures.editing = drag.active || widgetSession != null || resizeSlot != null; LiveDiscover.allowNativeOpen = pager.currentPage == 0 && !drag.active && widgetSession == null && resizeSlot == null }
    val pageFling = androidx.compose.foundation.pager.PagerDefaults.flingBehavior(nativePager, pagerSnapDistance = pageGestures)
    var nativeMotion by remember { mutableStateOf(false) }
    DisposableEffect(nativePager) {
        val callback: (Float) -> Unit = { progress ->
            val scrolling = nativePager.isScrollInProgress
            if (DuoMotionTrace.enabled) DuoMotionTrace.event("native_callback_received",
                "progress=$progress scrolling=$scrolling nativeMotion=$nativeMotion current=${nativePager.currentPage} offset=${nativePager.currentPageOffsetFraction}")
            if (!scrolling || nativeMotion) {
                val priorNativeMotion = nativeMotion
                nativeMotion = progress > 0f && progress < 1f
                val position = 1f - progress
                val page = position.roundToInt()
                if (DuoMotionTrace.enabled) DuoMotionTrace.event("native_callback_accepted",
                    "progress=$progress nativeMotion=$priorNativeMotion->$nativeMotion requestPage=$page requestOffset=${position - page}")
                nativePager.requestScrollToPage(page, position - page)
            } else if (DuoMotionTrace.enabled) DuoMotionTrace.event("native_callback_rejected",
                "progress=$progress reason=compose_scrolling nativeMotion=$nativeMotion")
        }
        LiveDiscover.onNativeProgress = callback
        onDispose { if (LiveDiscover.onNativeProgress === callback) LiveDiscover.onNativeProgress = null }
    }
    LaunchedEffect(nativePager) {
        snapshotFlow { Triple((1f - nativePager.currentPage - nativePager.currentPageOffsetFraction).coerceIn(0f, 1f), nativePager.isScrollInProgress, nativeMotion) to (nativePager.targetPage < firstHome) }
            .collect { (motion, towardFeed) ->
                val (progress, scrolling, native) = motion
                if (firstHome > 0) {
                    if (DuoMotionTrace.enabled) DuoMotionTrace.event("pager_observer",
                        "progress=$progress scrolling=$scrolling nativeMotion=$native towardFeed=$towardFeed")
                    if (scrolling) {
                        if (nativeMotion && DuoMotionTrace.enabled) DuoMotionTrace.event("native_owner_cleared",
                            "reason=compose_scrolling progress=$progress")
                        nativeMotion = false
                        LiveDiscover.page(progress, true, towardFeed)
                    } else if (!native) LiveDiscover.page(progress, false)
                }
            }
    }
    val scope = rememberCoroutineScope()
    DisposableEffect(pager) {
        val callback = { scope.launch { pager.animateScrollToPage(0) }; Unit }
        LiveDiscover.onHomeRequest = callback
        onDispose { if (LiveDiscover.onHomeRequest === callback) LiveDiscover.onHomeRequest = null }
    }
    var previousHomePages by remember { mutableIntStateOf(homePages) }
    var previousEditRevision by remember { mutableIntStateOf(state.editRevision) }
    val focus = androidx.compose.ui.platform.LocalFocusManager.current
    val keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    LaunchedEffect(pager, homePages) {
        snapshotFlow { pager.settledPage to drag.active }.distinctUntilChanged().collect { (page, moving) ->
            if (!moving) { savedPage = page; if (page in 0 until homePages) lastHomePage = page }
        }
    }
    LaunchedEffect(homePages, state.editRevision) {
        if (homePages != previousHomePages && !drag.active) {
            // Pin edits in the library keep the library selected; a completed drop stays on home.
            if (state.editRevision == previousEditRevision) {
                if (pager.currentPage == previousHomePages) pager.scrollToPage(homePages)
                else if (pager.currentPage >= pageCount) pager.scrollToPage(homePages - 1)
            } else if (pager.currentPage >= homePages) pager.scrollToPage(homePages - 1)
        }
        previousHomePages = homePages
        previousEditRevision = state.editRevision
    }
    LaunchedEffect(pager.settledPage) { if (pager.settledPage != homePages) focus.clearFocus() }
    LaunchedEffect(homeRequests) { if (homeRequests > 0) {
        // An app can pause Home after the destination is visible but before its settle completes.
        val page = pager.currentPage.takeIf { it in 0 until homePages }
            ?: lastHomePage.coerceIn(0, homePages - 1)
        drag.clear(); widgetSession = null; resizeSlot = null; sheet = ""; widgetPackage = null
        widgetExactTarget = false; widgetPlacementMessage = null; selectedId = null; appMoveMenu = false
        openFolderId = null; createFolderFirstId = null; homeSearchOpen = false
        focus.clearFocus(); keyboard?.hide()
        pager.animateScrollToPage(page)
    } }
    LaunchedEffect(mainPageRequests) { if (mainPageRequests > 0) {
        SelectionMode.ids = null; homeSearchOpen = false
        pager.animateScrollToPage(state.extras.mainPage.coerceIn(0, homePages - 1))
    } }
    LaunchedEffect(searchRequests) { if (searchRequests > 0) { drag.clear(); widgetSession = null; resizeSlot = null; sheet = ""; widgetPackage = null; widgetExactTarget = false; selectedId = null
        openHomeSearch()
    } }
    val widgetPickerBack = {
        if (widgetSession != null) {
            leaveTemporaryWidgetPage(); widgetSession = null; widgetPlacementMessage = null
        } else {
            sheet = ""; widgetPackage = null; widgetExactTarget = false; widgetPlacementMessage = null
        }
    }
    BackHandler(enabled = sheet == "widgets") { widgetPickerBack() }
    BackHandler(enabled = sheet.isEmpty()) { if (resizeSlot != null) resizeSlot = null else if (drag.active) {
        val destination = if (drag.source?.target is DropTarget.Library) homePages else drag.originPage.coerceAtMost(homePages - 1)
        drag.clear(); scope.launch { pager.scrollToPage(destination) }
    } else if (selectedId != null) selectedId = null else { focus.clearFocus(); scope.launch { pager.animateScrollToPage(0) } } }
    val openDiscover = { if (firstHome > 0) scope.launch { pager.animateScrollToPage(-1) } else onDiscover(); Unit }
    val openLibrary = { scope.launch { pager.animateScrollToPage(homePages) }; Unit }

    val dragWindowPage = if (expandedWorkspace && (drag.active || widgetSession != null)) pager.settledPage else pager.currentPage
    val eligibleDragPages = remember(expandedWorkspace, dragWindowPage, visibleHomePages) {
        if (expandedWorkspace && dragWindowPage in 0 until visibleHomePages) {
            setOfNotNull((dragWindowPage - 1).takeIf { it >= -1 }, dragWindowPage)
        } else setOf(dragWindowPage)
    }
    val rawTarget = if (drag.active) drag.destination(drag.pointer, eligibleDragPages)?.target else null
    val target = if (rawTarget is DropTarget.Home && drag.source?.target is DropTarget.Widget) {
        val slot = (drag.source!!.target as DropTarget.Widget).index
        model.placement(slot)?.let {
            DropTarget.Home(adjustedWidgetDropIndex(rawTarget.index, it, drag.source!!.bounds, drag.origin))
        } ?: rawTarget
    } else rawTarget
    val blockedDock = drag.moved && target is DropTarget.Dock &&
        if (drag.source?.folderId != null) state.dock.none { it == null }
        else drag.source?.appId?.let { !canPlaceInDock(state.layout, it) } == true
    val insertionTarget = target.takeIf { drag.moved && !blockedDock }
    val widgetRawTarget = widgetSession?.let { session -> drag.regions.values.firstOrNull {
        it.target is DropTarget.Home && it.page in eligibleDragPages && it.bounds.contains(session.pointer)
    }?.target as? DropTarget.Home }
    val widgetDraft = widgetSession?.let { session -> session.candidate ?: widgetRawTarget?.let { cell ->
        widgetCandidate(state.layout, session.slot, session.targetIndex ?: cell.index, session.span.width, session.span.height)
    } ?: session.targetIndex?.let { widgetCandidate(state.layout, session.slot, it, session.span.width, session.span.height) } }
    val dropHomePage = if (pager.currentPage >= visibleHomePages)
        lastHomePage.coerceIn(0, homePages - 1) else pager.currentPage.coerceIn(0, homePages)
    val previewLayout = remember(state.layout, drag.source, insertionTarget, drag.moved) {
        val id = drag.source?.appId
        when {
            id != null && insertionTarget is DropTarget.Home -> dropApp(state.layout, id, insertionTarget)
            id != null && insertionTarget is DropTarget.Dock -> dropApp(state.layout, id, insertionTarget)
            drag.source?.target is DropTarget.Widget && insertionTarget is DropTarget.Home ->
                moveWidget(state.layout, (drag.source!!.target as DropTarget.Widget).index, insertionTarget.index)
            else -> state.layout
        }
    }
    val edgeWidth = with(LocalDensity.current) { 30.dp.toPx() }
    val edgePointer = widgetSession?.takeIf { it.dragging || it.moving }?.pointer ?: drag.pointer
    val edgeActive = (drag.active && drag.moved) || widgetSession?.dragging == true || widgetSession?.moving == true
    val edge = if (!edgeActive) 0 else dragEdgeDirection(edgePointer, drag.rootBounds, edgeWidth)
    LaunchedEffect(edgeActive, edge) {
        if (edge != 0) while (drag.active || widgetSession?.dragging == true || widgetSession?.moving == true) {
            delay(650)
            val next = (pager.currentPage + edge).coerceIn(0, homePages)
            if ((!drag.active && widgetSession?.dragging != true && widgetSession?.moving != true) || next == pager.currentPage) break
            // Do not key this effect on currentPage: it changes halfway through the
            // animation and would cancel the turn before the inner grid is visible.
            // Once the hold commits a turn, finish its animation while the finger moves
            // into the incoming page. Leaving the edge cancels only the next hold timer.
            scope.launch { pager.animateScrollToPage(next) }.join()
        }
    }
    fun finishDrag(cancelled: Boolean) {
        val source = drag.source ?: return
        val moved = drag.moved
        val rawDestination = if (moved && !cancelled) drag.destination(drag.pointer, eligibleDragPages)?.target else null
        val destination = if (rawDestination is DropTarget.Home && source.target is DropTarget.Widget) {
            model.placement(source.target.index)?.let {
                DropTarget.Home(adjustedWidgetDropIndex(rawDestination.index, it, source.bounds, drag.origin))
            }
                ?: rawDestination
        } else rawDestination
        val changed = when {
            source.folderId != null && destination is DropTarget.Folder ->
                model.addAppToFolder(destination.id, source.appId ?: "")
            source.folderId != null && destination != null && source.appId != null ->
                model.removeAppFromFolder(source.folderId, source.appId, destination)
            destination == DropTarget.Remove -> model.removePlacement(source.target)
            destination is DropTarget.Home && source.target is DropTarget.Widget -> model.moveWidgetTo(source.target.index, destination.index)
            destination != null && source.appId != null -> model.applyDrop(source.appId, destination)
            else -> false
        }
        val returnToLibrary = source.target is DropTarget.Library && source.folderId == null && !changed
        val destinationHomePage = (destination as? DropTarget.Home)?.index?.let(::homeCellPage)
        val currentWindow = pager.settledPage.coerceIn(0, visibleHomePages - 1)
        val page = when (destination) {
            is DropTarget.Home -> if (expandedWorkspace && homeCellPage(destination.index) in eligibleDragPages) currentWindow else destinationHomePage!!
            is DropTarget.Dock -> dropHomePage
            is DropTarget.Widget -> 0
            else -> if (source.target is DropTarget.Library) pager.currentPage else drag.originPage
        }
        scope.launch {
            // Let a new home page compose before removing the temporary drop page.
            withFrameNanos { }
            drag.clear()
            withFrameNanos { }
            pager.scrollToPage(if (returnToLibrary) model.state.value.homePages else page.coerceIn(0, model.state.value.homePages - 1))
            if (!moved && !cancelled) {
                if (source.target is DropTarget.Dock) { dockSlot = source.target.index; sheet = "dock" }
                else if (source.target is DropTarget.Widget) { widgetSlot = source.target.index; widgetMenuSlot = source.target.index }
                else if (source.appId?.let(::isFolderId) == true) openFolderId = source.appId
                else if (source.folderId == null) selectedId = source.appId
            }
        }
    }

    val homeLayer = rememberGraphicsLayer()
    DisposableEffect(homeLayer) {
        homeLayer.compositingStrategy = androidx.compose.ui.graphics.layer.CompositingStrategy.Offscreen
        LiveDiscover.homeLayer = homeLayer
        onDispose { if (LiveDiscover.homeLayer === homeLayer) LiveDiscover.homeLayer = null }
    }
    // The docks' frosted glass shows a blurred copy of the wallpaper as drawn (tilt included).
    val wallpaperLayer = rememberGraphicsLayer()
    val glassScene: androidx.compose.ui.graphics.drawscope.DrawScope.() -> Unit = { drawLayer(wallpaperLayer) }
    // Glass door: while a book-style foldable opens, Home is drawn through frosted panes hinged at the crease.
    val doorSettings by WallpaperStore.load(androidx.compose.ui.platform.LocalContext.current).collectAsState()
    val door = rememberGlassDoor(doorSettings.glassDoor, doorSettings.glassDoorStrength, doorSettings.glassDoorSide)
    Box(Modifier.fillMaxSize().glassDoor(door).graphicsLayer {
        // The feed frame reuses the pager's render nodes in another window. Give Main
        // a complete render target so cross-window damage cannot erase stationary controls.
        compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen
    }.onSizeChanged { LiveDiscover.fullSize = androidx.compose.ui.geometry.Size(it.width.toFloat(), it.height.toFloat()) }.testTag("launcher-root").homeDragInput(drag,
        enabled = sheet.isEmpty() && !showFirstRun && selectedId == null && resizeSlot == null && pager.currentPage >= 0 && !homeSearchOpen,
        page = pager.currentPage, eligiblePages = eligibleDragPages, onStart = {
            focus.clearFocus(); keyboard?.hide(); haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            if (drag.source?.folderId != null) openFolderId = null
            if (drag.source?.target is DropTarget.Library) scope.launch {
                withFrameNanos { }
                pager.scrollToPage(lastHomePage.coerceIn(0, homePages - 1))
            }
        },
        onFinish = { cancelled -> finishDrag(cancelled) })) {
        Box(Modifier.fillMaxSize().drawWithContent {
            wallpaperLayer.record { this@drawWithContent.drawContent() }
            drawLayer(wallpaperLayer)
        }) { DuneWallpaper(live = true) }
        BoxWithConstraints(Modifier.fillMaxSize().graphicsLayer { alpha = 1f - homeSearchProgress }
            .then(if (homeSearchOpen) Modifier.clearAndSetSemantics { } else Modifier)
            .windowInsetsPadding(WindowInsets.safeDrawing)) {
            val wide = maxWidth.value >= 650f
            val preset = if (wide) state.expanded else state.compact
            val density = LocalDensity.current
            val inLibrary = pager.currentPage == visibleHomePages
            var statusHeight by remember { mutableFloatStateOf(0f) }
            val geometry = homeGeometry(maxWidth.value, maxHeight.value, preset, state.labels,
                statusHeight = if (state.verticalStatus) statusHeight + 22f else 0f,
                labelHeight = with(density) { 14.sp.toDp().value } + 6f, inLibrary = inLibrary,
                homeBottomSpace = if (isDefaultHome) 44f else 88f)
            SideEffect {
                layoutGeometry = geometry
                resizePitchX = with(density) { (geometry.gridWidth / GRID_COLUMNS).dp.toPx() }
                resizePitchY = with(density) { minOf((geometry.widgetHeight + 18f) / 2f, geometry.rowHeight).dp.toPx() }
                resizeTopPitch = with(density) { ((geometry.widgetHeight + 18f) / 2f).dp.toPx() }
                resizeAppPitch = with(density) { geometry.rowHeight.dp.toPx() }
            }
            LaunchedEffect(geometry.gridWidth, geometry.widgetHeight, geometry.rowHeight) { resizeSlot = null }
            SideEffect { expandedWorkspace = geometry.expanded }
            LaunchedEffect(geometry.expanded) {
                if (!geometry.expanded) {
                    val sessionTargetsLeading = widgetSession?.let { session ->
                        session.candidate?.page == -1 || session.targetIndex?.let(::homeCellPage) == -1
                    } == true
                    val savedTargetLeading = widgetTargetIndex != Int.MIN_VALUE && homeCellPage(widgetTargetIndex) == -1
                    if (sessionTargetsLeading || savedTargetLeading) {
                        widgetSession = null
                        widgetTargetIndex = Int.MIN_VALUE
                        widgetExactTarget = false
                        widgetPackage = null
                        widgetProfileSerial = null
                        widgetPlacementMessage = null
                        sheet = ""
                    }
                    val dragTouchesLeading = drag.source?.page == -1 ||
                        ((target as? DropTarget.Home)?.index?.let(::homeCellPage) == -1)
                    if (dragTouchesLeading) {
                        drag.clear()
                    }
                }
            }
            val contentHeight = maxHeight
            val panelWidth = maxWidth - geometry.homeWidth.dp
            val side = geometry.dockSide
            // The vertical status rail sits above a side dock; with a bottom dock Android's status bar is shown instead.
            val showStatusRail = state.verticalStatus && side != DockSide.BOTTOM
            // Edit mode's toolbar takes the top edge, so Android's status bar steps aside while editing.
            val hideStatusBar = showStatusRail || SelectionMode.active
            LaunchedEffect(hideStatusBar) { onStatusMode(hideStatusBar) }
            val pagerWidth = if (side == DockSide.BOTTOM) maxWidth else maxWidth - geometry.dockWidth.dp - 28.dp
            // Room to the screen edge past the pages, under the side dock.
            val pastEnd = maxWidth - pagerWidth
            val fullWidth = maxWidth
            val dockAlignment = if (side == DockSide.LEFT) Alignment.TopStart else Alignment.TopEnd
            val sidePadding = if (side == DockSide.LEFT) Modifier.padding(start = 12.dp) else Modifier.padding(end = 12.dp)
            val leftColumnOrigin = (maxWidth / 2f - geometry.gridWidth.dp) / 2f - 16.dp
            val homeStride = panelWidth - leftColumnOrigin
            val bottomSpace = if (isDefaultHome) 44.dp else 88.dp
            val workspaceMotion = if (geometry.expanded) remember(firstHome, visibleHomePages, pagerWidth, homeStride, density) {
                WorkspacePageMotion(firstHome, visibleHomePages, with(density) { pagerWidth.toPx() }, with(density) { homeStride.toPx() })
            } else null
            val dockScroll = rememberScrollState()
            var gestureOriginInRoot by remember { mutableStateOf(Offset.Zero) }
            var gestureOriginInWindow by remember { mutableStateOf(Offset.Zero) }
            val pagerInputEnabled = pager.currentPage in -firstHome..visibleHomePages && !drag.active &&
                widgetSession == null && resizeSlot == null && sheet.isEmpty() && !showFirstRun && selectedId == null &&
                openFolderId == null && createFolderFirstId == null && widgetMenuSlot == null && !homeSearchOpen &&
                launcherActivity.backups.preview == null && !launcherActivity.backups.pickerPending &&
                !launcherActivity.backgrounds.pickerPending && widgets.setupStatus == null &&
                widgets.reconfigureWidgetId == null
            Box(Modifier.fillMaxSize().onGloballyPositioned {
                gestureOriginInRoot = it.boundsInRoot().topLeft
                gestureOriginInWindow = it.boundsInWindow().topLeft
            }.onePageGestures(
                nativePager,
                pageGestures,
                motion = workspaceMotion,
                enabled = pagerInputEnabled,
                // Positive IDs are provider-owned Android views. Leave their vertical
                // stream untouched so scrollable widgets retain native gesture handling.
                // A dock that is already scrolled also gets first use of a downward drag.
                canStartDownwardSwipe = { point ->
                    if (pager.currentPage !in 0 until visibleHomePages) false else {
                        val region = drag.hit(point + gestureOriginInRoot, eligibleDragPages)
                        val rootOnScreen = IntArray(2).also(launcherRootView::getLocationOnScreen)
                        val screenPoint = point + gestureOriginInWindow +
                            Offset(rootOnScreen[0].toFloat(), rootOnScreen[1].toFloat())
                        !(region?.target is DropTarget.Dock && dockScroll.value > 0) &&
                            !nativeWidgetConsumesVerticalGesture(launcherRootView, screenPoint)
                    }
                },
                onDownwardSwipe = launcherActivity::openSystemShade,
                onLeadingOverscroll = if (firstHome == 0 && discoverByGesture) onDiscover else null,
                jelly = jelly,
            ).graphicsLayer { jelly.apply(this) }) {
            // Where the dock sits over the pages, in the pages' own pixels: pages passing under it are frosted there.
            // A left dock keeps a plain frosted wallpaper; its pages do not pass beneath it.
            val bottomDockWidth = geometry.dockWidth.dp
            val dockFrost = remember(side, fullWidth, contentHeight, bottomSpace, geometry, density) {
                with(density) {
                    val patch = when (side) {
                        DockSide.RIGHT -> androidx.compose.ui.geometry.Rect(
                            androidx.compose.ui.geometry.Offset((fullWidth - 12.dp - geometry.dockWidth.dp).toPx(), geometry.dockTop.dp.toPx()),
                            androidx.compose.ui.geometry.Size(geometry.dockWidth.dp.toPx(), geometry.dockHeight.dp.toPx()))
                        DockSide.BOTTOM -> androidx.compose.ui.geometry.Rect(
                            androidx.compose.ui.geometry.Offset(((fullWidth - bottomDockWidth) / 2f).toPx(),
                                (contentHeight - bottomSpace - geometry.bottomDockHeight.dp).toPx()),
                            androidx.compose.ui.geometry.Size(bottomDockWidth.toPx(), geometry.bottomDockHeight.dp.toPx()))
                        DockSide.LEFT -> null
                    }
                    patch?.let { frostPatchEffect(it, 30.dp.toPx(), 10.dp.toPx()) }
                }
            }
            // Under a right dock pages run on to the screen edge while they move.
            val passUnder = if (side == DockSide.RIGHT) pastEnd else 0.dp
            val pagerModifier = Modifier.offset(x = if (side == DockSide.LEFT) geometry.dockWidth.dp + 28.dp else 0.dp)
                .fillMaxHeight().width(pagerWidth)
                .drawWithContent {
                    // The layer's offscreen buffer would cut pages off at this edge; on the unfolded screen it
                    // reaches under the dock so moving pages can pass beneath the glass.
                    val recordWidth = size.width + if (geometry.expanded) pastEnd.toPx() else 0f
                    val recordSize = androidx.compose.ui.unit.IntSize(recordWidth.roundToInt(), size.height.roundToInt())
                    homeLayer.record(this, layoutDirection, recordSize) { this@drawWithContent.drawContent() }
                    drawLayer(homeLayer)
                    LiveDiscover.host.get()?.invalidateFrame()
                }.testTag("app-pager")
                .discoverSwipe(discoverByGesture && firstHome == 0 && pager.currentPage == 0 && !drag.active && sheet.isEmpty() &&
                    !showFirstRun && selectedId == null, onDiscover)
                .onGloballyPositioned {
                    if (firstHome > 0) {
                        val bounds = it.boundsInWindow()
                        LiveDiscover.pagerOrigin = bounds.topLeft
                        val padding = 32 * density.density
                        LiveDiscover.prepare(launcherActivity,
                            android.graphics.Rect((bounds.left + padding).toInt(), (bounds.top + padding).toInt(),
                                (bounds.right - 16 * density.density).toInt(), (bounds.bottom - padding).toInt()), bounds.width)
                    }
                }
                .semantics { stateDescription = if (pager.currentPage == -1) "Discover" else if (pager.currentPage == visibleHomePages) "All apps" else "Home page ${pager.currentPage + 1} of $visibleHomePages" }
            // The dock's frosted wallpaper sits under the pages; the dock above adds only its tint and icons.
            if (dockFrost != null) FrostedBackdrop(RoundedCornerShape(30.dp),
                if (side == DockSide.BOTTOM) Modifier.align(Alignment.BottomCenter).padding(bottom = bottomSpace)
                    .width(bottomDockWidth).height(geometry.bottomDockHeight.dp)
                else Modifier.align(dockAlignment).then(sidePadding).offset(y = geometry.dockTop.dp)
                    .width(geometry.dockWidth.dp).height(geometry.dockHeight.dp), drawScene = glassScene)
            if (geometry.expanded) {
                Box(pagerModifier) {
                    // PagerState remains the source of truth for native Discover progress,
                    // snapping, accessibility state, and programmatic page requests.
                    HorizontalPager(nativePager, Modifier.fillMaxSize(), userScrollEnabled = false,
                        key = { if (it < firstHome) "discover" else if (it - firstHome == visibleHomePages) "library" else "home-${it - firstHome}" }) { }
                    ExpandedWorkspace(
                        nativePager = nativePager, motion = workspaceMotion!!, firstHome = firstHome,
                        visibleHomePages = visibleHomePages, panelWidth = panelWidth, pastEnd = passUnder, dockFrost = dockFrost,
                        contentHeight = contentHeight, bottomSpace = bottomSpace, geometry = geometry,
                        state = state, previewSlots = previewLayout.slots, previewLeadingSlots = previewLayout.leadingSlots,
                        previewWidgetPlacements = previewLayout.widgetPlacements, appsById = appsById,
                        widgets = widgets, drag = drag, target = target, insertionTarget = insertionTarget,
                        libraryQuery = libraryQuery, onLibraryQuery = { libraryQuery = it },
                        onLaunch = { launchOrSelect(it, null) }, onLaunchFrom = launchOrSelect, onPinned = model::setPinned,
                        onTurnOnWork = { model.turnOnWork(it) },
                        onActions = { selectedId = it.id }, onWidget = { widgetSlot = it; widgetMenuSlot = it },
                        onFolder = folderOrSelect,
                        onEmptyWidget = { SelectionMode.enter() },
                        onRefresh = model::refresh,
                    )
                }
            } else Box(pagerModifier) {
                // Like the unfolded workspace: the pager's layer reaches under a right dock so moving pages pass
                // beneath it, frosted where the glass covers them; a bottom dock frosts All apps resting behind it.
                val libraryPosition = (firstHome + visibleHomePages).toFloat()
                HorizontalPager(nativePager, Modifier.wrapContentWidth(Alignment.Start, unbounded = true)
                    .requiredWidth(pagerWidth + passUnder).fillMaxHeight()
                    .revealPastEnd(with(density) { pagerWidth.toPx() }, with(density) { passUnder.toPx() }, dockFrost,
                        frostAtRest = {
                            side == DockSide.BOTTOM && nativePager.currentPage + nativePager.currentPageOffsetFraction > libraryPosition - 1f
                        }) {
                        if (nativePager.isDiscoverBound(firstHome)) 0f else pastEndReveal(nativePager.currentPageOffsetFraction)
                    },
                    contentPadding = PaddingValues(end = passUnder),
                    // Keep adjacent Home panes attached so ordinary back-and-forth paging does
                    // not synchronously inflate provider RemoteViews inside the gesture frame.
                    // Discover is two physical positions before Home 2. Retain both Home
                    // neighbors to avoid reinflating Home 2's RemoteViews during native exit.
                    beyondViewportPageCount = if (firstHome > 0) 2 else 1,
                    userScrollEnabled = !drag.active && resizeSlot == null, flingBehavior = pageFling,
                    key = { if (it < firstHome) "discover" else if (it - firstHome == visibleHomePages) "library" else "home-${it - firstHome}" }) { physicalPage ->
                    val page = physicalPage - firstHome
                    if (page == -1) {
                        DiscoverContent(Modifier.fillMaxSize().padding(start = 16.dp, top = 16.dp, bottom = 16.dp))
                    } else if (page == visibleHomePages) {
                        AppLibrary(state, libraryQuery, { libraryQuery = it }, onLaunch, model::setPinned,
                            onActions = { selectedId = it.id }, modifier = Modifier.fillMaxSize().padding(start = 16.dp,
                                end = if (side == DockSide.RIGHT) 0.dp else 16.dp, top = 16.dp,
                                bottom = bottomSpace).testTag("library-page"),
                            drag = drag, page = visibleHomePages, onLaunchFrom = onLaunchFrom, onTurnOnWork = { model.turnOnWork(it) },
                            // A bottom dock floats over the lists' end, which scrolls clear of it.
                            bottomInset = if (side == DockSide.BOTTOM) geometry.bottomDockHeight.dp + 8.dp else 0.dp)
                    } else {
                        Row(Modifier.fillMaxSize().testTag("home-surface")) {
                            HomePagePane(page, state, previewLayout.slots, previewLayout.leadingSlots, previewLayout.widgetPlacements, appsById, geometry, contentHeight,
                                bottomSpace + geometry.bottomDockHeight.dp + (if (side == DockSide.BOTTOM) BOTTOM_SEARCH_SPACE.dp else 0.dp),
                                widgets, drag, target, insertionTarget, showLargeWidget = false,
                                onLaunch = launchOrSelect, onActions = { selectedId = it.id },
                                onWidget = { widgetSlot = it; widgetMenuSlot = it },
                                onFolder = folderOrSelect,
                                onEmptyWidget = { SelectionMode.enter() },
                                onRefresh = model::refresh)
                        }
                    }
                }
            }
            if (showStatusRail) StatusRail(deviceStatus,
                Modifier.align(dockAlignment).then(sidePadding).offset(y = geometry.contentTop.dp)
                    .width(geometry.dockWidth.dp).onSizeChanged {
                        // The normal rail's 20dp location slot and 3dp gap do not move the dock.
                        statusHeight = (with(density) { it.height.toDp().value } -
                            if (contentHeight < 500.dp) 0f else 23f).coerceAtLeast(0f)
                    },
                compact = contentHeight < 500.dp, iconSize = dockIconSize(geometry.iconSize).dp)
            val glassOpacity = ThemeStore.state.collectAsState().value.glassOpacity / .72f
            val glassShadow = if (WallpaperStore.state.collectAsState().value.glassShadow) 10.dp else 0.dp
            val dockTint = Glass.copy(alpha = (.32f * glassOpacity).coerceIn(0f, 1f))
            if (side == DockSide.BOTTOM) GlassPanel(RoundedCornerShape(30.dp), dockTint, if (dockFrost != null) null else glassScene,
                Modifier.align(Alignment.BottomCenter).padding(bottom = bottomSpace)
                .width(bottomDockWidth).height(geometry.bottomDockHeight.dp)
                .outerShadow(glassShadow, RoundedCornerShape(30.dp)).testTag("dock")) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    DockAppColumn(state.dock, previewLayout.dock, appsById, geometry.dockRowHeight,
                        geometry.dockIconSize, drag, insertionTarget,
                        onLaunch = onLaunchFrom, onChoose = { dockSlot = it; sheet = "dock" }, horizontal = true)
                }
            } else GlassPanel(RoundedCornerShape(30.dp), dockTint,
                // Its frosted wallpaper sits under the pages, which frost themselves where it covers them (see above).
                if (dockFrost != null) null else glassScene,
                Modifier.align(dockAlignment).then(sidePadding).offset(y = geometry.dockTop.dp)
                .width(geometry.dockWidth.dp).height(geometry.dockHeight.dp)
                // The shadow sits outside the offscreen layer below, which would otherwise clip it to the dock's box.
                .outerShadow(glassShadow, RoundedCornerShape(30.dp)).graphicsLayer {
                    // Composite the stationary dock independently of the shared pager layer.
                    compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen
                }.testTag("dock")) {
                // One slot per app row: each dock icon lines up with a row of Home icons.
                Column(Modifier.verticalScroll(dockScroll)) {
                    DockAppColumn(state.dock, previewLayout.dock, appsById, geometry.dockRowHeight,
                        geometry.dockIconSize, drag, insertionTarget,
                        onLaunch = onLaunchFrom, onChoose = { dockSlot = it; sheet = "dock" })
                }
            }
            if (side == DockSide.BOTTOM && !inLibrary && !drag.active && !SelectionMode.active) SearchPill(glassShadow,
                onClick = { openHomeSearch() },
                onLongClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    if (!onGoogleSearch(null)) openHomeSearch()
                },
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = bottomSpace + geometry.bottomDockHeight.dp))
            if (SelectionMode.active && !pageEditor && sheet != "widgets" && widgetSession == null && resizeSlot == null) {
                EditToolbar(state.canUndoEdit, onWidgets = {
                        widgetSlot = model.nextWidgetSlot(); widgetTargetIndex = pager.currentPage.coerceIn(0, homePages - 1) * HOME_CELLS
                        widgetPackage = null; widgetProfileSerial = null; widgetExactTarget = false; sheet = "widgets"
                    }, onSettings = { sheet = "settings" },
                    onPresets = { customizationPage = CustomizationPage.PRESETS; sheet = "settings" },
                    onUndo = { model.undoEdit() },
                    modifier = Modifier.align(Alignment.TopCenter).padding(top = 8.dp))
                // Edit Pages takes the page dots' place; panels sit above a bottom dock rather than over it.
                val panel = SelectionMode.ids.orEmpty().isNotEmpty() || SelectionMode.sizePanel
                Box(Modifier.align(if (side == DockSide.LEFT) Alignment.BottomEnd else Alignment.BottomStart).width(pagerWidth)
                    .padding(start = 16.dp, end = if (side == DockSide.RIGHT) 0.dp else 16.dp,
                        bottom = if (panel && side == DockSide.BOTTOM) bottomSpace + geometry.bottomDockHeight.dp + 8.dp else 6.dp),
                    contentAlignment = Alignment.BottomCenter) {
                    when {
                        SelectionMode.ids.orEmpty().isNotEmpty() -> SelectionPanel(homePages, model) { selectionMessage = it }
                        SelectionMode.sizePanel -> SizePanel(preset, wide, side == DockSide.BOTTOM, geometry.contentTop, model,
                            Modifier.widthIn(max = 640.dp))
                        else -> EditPagesButton({ pageEditor = true })
                    }
                }
            } else Column(Modifier.align(if (side == DockSide.LEFT) Alignment.BottomEnd else Alignment.BottomStart).width(pagerWidth)
                .padding(start = 16.dp, end = if (side == DockSide.RIGHT) 0.dp else 16.dp, bottom = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                if (!isDefaultHome) FilledTonalButton(onClick = { sheet = ""; onMakeDefault() }, Modifier.heightIn(min = 48.dp).testTag("home-setup")) {
                    Icon(Icons.Rounded.Home, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Set as home app")
                }
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    PageDots(visibleHomePages, pager.currentPage, homePages.takeIf { visibleHomePages > homePages },
                        onPage = { index -> scope.launch { pager.animateScrollToPage(index) } },
                        onDiscover = if (discoverByGesture) { { openDiscover() } } else null, onLibrary = { openLibrary() })
                }
            }
            if (!inLibrary && !drag.active && side != DockSide.BOTTOM) Column(Modifier
                .align(if (side == DockSide.LEFT) Alignment.BottomStart else Alignment.BottomEnd).then(sidePadding).padding(bottom = 6.dp)
                .width(geometry.dockWidth.dp), horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val controlSize = dockIconSize(geometry.iconSize).dp
                if (pager.currentPage == -1) CircleControl(Icons.Rounded.ArrowForward, "Back to home", "discover-home", controlSize) { scope.launch { pager.animateScrollToPage(0) } }
                val searchBounds = remember { android.graphics.Rect() }
                Box(Modifier.onGloballyPositioned { searchBounds.set(it.boundsInWindow().toAndroidBounds()) }) {
                    CircleControl(Icons.Rounded.Search, "Search apps", "search", controlSize, onLongClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        if (!onGoogleSearch(searchBounds)) openHomeSearch()
                    }) { openHomeSearch() }
                }
            }
            if (sheet.isNotEmpty() && sheet != "widgets") {
                val activeCustomizationPage = if (sheet == "settings:wallpaper") CustomizationPage.WALLPAPER else customizationPage
                ModalBottomSheet(onDismissRequest = {
                    customizationPage = CustomizationPage.OVERVIEW
                    sheet = ""; widgetPackage = null; widgetExactTarget = false
                }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                    properties = ModalBottomSheetProperties(shouldDismissOnBackPress = false),
                    containerColor = MaterialTheme.colorScheme.surface) {
                    ModalDialogBackHandler {
                        if ((sheet == "settings" || sheet == "settings:wallpaper") &&
                            activeCustomizationPage != CustomizationPage.OVERVIEW) {
                            customizationPage = CustomizationPage.OVERVIEW
                            sheet = "settings"
                        } else {
                            customizationPage = CustomizationPage.OVERVIEW
                            sheet = ""; widgetPackage = null; widgetExactTarget = false
                        }
                    }
                    when (sheet) {
                        "dock" -> AppPicker(shownApps, dockSlot,
                            onSelect = {
                                if (canPlaceInDock(state.layout, it.id)) {
                                    model.applyDrop(it.id, DropTarget.Dock(dockSlot)); sheet = ""
                                }
                            },
                            onClear = { model.removePlacement(DropTarget.Dock(dockSlot)) },
                            onLongClick = { selectedId = it.id; sheet = "" },
                            canSelect = { canPlaceInDock(state.layout, it.id) },
                            blockedHint = if (state.dock.none { it == null }) "Dock full • Move an app out first" else null)
                        "pins" -> Column(Modifier.fillMaxHeight(.9f).imePadding()) {
                            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), horizontalArrangement = Arrangement.End) {
                                TextButton(onClick = { sheet = "" }) { Text("Done") }
                            }
                            AppLibrary(state, pinQuery, { pinQuery = it }, onLaunch, model::setPinned,
                                onActions = { selectedId = it.id; sheet = "" }, editing = true, modifier = Modifier.weight(1f).fillMaxWidth(),
                                onTurnOnWork = { model.turnOnWork(it) })
                        }
                        "settings", "settings:wallpaper" -> CustomizationSheet(state, wide, model, isDefaultHome,
                            page = activeCustomizationPage, onPage = { customizationPage = it; sheet = "settings" },
                            onMakeDefault = { sheet = ""; onMakeDefault() },
                            onClose = { customizationPage = CustomizationPage.OVERVIEW; sheet = "" }, onEditPins = { sheet = "pins" },
                            onWidget = { widgetSlot = it; widgetPackage = null; widgetProfileSerial = null; widgetExactTarget = false; sheet = "widgets" },
                            onAddWidget = { page -> widgetSlot = model.nextWidgetSlot(); widgetTargetIndex = page * HOME_CELLS; widgetPackage = null; widgetProfileSerial = null; widgetExactTarget = false; sheet = "widgets" },
                            onRemoveWidget = widgets::remove,
                            onExportLayout = { sheet = ""; launcherActivity.backups.startExport() },
                            onImportLayout = { sheet = ""; launcherActivity.backups.startImport() },
                            appearance = appearance, onAppearanceMode = onAppearanceMode,
                            onAppearanceManual = onAppearanceManual, onAppearanceDeviceLocation = onAppearanceDeviceLocation,
                            onAppearanceClear = onAppearanceClear,
                            onShadeSetup = { sheet = ""; onShadeSetup() },
                            backgrounds = launcherActivity.backgrounds,
                            onWallpaperPreview = { sheet = ""; onWallpaperPreview() }, homePage = pager.currentPage.coerceIn(0, homePages - 1))
                        "widgetActions" -> model.placement(widgetSlot)?.let { placement ->
                            val topPitch = (geometry.widgetHeight + 18f) / 2f
                            val gridSizing = WidgetGridSizing(GRID_COLUMNS, GRID_ROWS, geometry.gridWidth / GRID_COLUMNS,
                                minOf(topPitch, geometry.rowHeight), maxOf(topPitch, geometry.rowHeight), 10f, 18f,
                                topRowHeightDp = topPitch, appRowHeightDp = geometry.rowHeight)
                            val constraints = widgets.manager.getAppWidgetInfo(placement.id)?.let { widgets.sizing(it, gridSizing) }
                            WidgetActions(placement, constraints,
                                canConfigure = widgets.canReconfigure(placement.id),
                                onConfigure = { widgets.reconfigure(placement.id); sheet = "" },
                                isValid = { x, y -> (x == placement.spanX && y == placement.spanY) || resizeWidget(state.layout, widgetSlot, x, y) != state.layout },
                                onResize = { x, y -> model.resizeWidget(widgetSlot, x, y) },
                                onStartResize = { x, y ->
                                    resizeSlot = widgetSlot; resizeWidth = x; resizeHeight = y
                                    resizeConstraints = constraints; sheet = ""
                                },
                                onMoveToPage = { page ->
                                    (0 until HOME_CELLS).firstOrNull { local ->
                                        widgetCandidate(state.layout, placement.slot, page * HOME_CELLS + local,
                                            placement.spanX, placement.spanY) != null
                                    }?.let { model.moveWidgetTo(placement.slot, page * HOME_CELLS + it) } == true
                                }, homePages = homePages,
                                onReplace = {
                                    widgetPackage = null
                                    widgetProfileSerial = widgets.manager.getAppWidgetInfo(placement.id)?.profile?.let {
                                        launcherActivity.getSystemService(UserManager::class.java).getSerialNumberForUser(it)
                                    }?.takeIf { it >= 0 }
                                    widgetExactTarget = false; sheet = "widgets"
                                },
                                onRemove = { widgets.remove(widgetSlot); sheet = "" },
                                onClose = { sheet = "" })
                        }
                    }
                }
            }
            if (showFirstRun) {
                ModalBottomSheet(
                    onDismissRequest = onFinishFirstRun,
                    sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                    containerColor = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.testTag("first-run-setup"),
                ) {
                    FirstRunSetupSheet(
                        isDefaultHome = isDefaultHome,
                        onMakeDefault = onMakeDefault,
                        onAddWidget = {
                            onFinishFirstRun()
                            widgetSlot = model.nextWidgetSlot()
                            widgetTargetIndex = pager.currentPage.coerceIn(0, homePages - 1) * HOME_CELLS
                            widgetPackage = null
                            widgetProfileSerial = null
                            widgetExactTarget = false
                            sheet = "widgets"
                        },
                        onExplore = onFinishFirstRun,
                        onSkip = onFinishFirstRun,
                    )
                }
            }
            if (sheet == "widgets") {
                val catalogProfiles = remember(state.profiles) { state.profiles.filter { it.isPersonal || it.isWork } }
                val selectedProfile = catalogProfiles.firstOrNull { it.userSerial == widgetProfileSerial }
                    ?: catalogProfiles.firstOrNull { it.isPersonal } ?: AppProfile(0, "Personal", true, false, false, true, true)
                val userManager = remember(launcherActivity) { launcherActivity.getSystemService(UserManager::class.java) }
                val providers = remember(widgetPackage, selectedProfile, sheet, state.apps) {
                    val user = userManager.getUserForSerialNumber(selectedProfile.userSerial)
                    if (user == null || !selectedProfile.available || !selectedProfile.unlocked || selectedProfile.quiet) emptyList()
                    else runCatching { widgetPackage?.let { widgets.providersForPackage(it, user) }
                        ?: widgets.providers(user) }.getOrDefault(emptyList()).filter { provider ->
                        provider.widgetCategory and AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN != 0 &&
                            provider.widgetFeatures and AppWidgetProviderInfo.WIDGET_FEATURE_HIDE_FROM_PICKER == 0
                    }
                }
                val catalog by produceState<List<WidgetCatalogEntry>?>(null, providers, selectedProfile.userSerial, sheet) {
                    value = withContext(Dispatchers.IO) { widgetCatalog(launcherActivity, providers, selectedProfile) }
                }
                val topPitch = (geometry.widgetHeight + 18f) / 2f
                val pickerSizing = remember(geometry) { WidgetGridSizing(GRID_COLUMNS, GRID_ROWS,
                    geometry.gridWidth / GRID_COLUMNS, minOf(topPitch, geometry.rowHeight),
                    maxOf(topPitch, geometry.rowHeight), 10f, 18f,
                    topRowHeightDp = topPitch, appRowHeightDp = geometry.rowHeight) }
                val footprint: (AppWidgetProviderInfo) -> WidgetSpan? = { provider ->
                    widgets.sizing(provider, pickerSizing)?.takeIf { it.minimumFitsGrid }?.preferred
                }
                // Page turns below wait two frames: a new temporary page joins the pager only after the session starts.
                VisualWidgetPicker(catalog, catalogProfiles.ifEmpty { listOf(selectedProfile) }, selectedProfile,
                    onSelectProfile = { widgetProfileSerial = it.userSerial; widgetPlacementMessage = null },
                    onTurnOnWork = { model.turnOnWork(it) }, hiddenForDrag = widgetSession != null,
                    footprint = footprint,
                    onBack = widgetPickerBack,
                    onTap = { provider ->
                        footprint(provider)?.let { preferredSpan ->
                            val existing = model.placement(widgetSlot)
                            val constraints = widgets.sizing(provider, pickerSizing)
                            val span = existing?.let { placement ->
                                WidgetSpan(placement.spanX, placement.spanY).takeIf {
                                    constraints != null && it.width in constraints.minimum.width..constraints.maximum.width &&
                                        it.height in constraints.minimum.height..constraints.maximum.height
                                }
                            } ?: preferredSpan
                            val special = existing?.takeIf { it.row + it.spanY > GRID_ROWS }
                            if (special != null) {
                                widgetSession = WidgetPickerSession(provider, widgetSlot,
                                    WidgetSpan(special.spanX, special.spanY), Offset.Zero,
                                    dragging = false, candidate = special)
                                widgetPlacementMessage = null
                                scope.launch { withFrameNanos { }; withFrameNanos { }; pager.scrollToPage(special.page.coerceAtLeast(0).coerceAtMost(homePages - 1)) }
                                return@let
                            }
                            val requestedIndex = existing?.let { homeCellIndex(it.page, it.row * GRID_COLUMNS + it.column) }
                                ?: widgetTargetIndex.takeUnless { it == Int.MIN_VALUE } ?: 0
                            val requestedPage = homeCellPage(requestedIndex).coerceIn(if (expandedWorkspace) -1 else 0, homePages)
                            val availablePages = (if (expandedWorkspace) -1 else 0)..homePages
                            val autoPages = (listOf(requestedPage) + availablePages.filter { it != requestedPage })
                            val freeIndex = if (existing != null) requestedIndex.takeIf {
                                widgetCandidate(state.layout, widgetSlot, it, span.width, span.height) != null
                            } else if (widgetExactTarget) requestedIndex.takeIf {
                                widgetCandidate(state.layout, widgetSlot, it, span.width, span.height) != null
                            } ?: nearestWidgetAnchor(state.layout, widgetSlot, homeCellPage(requestedIndex),
                                homeCellLocal(requestedIndex), span.width, span.height, covering = true)
                            else autoPages.asSequence().flatMap { page ->
                                (0 until HOME_CELLS).asSequence().map { homeCellIndex(page, it) }
                            }.firstOrNull { widgetCandidate(state.layout, widgetSlot, it, span.width, span.height) != null }
                            val targetIndex = freeIndex ?: requestedIndex
                            widgetSession = WidgetPickerSession(provider, widgetSlot, span, Offset.Zero,
                                dragging = false, targetIndex = targetIndex)
                            widgetPlacementMessage = if (freeIndex == null) WIDGET_NO_ROOM_MESSAGE else null
                            scope.launch { withFrameNanos { }; withFrameNanos { }; pager.scrollToPage(homeCellPage(targetIndex).coerceIn(0, homePages)) }
                        }
                    },
                    onBuiltin = builtin@{ builtinId ->
                        val existing = model.placement(widgetSlot)
                        val special = existing?.takeIf { it.row + it.spanY > GRID_ROWS }
                        val span = existing?.let { WidgetSpan(it.spanX, it.spanY) } ?: WidgetSpan(2, 2)
                        if (special != null) {
                            widgetSession = WidgetPickerSession(null, widgetSlot, span, Offset.Zero,
                                dragging = false, candidate = special, builtinId = builtinId)
                            widgetPlacementMessage = null
                            scope.launch { withFrameNanos { }; withFrameNanos { }; pager.scrollToPage(special.page.coerceAtLeast(0).coerceAtMost(homePages - 1)) }
                            return@builtin
                        }
                        val requested = existing?.let {
                            homeCellIndex(it.page, it.row * GRID_COLUMNS + it.column)
                        } ?: widgetTargetIndex.takeUnless { it == Int.MIN_VALUE } ?: 0
                        val requestedPage = homeCellPage(requested).coerceIn(if (expandedWorkspace) -1 else 0, homePages)
                        val availablePages = (if (expandedWorkspace) -1 else 0)..homePages
                        val candidates = if (model.placement(widgetSlot) != null) sequenceOf(requested)
                            else if (widgetExactTarget) sequenceOf(requested) + sequenceOf(nearestWidgetAnchor(state.layout,
                                widgetSlot, homeCellPage(requested), homeCellLocal(requested), span.width, span.height,
                                covering = true)).filterNotNull()
                            else (listOf(requestedPage) + availablePages.filter { it != requestedPage }).asSequence()
                                .flatMap { page -> (0 until HOME_CELLS).asSequence().map { homeCellIndex(page, it) } }
                        val free = candidates.firstOrNull {
                            widgetCandidate(state.layout, widgetSlot, it, span.width, span.height) != null
                        }
                        widgetSession = WidgetPickerSession(null, widgetSlot, span, Offset.Zero,
                            dragging = false, targetIndex = free ?: requested, builtinId = builtinId)
                        widgetPlacementMessage = if (free == null)
                            "There isn’t room for this card. Choose another page or move an item first." else null
                        scope.launch { withFrameNanos { }; withFrameNanos { }; pager.scrollToPage(homeCellPage(free ?: requested).coerceIn(0, homePages)) }
                    },
                    onDragStart = { provider, point ->
                        footprint(provider)?.let { span ->
                            widgetSession = WidgetPickerSession(provider, widgetSlot, span, point, dragging = true)
                            widgetPlacementMessage = null
                            scope.launch { withFrameNanos { }; withFrameNanos { }; pager.scrollToPage(lastHomePage.coerceIn(0, homePages - 1)) }
                        }
                    },
                    onBuiltinDragStart = { builtinId, point ->
                        widgetSession = WidgetPickerSession(null, widgetSlot, WidgetSpan(2, 2), point, dragging = true, builtinId = builtinId)
                        widgetPlacementMessage = null
                        scope.launch { withFrameNanos { }; withFrameNanos { }; pager.scrollToPage(lastHomePage.coerceIn(0, homePages - 1)) }
                    },
                    onDrag = { point -> widgetSession = widgetSession?.copy(pointer = point) },
                    onDrop = {
                        val session = widgetSession
                        if (session != null && widgetDraft != null) {
                            session.provider?.let { widgets.add(widgetDraft, it, pickerSizing) }
                                ?: session.builtinId?.let { widgets.setBuiltin(widgetDraft.copy(id = it)) }
                            widgetSession = null; sheet = ""; widgetPackage = null
                        } else {
                            leaveTemporaryWidgetPage(); widgetSession = null
                            widgetPlacementMessage = "There isn’t room there. Try another space or page."
                        }
                    },
                    onCancelDrag = {
                        if (widgetSession != null) {
                            leaveTemporaryWidgetPage(); widgetSession = null
                        }
                    })
                widgetSession?.let { session ->
                    val placementDensity = LocalDensity.current
                    val sessionEntry = session.provider?.let { selected -> catalog?.firstOrNull {
                        it.provider.provider == selected.provider && it.provider.profile == selected.profile } }
                    // Legacy overflow replacements are locked to their existing view
                    // bounds and may begin below the canonical six-row grid. They have
                    // no Home-cell address; specialAnchor below is their visual anchor.
                    val candidateIndex = widgetDraft?.takeIf { session.candidate == null }
                        ?.let { homeCellIndex(it.page, it.row * GRID_COLUMNS + it.column) }
                    // While the preview is dragged it shows the spot under it, even a blocked one (drawn red).
                    val visualIndex = candidateIndex ?: session.targetIndex.takeIf { session.moving }
                        ?: widgetRawTarget?.index ?: session.targetIndex
                    val specialAnchor = session.candidate?.let { drag.regions[DropTarget.Widget(session.slot)]?.bounds }
                    // Reads the live session and layout: the tap detector below outlives recompositions.
                    fun retargetWidget(page: Int, local: Int, covering: Boolean, pointer: Offset? = null) {
                        val current = widgetSession ?: return
                        val layout = model.state.value.layout
                        val requested = homeCellIndex(page, local)
                        val fitting = requested.takeIf {
                            widgetCandidate(layout, current.slot, it, current.span.width, current.span.height) != null
                        } ?: nearestWidgetAnchor(layout, current.slot, page, local, current.span.width,
                            current.span.height, covering)
                        widgetSession = current.copy(pointer = pointer ?: current.pointer, targetIndex = fitting ?: requested)
                        widgetPlacementMessage = if (fitting == null) WIDGET_NO_ROOM_MESSAGE else null
                    }
                    // Where this overlay sits on screen: cell bounds are measured from the top of the window, while
                    // the overlay is inset below the status bar.
                    var overlayOrigin by remember { mutableStateOf(Offset.Zero) }
                    val livePages by rememberUpdatedState(eligibleDragPages)
                    // The cells the widget would cover (window coordinates), inset the way Home draws a widget in them.
                    fun footprint(index: Int?, span: WidgetSpan): androidx.compose.ui.geometry.Rect? {
                        index ?: return null
                        val page = homeCellPage(index); val local = homeCellLocal(index)
                        val column = local % GRID_COLUMNS; val row = local / GRID_COLUMNS
                        val first = drag.regions[DropTarget.Home(index)]?.bounds ?: return null
                        val lastRow = (row + span.height - 1).coerceAtMost(GRID_ROWS - 1)
                        val lastColumn = (column + span.width - 1).coerceAtMost(GRID_COLUMNS - 1)
                        val last = drag.regions[DropTarget.Home(homeCellIndex(page, lastRow * GRID_COLUMNS + lastColumn))]?.bounds
                            ?: return null
                        return with(placementDensity) {
                            androidx.compose.ui.geometry.Rect(first.left + 5.dp.toPx(), first.top, last.right - 5.dp.toPx(),
                                maxOf(first.top + 48.dp.toPx(), last.bottom - 18.dp.toPx()))
                        }
                    }
                    // Where the grabbed point of the preview is held while dragging it.
                    var grab by remember(session.slot) { mutableStateOf<Offset?>(null) }
                    // The last spot the dragged preview fitted, where it returns if let go over a blocked one.
                    var lastFit by remember(session.slot) { mutableStateOf<Int?>(null) }
                    fun fitsAt(current: WidgetPickerSession, index: Int?) = index != null &&
                        widgetCandidate(model.state.value.layout, current.slot, index, current.span.width, current.span.height) != null
                    // Follow the dragged preview cell by cell: its top-left cell is the one under its top-left cell's
                    // center, kept so the whole widget stays on the grid. A spot whose cells are taken is shown, in red.
                    fun moveTo(point: Offset) {
                        val current = widgetSession ?: return
                        val held = grab ?: return
                        val cell = current.targetIndex?.let { drag.regions[DropTarget.Home(it)]?.bounds }
                        val probe = point - held + Offset((cell?.width ?: 0f) / 2f, (cell?.height ?: 0f) / 2f)
                        val under = drag.regions.values.firstOrNull {
                            it.target is DropTarget.Home && it.page in livePages && it.bounds.contains(probe)
                        }?.target as? DropTarget.Home
                        if (under == null) { widgetSession = current.copy(pointer = point); return }
                        val local = homeCellLocal(under.index)
                        val column = (local % GRID_COLUMNS).coerceAtMost(GRID_COLUMNS - current.span.width).coerceAtLeast(0)
                        val row = (local / GRID_COLUMNS).coerceAtMost(GRID_ROWS - current.span.height).coerceAtLeast(0)
                        val index = homeCellIndex(homeCellPage(under.index), row * GRID_COLUMNS + column)
                        if (fitsAt(current, index)) lastFit = index
                        widgetSession = current.copy(pointer = point, targetIndex = index)
                        widgetPlacementMessage = null
                    }
                    fun endMove() {
                        val current = widgetSession ?: return
                        grab = null
                        widgetSession = current.copy(moving = false,
                            targetIndex = if (fitsAt(current, current.targetIndex)) current.targetIndex else lastFit ?: current.targetIndex)
                    }
                    // After an edge hold turns the page, place the still-held preview on the new page.
                    LaunchedEffect(pager.settledPage, session.moving) { if (session.moving) moveTo(session.pointer) }
                    Box(Modifier.fillMaxSize().onGloballyPositioned { overlayOrigin = it.positionInRoot() }
                        .testTag("widget-placement-mode")
                        .then(if (!session.dragging && session.candidate == null)
                            Modifier.pointerInput(session.slot, session.span) {
                                detectTapGestures { local ->
                                    val point = local + overlayOrigin
                                    val cell = drag.regions.values.firstOrNull {
                                        it.target is DropTarget.Home && it.page in livePages && it.bounds.contains(point)
                                    }?.target as? DropTarget.Home
                                    cell?.let { retargetWidget(homeCellPage(it.index), homeCellLocal(it.index), covering = true, pointer = point) }
                                }
                            }.pointerInput(session.slot, session.span) {
                                // Drag the preview: it follows the finger and its outline snaps from cell to cell.
                                detectDragGestures(
                                    onDragStart = { local ->
                                        val point = local + overlayOrigin
                                        val current = widgetSession
                                        val rect = current?.let { footprint(it.targetIndex, it.span) }
                                        if (current != null && rect != null && rect.contains(point)) {
                                            grab = point - rect.topLeft
                                            lastFit = current.targetIndex.takeIf { fitsAt(current, it) }
                                            widgetSession = current.copy(moving = true, pointer = point)
                                        }
                                    },
                                    onDrag = { change, _ ->
                                        if (grab != null) { change.consume(); moveTo(change.position + overlayOrigin) }
                                    },
                                    onDragEnd = { endMove() },
                                    onDragCancel = { endMove() },
                                )
                            }
                        else Modifier)) {
                        Box(Modifier.fillMaxWidth().align(Alignment.TopCenter).statusBarsPadding()
                            .padding(start = 12.dp, end = 12.dp, top = 8.dp).testTag("widget-placement-toolbar")) {
                            Row(Modifier.align(Alignment.CenterStart), horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically) {
                                PlacementCircle(Icons.Rounded.ChevronLeft, "Back to widgets", "widget-placement-back", widgetPickerBack)
                                PlacementPill {
                                    if (session.candidate != null) Text("Replace here", Modifier.padding(horizontal = 16.dp)
                                        .testTag("widget-replacement-locked"))
                                    else {
                                        val targetPage = homeCellPage(session.targetIndex ?: 0)
                                        if (!session.dragging) IconButton(
                                            enabled = targetPage > if (expandedWorkspace) -1 else 0, onClick = {
                                            val local = homeCellLocal(session.targetIndex ?: 0)
                                            val page = targetPage - 1
                                            retargetWidget(page, local, covering = false)
                                            scope.launch { pager.animateScrollToPage(page.coerceAtLeast(0)) }
                                        }) { Icon(Icons.Rounded.ChevronLeft, "Previous home page") }
                                        Text("${session.span.width}\u00d7${session.span.height}", fontWeight = FontWeight.SemiBold,
                                            modifier = Modifier.padding(horizontal = if (session.dragging) 16.dp else 0.dp))
                                        if (!session.dragging) IconButton(enabled = targetPage < homePages, onClick = {
                                            val local = homeCellLocal(session.targetIndex ?: 0)
                                            val page = (targetPage + 1).coerceAtMost(homePages)
                                            retargetWidget(page, local, covering = false)
                                            scope.launch { pager.animateScrollToPage(page.coerceAtLeast(0)) }
                                        }) { Icon(Icons.Rounded.ChevronRight, "Next home page") }
                                    }
                                }
                            }
                            Row(Modifier.align(Alignment.CenterEnd), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                PlacementButton("Cancel", "widget-placement-cancel", primary = false) {
                                    leaveTemporaryWidgetPage(); widgetSession = null; sheet = ""; widgetPackage = null
                                }
                                if (!session.dragging) PlacementButton("Add", "widget-placement-apply", primary = true,
                                    enabled = widgetDraft != null) {
                                    widgetDraft?.let { draft ->
                                        val contentSize = specialAnchor?.let { bounds -> with(placementDensity) {
                                            WidgetContentSize(bounds.width.toDp().value, bounds.height.toDp().value)
                                        } }
                                        session.provider?.let { widgets.add(draft, it, pickerSizing, contentSize) }
                                            ?: session.builtinId?.let { widgets.setBuiltin(draft.copy(id = it)) }
                                        widgetSession = null; sheet = ""; widgetPackage = null
                                    }
                                }
                            }
                        }
                        val slotRect = specialAnchor ?: footprint(visualIndex, session.span)
                        val ready = widgetDraft != null
                        val floating = session.dragging || session.moving
                        val previewContent = @Composable {
                            Box(Modifier.fillMaxSize()) {
                                if (sessionEntry != null) WidgetProviderPreview(sessionEntry, session.span,
                                    Modifier.fillMaxSize().padding(5.dp).clip(RoundedCornerShape(18.dp)))
                                else Column(Modifier.align(Alignment.Center).padding(12.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(session.provider?.loadLabel(launcherActivity.packageManager)?.toString()
                                        ?: BUILTIN_WIDGET_NAMES[session.builtinId] ?: "Widget panel", color = Color.White,
                                        textAlign = TextAlign.Center)
                                    Text("${session.span.width} × ${session.span.height}", color = Color.White)
                                }
                            }
                        }
                        // Outline on the grid: exactly the cells the widget will take. White fits; red has no room.
                        if (slotRect != null) {
                            val local = slotRect.translate(-overlayOrigin)
                            Box(Modifier.offset { IntOffset(local.left.roundToInt(), local.top.roundToInt()) }
                                .size(with(placementDensity) { local.width.toDp() }, with(placementDensity) { local.height.toDp() })
                                .testTag("widget-placement-preview")
                                .semantics { stateDescription = if (ready) "Ready to place" else "No room here" }
                                .background(if (ready) Color.White.copy(alpha = if (floating) .14f else .08f)
                                    else Color(0xFFB83B3B).copy(alpha = .3f), RoundedCornerShape(24.dp))
                                .border(2.5.dp, if (ready) Color.White.copy(alpha = .92f) else Color(0xFFFF6B6B), RoundedCornerShape(24.dp))) {
                                if (!floating) previewContent()
                                if (!ready) Text("No room here", Modifier.align(Alignment.Center), color = Color.White,
                                    fontWeight = FontWeight.SemiBold)
                            }
                        }
                        // While moving, the widget itself follows the finger above the grid.
                        if (floating) {
                            val ghostSize = slotRect?.size ?: with(placementDensity) {
                                androidx.compose.ui.geometry.Size(180.dp.toPx(), 120.dp.toPx())
                            }
                            val held = grab ?: Offset(ghostSize.width / 2f, ghostSize.height / 2f)
                            val topLeft = session.pointer - held - overlayOrigin
                            Surface(Modifier.offset { IntOffset(topLeft.x.roundToInt(), topLeft.y.roundToInt()) }
                                .size(with(placementDensity) { ghostSize.width.toDp() }, with(placementDensity) { ghostSize.height.toDp() })
                                .graphicsLayer { alpha = .9f; scaleX = 1.04f; scaleY = 1.04f }
                                .shadow(16.dp, RoundedCornerShape(24.dp)).testTag("widget-placement-ghost")
                                .semantics { stateDescription = if (ready) "Ready to place" else "No room here" },
                                color = if (ready) Glass.copy(alpha = .7f) else Color(0xFFD64545).copy(alpha = .62f),
                                shape = RoundedCornerShape(24.dp),
                                border = if (ready) null else androidx.compose.foundation.BorderStroke(2.5.dp, Color(0xFFFF6B6B))) {
                                previewContent()
                                if (!ready) Box(Modifier.fillMaxSize().background(Color(0xFFB83B3B).copy(alpha = .38f)))
                            }
                        }
                    }
                }
                widgetPlacementMessage?.let { message ->
                    Surface(Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(20.dp),
                        color = Glass, shape = RoundedCornerShape(18.dp)) { Text(message, Modifier.padding(16.dp), color = Ink) }
                }
            }
        }
        if (drag.active) {
            if (drag.moved) {
                if (pager.currentPage > 0) Box(Modifier.align(Alignment.CenterStart).width(6.dp).height(112.dp)
                    .background(Color.White.copy(alpha = if (edge < 0) .9f else .3f), RoundedCornerShape(6.dp)).testTag("drag-edge-left"))
                if (pager.currentPage < homePages) Box(Modifier.align(Alignment.CenterEnd).width(6.dp).height(112.dp)
                    .background(Color.White.copy(alpha = if (edge > 0) .9f else .3f), RoundedCornerShape(6.dp)).testTag("drag-edge-right"))
            }
            appsById[drag.source?.appId]?.let { app ->
                val size = 66.dp
                val px = with(LocalDensity.current) { size.toPx() }
                Image(app.icon.asImageBitmap(), "Moving ${app.label}", Modifier
                    .offset { IntOffset((drag.pointer.x - drag.rootOrigin.x - px / 2).roundToInt(), (drag.pointer.y - drag.rootOrigin.y - px * .65f).roundToInt()) }
                    .size(size).shadow(16.dp, RoundedCornerShape(16.dp)).clip(RoundedCornerShape(16.dp)).testTag("drag-ghost"))
            }
            drag.source?.appId?.let { state.layout.folder(it) }?.let { folder ->
                Surface(Modifier.offset { IntOffset((drag.pointer.x - drag.rootOrigin.x - 42.dp.toPx()).roundToInt(),
                    (drag.pointer.y - drag.rootOrigin.y - 52.dp.toPx()).roundToInt()) }.size(84.dp)
                    .shadow(16.dp, RoundedCornerShape(20.dp)).testTag("folder-drag-ghost"),
                    color = Glass.copy(alpha = .96f), shape = RoundedCornerShape(20.dp)) {
                    Box(contentAlignment = Alignment.Center) { Text(folder.title, color = Ink, textAlign = TextAlign.Center) }
                }
            }
            drag.source?.widgetId?.let { id ->
                val width = 144.dp; val height = 108.dp
                val x = with(LocalDensity.current) { width.toPx() }
                val y = with(LocalDensity.current) { height.toPx() }
                Surface(Modifier.offset { IntOffset((drag.pointer.x - x / 2).roundToInt(), (drag.pointer.y - y * .65f).roundToInt()) }
                    .size(width, height).shadow(16.dp, RoundedCornerShape(24.dp)).testTag("drag-ghost"),
                    color = Glass.copy(alpha = .95f), shape = RoundedCornerShape(24.dp)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Rounded.Widgets, null, tint = Ink)
                        Spacer(Modifier.height(8.dp))
                        Text(remember(id, widgets) { widgetLabel(id, widgets) }, color = Ink, maxLines = 2, textAlign = TextAlign.Center)
                    }
                }
            }
            if (blockedDock) Surface(
                Modifier.align(Alignment.TopCenter).statusBarsPadding()
                    .padding(top = 10.dp, start = 20.dp, end = 100.dp),
                color = Glass.copy(alpha = .96f), shape = RoundedCornerShape(18.dp)
            ) {
                Text("Dock full • Move an app out first",
                    Modifier.padding(horizontal = 16.dp, vertical = 12.dp), color = Ink, fontSize = 13.sp)
            }
            if (drag.moved && drag.source?.target !is DropTarget.Library &&
                drag.source?.appId?.let(::isFolderId) != true) Surface(
                // Keep removal in the right-side control area that is vacated during a drag.
                // A centered target overlaps the expanded workspace's right-hand first cell.
                Modifier.align(when (if (expandedWorkspace) DockSide.RIGHT else state.compact.dockSide) {
                    // A bottom dock fills the lower edge, so removal moves to the top corner.
                    DockSide.BOTTOM -> Alignment.TopEnd; DockSide.LEFT -> Alignment.BottomStart; DockSide.RIGHT -> Alignment.BottomEnd
                }).navigationBarsPadding().statusBarsPadding().padding(horizontal = 12.dp, vertical = 12.dp)
                    .width(sideDockBarWidth((if (expandedWorkspace) state.expanded else state.compact).dockWidth).dp).height(64.dp)
                    .dropRegion(drag, DropTarget.Remove).testTag("remove-drop-target"),
                color = if (target == DropTarget.Remove) Color(0xFFB33B3B) else Glass.copy(alpha = .96f), shape = RoundedCornerShape(24.dp)) {
                Column(Modifier.fillMaxSize().padding(vertical = 6.dp), verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Rounded.DeleteOutline, null)
                    Text("Remove", fontSize = 11.sp, maxLines = 1)
                }
            }
        }
        // Drop regions are in root coordinates; this layer may start below the status bar.
        var overlayOrigin by remember { mutableStateOf(Offset.Zero) }
        Spacer(Modifier.matchParentSize().onGloballyPositioned { overlayOrigin = it.boundsInRoot().topLeft })
        resizeSlot?.let { slot ->
            val placement = model.placement(slot)
            val bounds = drag.regions[DropTarget.Widget(slot)]?.bounds?.translate(-overlayOrigin)
            if (placement != null && bounds != null) {
                val minW = resizeConstraints?.minimum?.width ?: 2
                val minH = resizeConstraints?.minimum?.height ?: 2
                val maxW = minOf(GRID_COLUMNS - placement.column, resizeConstraints?.maximum?.width ?: GRID_COLUMNS)
                val maxH = minOf(GRID_ROWS - placement.row, resizeConstraints?.maximum?.height ?: GRID_ROWS)
                val feasible = placement.page >= -1 && placement.row in 0 until GRID_ROWS &&
                    !(placement.id >= 0 && resizeConstraints == null) && minW <= maxW && minH <= maxH
                val candidate = resizeWidget(state.layout, slot, resizeWidth, resizeHeight)
                val valid = feasible && ((resizeWidth == placement.spanX && resizeHeight == placement.spanY) || candidate != state.layout)
                val widthPx = (bounds.width + (resizeWidth - placement.spanX) * resizePitchX).coerceAtLeast(resizePitchX)
                val density = LocalDensity.current
                fun resizeRowTop(row: Int) = if (row <= 2) row * resizeTopPitch else 2 * resizeTopPitch + (row - 2) * resizeAppPitch
                val heightPx = (resizeRowTop(placement.row + resizeHeight) - resizeRowTop(placement.row) -
                    with(density) { 18.dp.toPx() }).coerceAtLeast(resizePitchY)
                BackHandler { resizeSlot = null }
                Box(Modifier.offset { IntOffset(bounds.left.roundToInt(), bounds.top.roundToInt()) }
                    .size(with(density) { widthPx.toDp() }, with(density) { heightPx.toDp() })
                    .border(3.dp, if (valid) Color.White else Color(0xFFFF6B6B), RoundedCornerShape(24.dp))
                    .testTag("widget-resize-preview-$slot")) {
                    if (!feasible) Text("Move this widget into the six-row grid before resizing.",
                        color = Color.White, modifier = Modifier.align(Alignment.Center).background(Color.Black.copy(alpha = .65f)).padding(8.dp))
                }
                // The corner arc is the drag handle; its touch area is centred on the outline's corner.
                ResizeCornerHandle(valid, Modifier
                    .offset { IntOffset((bounds.left + widthPx - with(density) { 30.dp.toPx() }).roundToInt(),
                        (bounds.top + heightPx - with(density) { 30.dp.toPx() }).roundToInt()) }
                    .size(60.dp).semantics { contentDescription = "Drag to resize widget" }
                    .testTag("widget-resize-handle-$slot")
                    .pointerInput(slot, resizeConstraints) {
                        var dx = 0f; var dy = 0f; var startWidth = resizeWidth; var startHeight = resizeHeight
                        detectDragGestures(onDragStart = {
                            dx = 0f; dy = 0f; startWidth = resizeWidth; startHeight = resizeHeight
                        }, onDrag = { change, amount ->
                            change.consume(); dx += amount.x; dy += amount.y
                            if (feasible && resizeConstraints?.canResizeHorizontally != false)
                                resizeWidth = (startWidth + (dx / resizePitchX).roundToInt()).coerceIn(minW, maxW)
                            if (feasible && resizeConstraints?.canResizeVertically != false)
                                resizeHeight = (startHeight + (dy / resizePitchY).roundToInt()).coerceIn(minH, maxH)
                        })
                    })
                // Cancel · size · Done, centred under the widget (above it when the widget reaches the bottom).
                val gapPx = with(density) { 18.dp.toPx() }
                Box(Modifier.fillMaxSize().layout { measurable, constraints ->
                    val pill = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
                    val below = bounds.top + heightPx + gapPx
                    val y = if (below + pill.height <= constraints.maxHeight - gapPx) below else bounds.top - gapPx - pill.height
                    val x = (bounds.left + widthPx / 2f - pill.width / 2f).coerceIn(gapPx, (constraints.maxWidth - pill.width - gapPx).coerceAtLeast(gapPx))
                    layout(constraints.maxWidth, constraints.maxHeight) { pill.place(x.roundToInt(), y.roundToInt()) }
                }.wrapContentSize()) {
                    ResizePill(resizeWidth, resizeHeight, valid, onCancel = { resizeSlot = null },
                        onDone = { model.resizeWidget(slot, resizeWidth, resizeHeight); resizeSlot = null })
                }
            }
        }
        widgetMenuSlot?.let { slot ->
            val placement = model.placement(slot)
            val bounds = drag.regions[DropTarget.Widget(slot)]?.bounds?.translate(-overlayOrigin)
            if (placement == null || bounds == null) { SideEffect { widgetMenuSlot = null }; return@let }
            BackHandler { widgetMenuSlot = null }
            WidgetMenu(bounds, canConfigure = placement.id >= 0 && widgets.canReconfigure(placement.id),
                onSize = {
                    val gridSizing = layoutGeometry?.let { g ->
                        val topPitch = (g.widgetHeight + 18f) / 2f
                        WidgetGridSizing(GRID_COLUMNS, GRID_ROWS, g.gridWidth / GRID_COLUMNS,
                            minOf(topPitch, g.rowHeight), maxOf(topPitch, g.rowHeight), 10f, 18f,
                            topRowHeightDp = topPitch, appRowHeightDp = g.rowHeight)
                    }
                    resizeConstraints = gridSizing?.let { sizing -> widgets.manager.getAppWidgetInfo(placement.id)?.let { widgets.sizing(it, sizing) } }
                    resizeSlot = slot; resizeWidth = placement.spanX; resizeHeight = placement.spanY
                    widgetMenuSlot = null; SelectionMode.enter()
                },
                onConfigure = { widgets.reconfigure(placement.id); widgetMenuSlot = null },
                onEditHome = { widgetMenuSlot = null; SelectionMode.enter() },
                onReplace = {
                    widgetSlot = slot; widgetMenuSlot = null; widgetPackage = null
                    widgetProfileSerial = widgets.manager.getAppWidgetInfo(placement.id)?.profile?.let {
                        launcherActivity.getSystemService(UserManager::class.java).getSerialNumberForUser(it)
                    }?.takeIf { it >= 0 }
                    widgetExactTarget = false; sheet = "widgets"
                },
                onRemove = { widgets.remove(slot); widgetMenuSlot = null },
                onDismiss = { widgetMenuSlot = null })
        }
        appsById[selectedId]?.let { app ->
            val pinned = state.layout.indexOfShortcut(app.id) != null
            val packageName = app.packageName
            val hasWidgets = packageName.isNotEmpty() && runCatching {
                widgets.providersForPackage(packageName, app.user)
            }.getOrDefault(emptyList()).isNotEmpty()
            ModalBottomSheet(onDismissRequest = { appMoveMenu = false; selectedId = null },
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                properties = ModalBottomSheetProperties(shouldDismissOnBackPress = false)) {
                LauncherAppActionSheet(app, pinned, homePages, appMoveMenu, { appMoveMenu = it },
                    onAddOrRemove = { model.setPinned(app.id, !pinned); selectedId = null },
                    onMoveFirst = { model.move(app.id, -maxOf(HOME_CELLS, state.homeSlots.size)); selectedId = null },
                    onMoveEarlier = { model.move(app.id, -1); selectedId = null },
                    onMoveLater = { model.move(app.id, 1); selectedId = null },
                    onMovePage = { page -> model.applyDrop(app.id, DropTarget.Home(homeCellIndex(page, 0))); selectedId = null },
                    onInfo = { onAppInfo(app); selectedId = null },
                    onWidgets = if (hasWidgets) {{
                        val page = lastHomePage.coerceIn(0, homePages - 1)
                        widgetTargetIndex = homeCellIndex(page, 0); widgetExactTarget = false
                        widgetSlot = model.nextWidgetSlot(); widgetPackage = packageName
                        widgetProfileSerial = app.userSerial; selectedId = null; sheet = "widgets"
                    }} else null,
                    onCreateFolder = { createFolderFirstId = app.id; selectedId = null },
                    onClose = { appMoveMenu = false; selectedId = null },
                    onEdit = { editAppId = app.id; selectedId = null },
                    onCategory = if (app.shortcutId == null) {{ categoryAppId = app.id; selectedId = null }} else null,
                    onHide = { model.setHidden(app.id, true); selectedId = null },
                    onUninstall = if (canUninstall(launcherActivity, app)) {{ onUninstall(app); selectedId = null }} else null)
            }
        }
        if (pageEditor) PageEditorOverlay(state, appsById, model) { pageEditor = false }
        if (SelectionMode.active && !pageEditor && sheet != "widgets" && widgetSession == null && resizeSlot == null) BackHandler { SelectionMode.exit() }
        selectionMessage?.let { message ->
            LaunchedEffect(message) { kotlinx.coroutines.delay(2500); selectionMessage = null }
            Surface(Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(20.dp), color = Glass, shape = RoundedCornerShape(18.dp)) {
                Text(message, Modifier.padding(16.dp), color = Ink) }
        }
        categoryAppId?.let { id -> appsById[id] }?.let { app ->
            val current = state.extras.categoryOverrides[app.id] ?: app.category
            AlertDialog(onDismissRequest = { categoryAppId = null }, title = { Text("Category for ${app.label}") },
                text = { Column(Modifier.verticalScroll(rememberScrollState())) {
                    CATEGORY_TITLES.forEach { (key, title) ->
                        Row(Modifier.fillMaxWidth().clickable {
                            model.updateExtras { e -> e.copy(categoryOverrides = if (key == app.category) e.categoryOverrides - app.id
                                else e.categoryOverrides + (app.id to key)) }
                            categoryAppId = null
                        }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(current == key, null); Spacer(Modifier.width(8.dp)); Text(title)
                        }
                    }
                } },
                confirmButton = { TextButton({ categoryAppId = null }) { Text("Close") } })
        }
        editAppId?.let { id -> appsById[id] }?.let { app ->
            AppEditDialog(app, state.extras.appOverrides[app.id]?.icon, onDismiss = { editAppId = null },
                onSave = { label, icon -> model.setAppOverride(app.id, label, icon); editAppId = null })
        }
        createFolderFirstId?.let { firstId ->
            val first = appsById[firstId]
            AlertDialog(onDismissRequest = { createFolderFirstId = null }, title = { Text("Create folder with ${first?.label ?: "app"}") },
                text = { LazyColumn(Modifier.heightIn(max = 420.dp).testTag("folder-app-picker")) {
                    items(shownApps.filter { it.id != firstId && it.available }, key = { it.id }) { second ->
                        TextButton(onClick = {
                            val preferredPage = state.layout.indexOfShortcut(firstId)?.let(::homeCellPage)
                                ?.takeIf { it >= 0 || expandedWorkspace } ?: lastHomePage.coerceIn(0, homePages - 1)
                            val blocked = state.widgetPlacements.flatMapTo(mutableSetOf()) { it.coveredIndices() }
                            val targetIndex = (0 until HOME_CELLS).map { homeCellIndex(preferredPage, it) }
                                .firstOrNull { it !in blocked && state.layout.slotAt(it) in listOf(null, firstId, second.id) }
                            if (targetIndex != null) model.createFolder(firstId, second.id, targetIndex)
                            createFolderFirstId = null
                        }, modifier = Modifier.fillMaxWidth().testTag("folder-app-${second.id}")) {
                            Text(second.label, Modifier.fillMaxWidth())
                        }
                    }
                } }, confirmButton = { TextButton(onClick = { createFolderFirstId = null }) { Text("Cancel") } })
        }
        openFolderId?.let { id ->
            state.folders.firstOrNull { it.id == id }?.let { folder ->
                val blocked = state.widgetPlacements.flatMapTo(mutableSetOf()) { it.coveredIndices() }
                val destinationPages = (if (expandedWorkspace) listOf(-1) else emptyList()) + (0 until homePages)
                val homeDestinations = destinationPages.mapNotNull { destinationPage ->
                    (0 until HOME_CELLS).map { homeCellIndex(destinationPage, it) }
                        .firstOrNull { it !in blocked && state.layout.slotAt(it) == null }
                }
                FolderPanel(folder, appsById, drag, pager.currentPage, homeDestinations,
                    dockVacancies = state.dock.indices.filter { state.dock[it] == null },
                    onDismiss = { openFolderId = null }, onRename = { model.renameFolder(id, it) },
                    onLaunch = onLaunchFrom,
                    onMoveOut = { appId, destination ->
                        if (model.removeAppFromFolder(id, appId, destination)) openFolderId = model.folder(id)?.id
                    })
            } ?: LaunchedEffect(id) { openFolderId = null }
        }
        launcherActivity.backups.preview?.let { preview ->
            LayoutRestorePreview(preview, onRestore = {
                launcherActivity.backups.applyImport(); sheet = ""
            }, onCancel = launcherActivity.backups::cancelImport)
        }
        if (launcherActivity.backups.pickerPending) AlertDialog(onDismissRequest = {},
            title = { Text("Layout document") },
            text = { Text("The system document picker is still open. Return to it to finish, or cancel this operation.") },
            confirmButton = { TextButton(onClick = { launcherActivity.backups.resumePendingPicker() },
                modifier = Modifier.testTag("backup-picker-resume")) { Text("Resume") } },
            dismissButton = { TextButton(onClick = launcherActivity.backups::cancelImport,
                modifier = Modifier.testTag("backup-picker-cancel")) { Text("Cancel") } })
        if (launcherActivity.backgrounds.pickerPending && !launcherActivity.backgrounds.loading) AlertDialog(
            onDismissRequest = {}, title = { Text("Background photo") },
            text = { Text("The photo picker was interrupted. Resume choosing a photo, or cancel and keep the current background.") },
            confirmButton = { TextButton(onClick = launcherActivity.backgrounds::choosePhoto,
                modifier = Modifier.testTag("background-picker-resume")) { Text("Resume") } },
            dismissButton = { TextButton(onClick = launcherActivity.backgrounds::cancelPendingSelection,
                modifier = Modifier.testTag("background-picker-cancel")) { Text("Cancel") } })
        (launcherActivity.backups.errorMessage ?: launcherActivity.backups.successMessage)?.let { message ->
            AlertDialog(onDismissRequest = launcherActivity.backups::clearMessage,
                title = { Text(if (launcherActivity.backups.errorMessage != null) "Layout backup problem" else "Layout backup") },
                text = { Text(message) }, confirmButton = { TextButton(onClick = launcherActivity.backups::clearMessage) { Text("OK") } })
        }
        widgets.failureMessage?.let { message ->
            AlertDialog(onDismissRequest = widgets::clearFailure, title = { Text("Widget not added") },
                text = { Text(message, Modifier.testTag("widget-bind-error")) },
                confirmButton = { TextButton(onClick = widgets::clearFailure) { Text("OK") } })
        }
        if (widgets.pendingPlacement != null && widgets.setupStatus != null) {
            AlertDialog(onDismissRequest = {}, title = { Text("Finish widget setup") },
                text = { Text("The widget is waiting at its chosen spot. Finish setup to add it, or cancel to remove the placeholder.") },
                confirmButton = { Button(onClick = widgets::finishPendingSetup,
                    modifier = Modifier.semantics { contentDescription = "Continue widget setup" }) { Text("Finish setup") } },
                dismissButton = { TextButton(onClick = { leaveTemporaryWidgetPage(); widgets.cancelPendingSetup() },
                    modifier = Modifier.semantics { contentDescription = "Cancel widget setup" }) { Text("Cancel") } })
        }
        widgets.reconfigureWidgetId?.let {
            AlertDialog(onDismissRequest = {}, title = { Text("Widget settings") },
                text = { Text("Widget settings were interrupted. Resume configuration, or cancel and keep the widget unchanged.") },
                confirmButton = { Button(onClick = widgets::finishPendingReconfigure,
                    modifier = Modifier.testTag("widget-reconfigure-resume")) { Text("Resume") } },
                dismissButton = { TextButton(onClick = widgets::cancelPendingReconfigure,
                    modifier = Modifier.testTag("widget-reconfigure-cancel")) { Text("Cancel") } })
        }
        }
        // Outside Home's container, so the fade that hides Home leaves this alone.
        // Search fades in while rising from below as Home fades out; Cancel plays it in reverse.
        if (homeSearchOpen || homeSearchProgress > 0f) HomeSearchPanel(state, homeSearchQuery, { homeSearchQuery = it },
            onClose = { homeSearchOpen = false },
            onLaunchFrom = { app, bounds -> onLaunchFrom(app, bounds); homeSearchOpen = false },
            onActions = { selectedId = it.id },
            modifier = Modifier.fillMaxSize().graphicsLayer {
                alpha = homeSearchProgress
                translationY = (1f - homeSearchProgress) * size.height / 3f
            }.windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 8.dp))
    }
}

@Composable
private fun ExpandedWorkspace(
    nativePager: androidx.compose.foundation.pager.PagerState,
    motion: WorkspacePageMotion,
    firstHome: Int,
    visibleHomePages: Int,
    panelWidth: Dp,
    /** Room past the workspace's right edge (under the dock) that moving pages may pass through. */
    pastEnd: Dp,
    /** Blurs the patch of moving pages under the dock's glass. */
    dockFrost: androidx.compose.ui.graphics.RenderEffect?,
    contentHeight: Dp,
    bottomSpace: Dp,
    geometry: HomeGeometry,
    state: LauncherState,
    previewSlots: List<String?>,
    previewLeadingSlots: List<String?>,
    previewWidgetPlacements: List<WidgetPlacement>,
    appsById: Map<String, AppEntry>,
    widgets: WidgetController,
    drag: HomeDragState,
    target: DropTarget?,
    insertionTarget: DropTarget?,
    libraryQuery: String,
    onLibraryQuery: (String) -> Unit,
    onLaunch: (AppEntry) -> Unit,
    onLaunchFrom: (AppEntry, android.graphics.Rect?) -> Unit,
    onPinned: (String, Boolean) -> Unit,
    onTurnOnWork: (Long) -> Unit,
    onActions: (AppEntry) -> Unit,
    onWidget: (Int) -> Unit,
    onFolder: (String) -> Unit,
    onEmptyWidget: (Int) -> Unit,
    onRefresh: () -> Unit,
) {
    val density = LocalDensity.current
    val viewportWidth = motion.pageWidth
    val stride = motion.homeStride
    val initialHomeOrigin = with(density) { panelWidth.toPx() }
    val homePaneWidth = with(density) { (geometry.gridWidth + 16f).dp.toPx() }
    val stateHolder = androidx.compose.runtime.saveable.rememberSaveableStateHolder()
    val visibleHomes by remember(nativePager, motion, firstHome, visibleHomePages, initialHomeOrigin, homePaneWidth) {
        derivedStateOf(structuralEqualityPolicy()) {
            val physicalPosition = nativePager.currentPage + nativePager.currentPageOffsetFraction
            val scroll = motion.offset(physicalPosition)
            val intersectingHomes = (0 until visibleHomePages).filter { page ->
                val start = initialHomeOrigin + page * stride
                start + homePaneWidth > scroll && start < scroll + viewportWidth
            }
            val nearestLogicalPage = nativePager.currentPage - firstHome
            // While Discover is current, keep the initial Home pair cached. Otherwise Home 2
            // is recreated midway through the first native exit and provider inflation can
            // block the gesture frame even though that pane began offscreen.
            val retentionAnchor = nearestLogicalPage.coerceAtLeast(0)
            (intersectingHomes + (retentionAnchor - 1..retentionAnchor + 1))
                .filter { it in 0 until visibleHomePages }.distinct().sorted()
        }
    }
    val place: Modifier.(Float) -> Modifier = { x ->
        offset {
            val physicalPosition = nativePager.currentPage + nativePager.currentPageOffsetFraction
            IntOffset((x - motion.offset(physicalPosition)).roundToInt(), 0)
        }
    }
    val showDiscover by remember(nativePager, firstHome) {
        derivedStateOf(structuralEqualityPolicy()) {
            firstHome > 0 && nativePager.currentPage + nativePager.currentPageOffsetFraction <= firstHome + .25f
        }
    }
    val leadingX = initialHomeOrigin - stride
    val showLeading by remember(nativePager, motion, firstHome, panelWidth, leadingX, homePaneWidth) {
        derivedStateOf(structuralEqualityPolicy()) {
            val physicalPosition = nativePager.currentPage + nativePager.currentPageOffsetFraction
            val scroll = motion.offset(physicalPosition)
            panelWidth.value > 0f && physicalPosition - firstHome < 1f &&
                leadingX - scroll + homePaneWidth > 0f
        }
    }
    val libraryPhysicalPage = firstHome + visibleHomePages
    val showLibrary by remember(nativePager, libraryPhysicalPage) {
        derivedStateOf(structuralEqualityPolicy()) {
            nativePager.currentPage + nativePager.currentPageOffsetFraction >= libraryPhysicalPage - 1.25f
        }
    }

    val pastEndPx = with(density) { pastEnd.toPx() }
    val viewport = with(density) { viewportWidth.toDp() }
    // The pages' layer runs on to the screen edge so moving pages can pass under the dock (and be frosted there);
    // panes therefore take explicit widths rather than filling it.
    Box(Modifier.fillMaxSize().testTag("expanded-workspace")) {
    Box(Modifier.wrapContentWidth(Alignment.Start, unbounded = true).requiredWidth(viewport + pastEnd).fillMaxHeight()
        .revealPastEnd(viewportWidth, pastEndPx, dockFrost) {
            if (nativePager.isDiscoverBound(firstHome)) 0f else pastEndReveal(nativePager.currentPageOffsetFraction)
        }) {
        if (showDiscover) {
            key("discover-pane") {
                Box(Modifier.place(-viewportWidth).width(viewport).fillMaxHeight()) {
                    DiscoverContent(Modifier.fillMaxSize().padding(start = 16.dp, top = 16.dp, bottom = 16.dp))
                }
            }
        }

        if (showLeading) {
            key("expanded-leading-home") {
                Box(Modifier.place(leadingX).width((geometry.gridWidth + 16f).dp).fillMaxHeight()
                    .testTag("expanded-leading-home")) {
                    HomePagePane(
                        -1, state, previewSlots, previewLeadingSlots, previewWidgetPlacements, appsById, geometry, contentHeight, bottomSpace,
                        widgets, drag, target, insertionTarget, showLargeWidget = true,
                        onLaunch = onLaunchFrom, onActions = onActions, onWidget = onWidget,
                        onFolder = onFolder, onEmptyWidget = onEmptyWidget, onRefresh = onRefresh,
                        modifier = Modifier,
                    )
                }
            }
        }

        visibleHomes.forEach { page ->
            key("expanded-home-$page") {
                stateHolder.SaveableStateProvider("expanded-home-$page") {
                    Box(Modifier.place(initialHomeOrigin + page * stride)
                        .width((geometry.gridWidth + 16f).dp).fillMaxHeight()) {
                        HomePagePane(
                            page, state, previewSlots, previewLeadingSlots, previewWidgetPlacements, appsById, geometry, contentHeight, bottomSpace,
                            widgets, drag, target, insertionTarget, showLargeWidget = page > 0,
                            onLaunch = onLaunchFrom, onActions = onActions, onWidget = onWidget,
                            onFolder = onFolder,
                            onEmptyWidget = onEmptyWidget,
                            onRefresh = onRefresh,
                        )
                    }
                }
            }
        }

        if (showLibrary) {
            key("library-pane") {
                Box(Modifier.place((visibleHomePages - 1) * stride + viewportWidth).width(viewport).fillMaxHeight()) {
                    AppLibrary(state, libraryQuery, onLibraryQuery, onLaunch, onPinned,
                        onActions = onActions,
                        modifier = Modifier.fillMaxSize().padding(start = 16.dp, top = 16.dp, bottom = bottomSpace)
                            .testTag("library-page"),
                        drag = drag, page = visibleHomePages, onLaunchFrom = onLaunchFrom, onTurnOnWork = onTurnOnWork)
                }
            }
        }
    }
    }
}

@Composable
private fun HomePagePane(
    page: Int,
    state: LauncherState,
    previewSlots: List<String?>,
    previewLeadingSlots: List<String?>,
    previewWidgetPlacements: List<WidgetPlacement>,
    appsById: Map<String, AppEntry>,
    geometry: HomeGeometry,
    contentHeight: Dp,
    bottomSpace: Dp,
    widgets: WidgetController,
    drag: HomeDragState,
    target: DropTarget?,
    insertionTarget: DropTarget?,
    showLargeWidget: Boolean,
    onLaunch: (AppEntry, android.graphics.Rect?) -> Unit,
    onActions: (AppEntry) -> Unit,
    onWidget: (Int) -> Unit,
    onFolder: (String) -> Unit,
    onEmptyWidget: (Int) -> Unit = {},
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val homeScroll = rememberScrollState()
    // Edit mode's toolbar needs the top 60dp; Home slides down under it instead of being covered.
    val editTop by animateDpAsState(maxOf(geometry.contentTop, if (SelectionMode.active) 60f else 0f).dp, label = "edit top")
    var paneBounds by remember { mutableStateOf(androidx.compose.ui.geometry.Rect.Zero) }
    val pageStart = homeCellIndex(page, 0)
    val backgroundTarget = (pageStart until pageStart + HOME_CELLS).firstOrNull { index ->
        state.layout.slotAt(index) == null && state.widgetPlacements.none { index in it.coveredIndices() }
    } ?: pageStart
    val verticalEdge = with(LocalDensity.current) { 42.dp.toPx() }
    LaunchedEffect(drag.active, page, paneBounds) {
        while (drag.active) {
            val pointer = drag.pointer
            val amount = when {
                // Only app and folder drags scroll the page to reach rows below; a dragged widget leaves the page still.
                drag.source?.widgetId != null -> 0f
                !paneBounds.contains(pointer) -> 0f
                pointer.y < paneBounds.top + verticalEdge && homeScroll.canScrollBackward -> -18f
                pointer.y > paneBounds.bottom - verticalEdge && homeScroll.canScrollForward -> 18f
                else -> 0f
            }
            if (amount != 0f) homeScroll.scrollBy(amount)
            delay(16)
        }
    }
    Box(modifier.testTag("home-page-$page")
        .semantics {
            onLongClick("Home options") {
                if (!drag.active) onEmptyWidget(backgroundTarget)
                !drag.active
            }
        }
        .onGloballyPositioned { paneBounds = it.boundsInRoot() }
        .width((geometry.gridWidth + 16f).dp)
        .height((contentHeight - bottomSpace).coerceAtLeast(0.dp))) {
        Box(Modifier.width(16.dp).fillMaxHeight().testTag("home-options-margin-$page")
            .pointerInput(backgroundTarget, drag.active) {
                detectTapGestures(onLongPress = {
                    if (!drag.active) onEmptyWidget(backgroundTarget)
                })
            })
        Column(Modifier.offset(x = 16.dp).width(geometry.gridWidth.dp).fillMaxHeight()
            .verticalScroll(homeScroll).padding(top = editTop, bottom = 8.dp)) {
            SharedHomeGrid(page, state.homeSlots, state.leadingSlots, previewSlots, previewLeadingSlots, previewWidgetPlacements,
                appsById, geometry, state.labels, widgets, drag, target,
                folders = state.folders, onLaunch = onLaunch, onActions = onActions, onWidget = onWidget,
                onFolder = onFolder, onEmptyWidget = onEmptyWidget)
            if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth().padding(16.dp))
            if (state.error != null) Text(state.error, color = Color.White,
                modifier = Modifier.clickable(onClick = onRefresh).padding(12.dp))
        }
    }
}

@Composable
private fun CircleControl(icon: ImageVector, label: String, tag: String, visualSize: Dp, onLongClick: (() -> Unit)? = null,
    action: () -> Unit) {
    // The touch target may be larger than the circle; the ripple stays on the circle and the shadow is not clipped.
    val interaction = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    Box(Modifier.size(visualSize.coerceAtLeast(48.dp))
        .combinedClickable(interaction, null, onClick = action, onLongClick = onLongClick, onLongClickLabel = onLongClick?.let { "Search Google" },
            role = androidx.compose.ui.semantics.Role.Button).testTag(tag), contentAlignment = Alignment.Center) {
        val shadow = if (WallpaperStore.state.collectAsState().value.glassShadow) 8.dp else 0.dp
        Box(Modifier.size(visualSize).testTag("$tag-visual").outerShadow(shadow, CircleShape).clip(CircleShape)
            .indication(interaction, androidx.compose.material3.ripple()).glassDepth(CircleShape, Glass.copy(alpha = .22f)),
            contentAlignment = Alignment.Center) {
            Icon(icon, label, tint = Color.White, modifier = Modifier.size(22.dp))
        }
    }
}

@Composable
private fun SharedHomeGrid(
    page: Int,
    savedSlots: List<String?>,
    savedLeadingSlots: List<String?>,
    previewSlots: List<String?>,
    previewLeadingSlots: List<String?>,
    widgetPlacements: List<WidgetPlacement>,
    appsById: Map<String, AppEntry>,
    geometry: HomeGeometry,
    labels: Boolean,
    widgets: WidgetController,
    drag: HomeDragState,
    target: DropTarget?,
    folders: List<FolderEntry>,
    onLaunch: (AppEntry, android.graphics.Rect?) -> Unit,
    onActions: (AppEntry) -> Unit,
    onWidget: (Int) -> Unit,
    onFolder: (String) -> Unit,
    onEmptyWidget: (Int) -> Unit,
) {
    val rowHeight = geometry.rowHeight
    val iconSize = geometry.iconSize
    val pageStart = homeCellIndex(page, 0)
    val pageRange = pageStart until pageStart + HOME_CELLS
    fun savedAt(index: Int) = if (page == -1) savedLeadingSlots.getOrNull(homeCellLocal(index)) else savedSlots.getOrNull(index)
    fun previewAt(index: Int) = if (page == -1) previewLeadingSlots.getOrNull(homeCellLocal(index)) else previewSlots.getOrNull(index)
    fun savedIndexOf(id: String) = if (page == -1) savedLeadingSlots.indexOf(id).takeIf { it >= 0 }?.let { homeCellIndex(-1, it) }
        else savedSlots.indexOf(id).takeIf { it >= 0 }
    fun previewIndexOf(id: String) = if (page == -1) previewLeadingSlots.indexOf(id).takeIf { it >= 0 }?.let { homeCellIndex(-1, it) }
        else previewSlots.indexOf(id).takeIf { it >= 0 }
    val draggedId = drag.source?.appId
    val homeTarget = (target as? DropTarget.Home)?.index
    val source = drag.source?.target as? DropTarget.Home
    val draggedPreviewIndex = draggedId?.let(::previewIndexOf) ?: -1
    val hiddenIndex = when {
        !drag.active || !drag.moved -> null
        homeTarget != null -> draggedPreviewIndex.takeIf { it >= 0 }
        source != null && target !is DropTarget.Dock -> draggedPreviewIndex.takeIf { it >= 0 }
        else -> null
    }
    val dimDragged = drag.active && !drag.moved && source != null
    val pending = widgets.pendingPlacement?.takeIf { it.page == page }
    val pendingIsReplacement = pending != null && widgetPlacements.any { it.slot == pending.slot }
    val pageWidgets = widgetPlacements.filter { it.page == page } + listOfNotNull(pending?.takeUnless { pendingIsReplacement })
    val renderedRows = maxOf(GRID_ROWS, pageWidgets.maxOfOrNull { it.row + it.spanY } ?: GRID_ROWS)
    val coveredCells = pageWidgets.flatMap { it.coveredIndices() }.toSet()
    val editing = SelectionMode.active
    val sizing = editing && SelectionMode.sizePanel
    val topPitch = (geometry.widgetHeight + 18f) / 2f
    fun rowTop(row: Int) = if (row <= 2) row * topPitch else geometry.widgetHeight + 18f + (row - 2) * rowHeight
    BoxWithConstraints(Modifier.fillMaxWidth().height(rowTop(renderedRows).dp)) {
        val density = LocalDensity.current
        val cellWidth = maxWidth / 4
        val cellWidthPx = with(density) { cellWidth.toPx() }
        val rowHeightPx = with(density) { rowHeight.dp.toPx() }

        repeat(HOME_CELLS) { localIndex ->
            val globalIndex = pageStart + localIndex
            val cell = DropTarget.Home(globalIndex)
            val savedId = savedAt(globalIndex)
            val savedApp = appsById[savedId]
            val savedFolder = folders.firstOrNull { it.id == savedId }
            val previewId = previewAt(globalIndex)
            val highlighted = drag.active && target == cell
            val gap = hiddenIndex == globalIndex
            val row = localIndex / GRID_COLUMNS
            val cellHeight = rowTop(row + 1) - rowTop(row)
            Box(Modifier.offset(x = cellWidth * (localIndex % GRID_COLUMNS), y = rowTop(row).dp)
                .width(cellWidth).height(cellHeight.dp).testTag("home-cell-$globalIndex")
                .dropRegion(drag, cell, savedApp?.id ?: savedFolder?.id, page)
                // No press ripple: holding an empty cell should not shade it on the way into edit mode.
                .combinedClickable(interactionSource = remember { MutableInteractionSource() }, indication = null,
                    onClick = { savedFolder?.let { onFolder(it.id) } },
                    onLongClick = { if (savedId == null && !drag.active) onEmptyWidget(globalIndex) })
                .background(if (highlighted) Glass.copy(alpha = .25f) else Color.Transparent, RoundedCornerShape(16.dp))
                .border(if (highlighted) 2.dp else 0.dp,
                    if (highlighted) Color.White.copy(alpha = .8f) else Color.Transparent, RoundedCornerShape(16.dp)),
                contentAlignment = Alignment.TopCenter) {
                if (drag.active && drag.source?.appId != null && (gap || previewId == null)) Box(
                    Modifier.size(iconSize.dp).testTag(if (gap) "drag-gap-home-$globalIndex" else "empty-home-slot-$globalIndex")
                        .background(Glass.copy(alpha = if (gap) .16f else .08f), RoundedCornerShape(18.dp))
                        .border(if (gap) 2.dp else 1.dp, Color.White.copy(alpha = if (gap) .55f else .3f), RoundedCornerShape(18.dp)))
                // Edit mode outlines every open cell; Size outlines all cells in blue to show the grid being sized.
                else if (editing && globalIndex !in coveredCells && (sizing || previewId == null)) Box(
                    Modifier.offset(y = if (sizing) (-3).dp else 0.dp).size(iconSize.dp + if (sizing) 6.dp else 0.dp)
                        .testTag(if (sizing) "size-cell-$globalIndex" else "edit-slot-$globalIndex")
                        .border(if (sizing) 2.dp else 1.dp, if (sizing) EditBlue else Color.White.copy(alpha = .3f),
                            RoundedCornerShape((iconSize * .27f).dp)))
            }
        }

        val ids = (if (page == -1) savedLeadingSlots + previewLeadingSlots
            else savedSlots.slicePage(pageRange) + previewSlots.slicePage(pageRange)).filterNotNull().distinct()
        ids.forEach { id ->
            val savedIndex = savedIndexOf(id) ?: -1
            val previewIndex = previewIndexOf(id) ?: -1
            val renderIndex = previewIndex.takeIf { it in pageRange } ?: savedIndex.takeIf { it in pageRange } ?: return@forEach
            val app = appsById[id] ?: return@forEach
            key(id) {
                val localIndex = renderIndex - pageStart
                val row = localIndex / GRID_COLUMNS
                val animatedOffset by animateIntOffsetAsState(
                    IntOffset(((localIndex % GRID_COLUMNS) * cellWidthPx).roundToInt(), with(density) { rowTop(row).dp.toPx() }.roundToInt()),
                    label = "home insertion $id",
                )
                val visible = previewIndex in pageRange && renderIndex != hiddenIndex
                val opacity by animateFloatAsState(
                    if (dimDragged && id == draggedId) .28f else 1f,
                    label = "home insertion visibility $id",
                )
                Box(Modifier.offset { animatedOffset }.width(cellWidth).height(rowHeight.dp)
                    .alpha(opacity).testTag("home-app-$id"), contentAlignment = Alignment.TopCenter) {
                    if (visible) AppTile(app, iconSize, labels,
                        onClick = { onLaunch(app, it) }, onLongClick = { onActions(app) })
                }
            }
        }
        folders.forEach { folder ->
            val savedIndex = savedIndexOf(folder.id) ?: -1
            val previewIndex = previewIndexOf(folder.id) ?: -1
            val renderIndex = previewIndex.takeIf { it in pageRange } ?: savedIndex.takeIf { it in pageRange } ?: return@forEach
            val localIndex = renderIndex - pageStart
            val row = localIndex / GRID_COLUMNS
            val x = cellWidth * (localIndex % GRID_COLUMNS)
            val y = rowTop(row).dp
            FolderTile(folder, appsById, iconSize, labels, drag, page,
                Modifier.offset(x = x, y = y).width(cellWidth).height(rowHeight.dp)
                    .testTag("home-folder-${folder.id}"), onClick = { onFolder(folder.id) })
        }
        pageWidgets.forEach { placement ->
            key("widget-${placement.slot}") {
                val x = cellWidth * placement.column + 5.dp
                val width = (cellWidth * placement.spanX - 10.dp).coerceAtLeast(1.dp)
                val y = rowTop(placement.row)
                val height = (rowTop(placement.row + placement.spanY) - y - 18f).coerceAtLeast(48f)
                if (placement == pending) Surface(Modifier.offset(x = x, y = y.dp).width(width).height(height.dp)
                    .testTag("widget-pending-${placement.slot}").semantics(mergeDescendants = true) {
                        contentDescription = "Pending ${widgets.pendingProvider?.shortClassName ?: "widget"}"
                    }, color = Glass.copy(alpha = .72f),
                    shape = RoundedCornerShape(24.dp), border = androidx.compose.foundation.BorderStroke(2.dp, Color.White)) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 3.dp)
                        Spacer(Modifier.height(8.dp)); Text("Finish widget setup", color = Ink)
                    }
                } else MovableWidget(placement.id, placement.slot, widgets, drag, target,
                    Modifier.offset(x = x, y = y.dp).width(width).height(height.dp), page = page) { onWidget(placement.slot) }
            }
        }
    }
}

private fun <T> List<T>.slicePage(range: IntRange): List<T> =
    if (isEmpty() || range.first >= size) emptyList() else subList(range.first, minOf(range.last + 1, size))

@Composable
private fun AppPicker(apps: List<AppEntry>, dockSlot: Int?, onSelect: (AppEntry) -> Unit, onClear: () -> Unit,
    onLongClick: (AppEntry) -> Unit, canSelect: (AppEntry) -> Boolean = { true }, blockedHint: String? = null) {
    var query by rememberSaveable { mutableStateOf("") }
    val filtered = remember(apps, query) { apps.filter { it.label.contains(query.trim(), ignoreCase = true) } }
    Column(Modifier.fillMaxWidth().fillMaxHeight(.88f).padding(horizontal = 20.dp).imePadding()) {
        Text(if (dockSlot == null) "Your apps" else "Dock position ${dockSlot + 1}", style = MaterialTheme.typography.headlineSmall)
        OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth().padding(vertical = 16.dp).testTag("search-field"),
            placeholder = { Text("Search apps") }, leadingIcon = { Icon(Icons.Rounded.Search, null) }, singleLine = true,
            trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Rounded.Close, "Clear search") } }, shape = RoundedCornerShape(20.dp))
        if (dockSlot != null) TextButton(onClick = onClear) { Text("Leave this position empty") }
        if (blockedHint != null) Text(blockedHint, color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(bottom = 8.dp).testTag("dock-full-guidance"))
        LazyColumn(Modifier.weight(1f)) {
            if (filtered.isEmpty()) item { Text("No apps found", Modifier.padding(vertical = 24.dp)) }
            items(filtered, key = { it.id }) { app ->
                val enabled = canSelect(app)
                Row(Modifier.fillMaxWidth().testTag("picker-app-${app.id}")
                    .combinedClickable(enabled = enabled, onClick = { onSelect(app) }, onLongClick = { onLongClick(app) })
                    .alpha(if (enabled) 1f else .45f)
                    .padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Image(app.icon.asImageBitmap(), null, Modifier.size(44.dp).clip(LocalIconShape.current))
                    Text(app.label, Modifier.padding(start = 16.dp).weight(1f), maxLines = 2)
                    if (dockSlot != null && enabled) Icon(Icons.Rounded.Add, "Choose ${app.label}")
                }
            }
        }
    }
}

@Composable
private fun WidgetActions(
    placement: WidgetPlacement,
    constraints: WidgetSpanConstraints?,
    canConfigure: Boolean,
    onConfigure: () -> Unit,
    isValid: (Int, Int) -> Boolean,
    onResize: (Int, Int) -> Unit,
    onStartResize: (Int, Int) -> Unit,
    onMoveToPage: (Int) -> Boolean,
    homePages: Int,
    onReplace: () -> Unit,
    onRemove: () -> Unit,
    onClose: () -> Unit,
) {
    val sheetMaxHeight = with(LocalDensity.current) {
        (LocalWindowInfo.current.containerSize.height * .88f).toDp()
    }
    var width by remember(placement.slot, placement.spanX) { mutableIntStateOf(placement.spanX) }
    var height by remember(placement.slot, placement.spanY) { mutableIntStateOf(placement.spanY) }
    val minWidth = constraints?.minimum?.width ?: 2
    val minHeight = constraints?.minimum?.height ?: 2
    val maxWidth = minOf(GRID_COLUMNS - placement.column, constraints?.maximum?.width ?: GRID_COLUMNS)
    val maxHeight = minOf(GRID_ROWS - placement.row, constraints?.maximum?.height ?: GRID_ROWS)
    val feasible = placement.page >= -1 && placement.row in 0 until GRID_ROWS &&
        !(placement.id >= 0 && constraints == null) && minWidth <= maxWidth && minHeight <= maxHeight
    val valid = feasible && isValid(width, height)
    Column(Modifier.fillMaxWidth().heightIn(max = sheetMaxHeight).verticalScroll(rememberScrollState())
        .padding(horizontal = 24.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Widget options", Modifier.weight(1f), style = MaterialTheme.typography.headlineSmall)
            IconButton(onClick = onClose) { Icon(Icons.Rounded.Close, "Close widget options") }
        }
        if (canConfigure) ActionRow(Icons.Rounded.Settings, "Widget settings", onConfigure,
            Modifier.testTag("widget-settings-${placement.slot}"))
        Text("Resize", style = MaterialTheme.typography.titleMedium)
        Button(enabled = feasible, onClick = { onStartResize(width, height) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Resize on Home") }
        if (!feasible) Text("Move this widget into the six-row grid before resizing.", color = MaterialTheme.colorScheme.error)
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
            repeat(homePages) { page -> TextButton(onClick = { onMoveToPage(page) },
                modifier = Modifier.testTag("widget-move-${placement.slot}-page-$page")) { Text("Move to page ${page + 1}") } }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Width", Modifier.weight(1f))
            IconButton(enabled = constraints?.canResizeHorizontally != false,
                onClick = { if (feasible) width = (width - 1).coerceAtLeast(minWidth) }) {
                Icon(Icons.Rounded.Remove, "Decrease widget width")
            }
            Text("$width columns", Modifier.width(88.dp), textAlign = TextAlign.Center)
            IconButton(enabled = constraints?.canResizeHorizontally != false,
                onClick = { if (feasible) width = (width + 1).coerceAtMost(maxWidth) }) {
                Icon(Icons.Rounded.Add, "Increase widget width")
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Height", Modifier.weight(1f))
            IconButton(enabled = constraints?.canResizeVertically != false,
                onClick = { if (feasible) height = (height - 1).coerceAtLeast(minHeight) }) {
                Icon(Icons.Rounded.Remove, "Decrease widget height")
            }
            Text("$height rows", Modifier.width(88.dp), textAlign = TextAlign.Center)
            IconButton(enabled = constraints?.canResizeVertically != false,
                onClick = { if (feasible) height = (height + 1).coerceAtMost(maxHeight) }) {
                Icon(Icons.Rounded.Add, "Increase widget height")
            }
        }
        Text("Sizes that overlap another item are ignored.", style = MaterialTheme.typography.bodySmall)
        if (!valid) Text("That size overlaps another item or extends beyond the page.", color = MaterialTheme.colorScheme.error)
        Button(enabled = valid, onClick = { onResize(width, height); onClose() },
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Apply size") }
        ActionRow(Icons.Rounded.FindReplace, "Replace", onReplace)
        HorizontalDivider()
        ActionRow(Icons.Rounded.DeleteOutline, "Remove", onRemove, tint = MaterialTheme.colorScheme.error)
        Spacer(Modifier.height(12.dp))
    }
}

/** Toward Discover: that page lives in its own window, so nothing passes under the dock on the way. */
private fun androidx.compose.foundation.pager.PagerState.isDiscoverBound(firstHome: Int) = currentPage < firstHome
