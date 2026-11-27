@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.jake.duolauncher

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInWindow

private val LabelShadow = TextStyle(shadow = Shadow(Color.Black.copy(alpha = .55f), Offset(0f, 1f), 3f))

@Composable
internal fun AppLibrary(
    state: LauncherState, query: String, onQuery: (String) -> Unit,
    onLaunch: (AppEntry) -> Unit, onPin: (String, Boolean) -> Unit, onActions: (AppEntry) -> Unit,
    modifier: Modifier = Modifier, editing: Boolean = false,
    drag: HomeDragState? = null, page: Int? = null,
    onLaunchFrom: (AppEntry, android.graphics.Rect?) -> Unit = { app, _ -> onLaunch(app) },
    onTurnOnWork: (Long) -> Unit = {},
    /** Room at the end of the lists for a bottom dock they scroll behind. */
    bottomInset: androidx.compose.ui.unit.Dp = 0.dp,
) {
    val glass = !editing
    val palette = LocalDuoPalette.current
    val ink = if (glass) Ink else MaterialTheme.colorScheme.onSurface
    val pinned = remember(state.homeSlots, state.leadingSlots) {
        (state.homeSlots.asSequence() + state.leadingSlots.asSequence()).filterNotNull().toSet()
    }
    val hasWork = state.profiles.any { it.isWork } || state.apps.any { it.isWork }
    var showWork by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val selectedProfile = if (showWork) state.profiles.firstOrNull { it.isWork } else state.profiles.firstOrNull { it.isPersonal }
    LaunchedEffect(showWork, selectedProfile?.available, selectedProfile?.quiet) {
        listState.scrollToItem(0)
    }
    val hidden = state.extras.hiddenApps
    var revealHidden by remember { mutableStateOf(false) }
    val visibleApps = remember(state.apps, query, showWork, hasWork, hidden) {
        state.apps.filter { it.id !in hidden && (!hasWork || it.isWork == showWork) && it.label.contains(query.trim(), true) }
    }
    val hiddenApps = remember(state.apps, hidden) { state.apps.filter { it.id in hidden } }
    val activity = androidx.activity.compose.LocalActivity.current
    val context = androidx.compose.ui.platform.LocalContext.current
    val lockHidden by PrivacyStore.lockHidden(context).collectAsState()
    val groups = remember(visibleApps) {
        visibleApps.groupBy {
            it.label.firstOrNull()?.takeIf(Char::isLetter)?.uppercaseChar()?.toString() ?: "#"
        }
    }
    val workPaused = selectedProfile?.takeIf { showWork && !it.available }
    val toggleHidden = {
        if (revealHidden) revealHidden = false
        else if (lockHidden && activity != null) authenticate(activity, "Show hidden apps") { revealHidden = true }
        else revealHidden = true
    }
    val categoriesMode by RecentApps.categoriesMode(context).collectAsState()
    val workChips: @Composable () -> Unit = {
        if (hasWork) Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = !showWork, onClick = { showWork = false }, label = { Text("Personal") })
            FilterChip(selected = showWork, onClick = { showWork = true }, label = { Text("Work") })
        }
    }

    if (!editing && categoriesMode) {
        CategoryLibrary(state, query, onQuery, visibleApps, groups, hiddenApps, revealHidden, toggleHidden, workPaused,
            pinned, drag, page, listState, onPin, onLaunchFrom, onActions, onTurnOnWork, workChips, modifier, bottomInset)
        return
    }

    Surface(modifier, shape = RoundedCornerShape(24.dp),
        color = if (glass) Glass.copy(alpha = .48f) else MaterialTheme.colorScheme.surface,
        contentColor = ink,
        border = if (glass) BorderStroke(1.dp, Color.White.copy(alpha = .38f)) else null) {
        Column(Modifier.background(Brush.verticalGradient(if (glass)
            listOf(Color.White.copy(alpha = .09f), Color.Transparent) else listOf(Color.Transparent, Color.Transparent)))
            .padding(horizontal = 16.dp).padding(top = 18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (editing) "Choose home apps" else "All apps", Modifier.weight(1f), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Medium)
                Text(if (editing) "${pinned.size} pinned" else "${visibleApps.size}", color = ink, fontSize = 12.sp)
            }
            workChips()
            OutlinedTextField(query, onQuery, Modifier.fillMaxWidth().padding(vertical = 12.dp).testTag(if (editing) "pin-search" else "library-search"),
                placeholder = { Text("Search apps") }, singleLine = true, shape = RoundedCornerShape(16.dp),
                leadingIcon = { Icon(Icons.Rounded.Search, null) },
                trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { onQuery("") }) { Icon(Icons.Rounded.Close, "Clear search") } },
                colors = if (glass) OutlinedTextFieldDefaults.colors(
                    focusedTextColor = ink, unfocusedTextColor = ink, cursorColor = ink,
                    focusedContainerColor = Color.White.copy(alpha = .18f), unfocusedContainerColor = Color.White.copy(alpha = .12f),
                    focusedBorderColor = Color.White.copy(alpha = .8f), unfocusedBorderColor = Color.White.copy(alpha = .45f),
                    focusedPlaceholderColor = ink, unfocusedPlaceholderColor = ink,
                    focusedLeadingIconColor = ink, unfocusedLeadingIconColor = ink,
                    focusedTrailingIconColor = ink, unfocusedTrailingIconColor = ink,
                ) else OutlinedTextFieldDefaults.colors())
            val letterChip = if (glass) (if (palette.dark) Color(0xFF314852) else Color(0xFFB7CBD3)) else MaterialTheme.colorScheme.surfaceContainer
            LazyColumn(Modifier.weight(1f).testTag("all-apps-list"), state = listState,
                contentPadding = PaddingValues(bottom = 12.dp + bottomInset)) {
                libraryEntries(groups, workPaused, onTurnOnWork, if (state.loading) "Loading apps…" else "No apps found",
                    letterChip = letterChip,
                    letterInk = ink, divider = glass,
                    hiddenApps = if (!editing && query.isBlank()) hiddenApps else emptyList(), revealHidden = revealHidden, onToggleHidden = toggleHidden,
                ) { app, hiddenEntry -> LibraryRow(app, pinned, editing && !hiddenEntry, if (hiddenEntry) null else drag, page, onPin, onLaunchFrom, onActions, hiddenEntry) }
            }
        }
    }
}

/** Letter-grouped rows shared by the list view and category search: work notice, groups, then hidden apps. */
private fun LazyListScope.libraryEntries(
    groups: Map<String, List<AppEntry>>, workPaused: AppProfile?, onTurnOnWork: (Long) -> Unit, emptyText: String,
    letterChip: Color, letterInk: Color, divider: Boolean,
    hiddenApps: List<AppEntry>, revealHidden: Boolean, onToggleHidden: () -> Unit,
    row: @Composable (AppEntry, Boolean) -> Unit,
) {
    if (workPaused != null) item("work-paused") {
        Column(Modifier.fillMaxWidth().padding(vertical = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(if (workPaused.quiet) "Work apps are paused" else "Work profile is unavailable")
            if (workPaused.quiet) Button(onClick = { onTurnOnWork(workPaused.userSerial) },
                Modifier.padding(top = 10.dp).testTag("turn-on-work")) { Text("Turn on work apps") }
        }
    }
    if (groups.isEmpty()) item("library-empty") { Text(emptyText, Modifier.padding(vertical = 20.dp)) }
    groups.forEach { (letter, entries) ->
        stickyHeader(key = "heading-$letter") {
            Row(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                // An opaque small chip prevents text from showing through the sticky letter.
                Box(Modifier.size(width = 32.dp, height = 28.dp).background(letterChip, RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) {
                    Text(letter, color = letterInk, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                }
                if (divider) HorizontalDivider(Modifier.weight(1f).padding(start = 10.dp), color = Color.White.copy(alpha = .24f))
            }
        }
        items(entries, key = { it.id }) { app -> row(app, false) }
    }
    if (hiddenApps.isNotEmpty()) {
        item("hidden-apps-toggle") {
            TextButton(onClick = onToggleHidden, Modifier.fillMaxWidth().testTag("hidden-apps-toggle"),
                colors = ButtonDefaults.textButtonColors(contentColor = LocalContentColor.current)) {
                Icon(if (revealHidden) Icons.Rounded.VisibilityOff else Icons.Rounded.Lock, null)
                Spacer(Modifier.width(8.dp))
                Text(if (revealHidden) "Hide ${hiddenApps.size} hidden apps" else "Show ${hiddenApps.size} hidden apps")
            }
        }
        if (revealHidden) items(hiddenApps, key = { "hidden-${it.id}" }) { app -> row(app, true) }
    }
}

/**
 * All apps as category folders laid straight on the wallpaper, with a pill search field on top.
 * Focusing the field (or Home's search button) switches to the letter list until Cancel.
 */
@Composable
private fun CategoryLibrary(
    state: LauncherState, query: String, onQuery: (String) -> Unit, visibleApps: List<AppEntry>,
    letterGroups: Map<String, List<AppEntry>>, hiddenApps: List<AppEntry>, revealHidden: Boolean, onToggleHidden: () -> Unit,
    workPaused: AppProfile?, pinned: Set<String>, drag: HomeDragState?, page: Int?,
    listState: androidx.compose.foundation.lazy.LazyListState,
    onPin: (String, Boolean) -> Unit, onLaunchFrom: (AppEntry, android.graphics.Rect?) -> Unit,
    onActions: (AppEntry) -> Unit, onTurnOnWork: (Long) -> Unit, workChips: @Composable () -> Unit, modifier: Modifier,
    bottomInset: androidx.compose.ui.unit.Dp,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val focusManager = LocalFocusManager.current
    val focus = remember { FocusRequester() }
    var focused by remember { mutableStateOf(false) }
    var openCategory by rememberSaveable { mutableStateOf<String?>(null) }
    // Revealing hidden apps also shows the list, where they appear at the end.
    val searching = focused || query.isNotEmpty() || revealHidden
    val recentIds by RecentApps.recents(context).collectAsState()
    val libraryGroups = remember(visibleApps, state.extras.categoryOverrides, recentIds) {
        groupLibrary(visibleApps, state.extras.categoryOverrides, recentIds, System.currentTimeMillis())
    }
    val cancel = { onQuery(""); focusManager.clearFocus(); focused = false; if (revealHidden) onToggleHidden() }
    val extras = rememberSearchExtras(query, onOpened = { cancel() })
    if (searching) BackHandler { cancel() }
    else if (openCategory != null) BackHandler { openCategory = null }
    CompositionLocalProvider(LocalContentColor provides Color.White) {
        Column(modifier.padding(end = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                LibrarySearchPill(query, onQuery, searching, focus, { focused = it }, Modifier.weight(1f))
                if (searching) TextButton(cancel, Modifier.testTag("library-search-cancel"),
                    colors = ButtonDefaults.textButtonColors(contentColor = Color.White)) { Text("Cancel") }
            }
            workChips()
            Spacer(Modifier.height(12.dp))
            val open = libraryGroups.firstOrNull { it.key == openCategory }
            when {
                searching || workPaused != null -> LazyColumn(Modifier.weight(1f).fillMaxWidth()
                    .background(Color.Black.copy(alpha = .34f), RoundedCornerShape(20.dp)).padding(horizontal = 12.dp)
                    .testTag("all-apps-list"), state = listState, contentPadding = PaddingValues(bottom = 12.dp + bottomInset)) {
                    // Revealed from the grid: only the hidden apps, so they are not buried at the end of the list.
                    val onlyHidden = revealHidden && !focused && query.isEmpty() && workPaused == null
                    if (onlyHidden) {
                        item("hidden-title") { Text("Hidden apps", Modifier.padding(top = 14.dp, bottom = 6.dp), style = MaterialTheme.typography.titleMedium) }
                        items(hiddenApps, key = { "hidden-${it.id}" }) { app -> LibraryRow(app, pinned, false, null, page, onPin, onLaunchFrom, onActions, true) }
                    } else {
                        if (workPaused == null) searchExtras(extras)
                        libraryEntries(letterGroups, workPaused, onTurnOnWork, if (state.loading) "Loading apps…" else "No apps found",
                        letterChip = Color.White.copy(alpha = .2f), letterInk = Color.White, divider = true,
                        hiddenApps = if (query.isBlank()) hiddenApps else emptyList(), revealHidden = revealHidden,
                        onToggleHidden = onToggleHidden,
                    ) { app, hiddenEntry -> LibraryRow(app, pinned, false, if (hiddenEntry) null else drag, page, onPin, onLaunchFrom, onActions, hiddenEntry) }
                    }
                }
                open != null -> CategoryContents(open, drag, page, onLaunchFrom, onActions, { openCategory = null },
                    Modifier.weight(1f).padding(bottom = bottomInset))
                else -> LazyVerticalGrid(GridCells.Adaptive(140.dp), Modifier.weight(1f).testTag("library-categories"),
                    horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(14.dp),
                    contentPadding = PaddingValues(bottom = 12.dp + bottomInset)) {
                    items(libraryGroups.size, key = { libraryGroups[it].key }) { index ->
                        CategoryTile(libraryGroups[index], drag, page, onLaunchFrom, onActions, { openCategory = libraryGroups[index].key })
                    }
                    if (hiddenApps.isNotEmpty()) item(key = "hidden-apps-toggle", span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                        TextButton(onToggleHidden, Modifier.fillMaxWidth().testTag("hidden-apps-toggle"),
                            colors = ButtonDefaults.textButtonColors(contentColor = Color.White)) {
                            Icon(Icons.Rounded.Lock, null); Spacer(Modifier.width(8.dp)); Text("Show ${hiddenApps.size} hidden apps")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LibrarySearchPill(query: String, onQuery: (String) -> Unit, active: Boolean, focus: FocusRequester,
    onFocus: (Boolean) -> Unit, modifier: Modifier, tag: String = "library-search") {
    BasicTextField(query, onQuery, modifier.height(44.dp).focusRequester(focus).onFocusChanged { onFocus(it.isFocused) }
        .testTag(tag), singleLine = true,
        textStyle = TextStyle(color = Color.White, fontSize = 15.sp), cursorBrush = SolidColor(Color.White),
        decorationBox = { field ->
            Row(Modifier.fillMaxSize().glassDepth(CircleShape, Color.White.copy(alpha = .14f)).padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = if (active) Arrangement.Start else Arrangement.Center) {
                Icon(Icons.Rounded.Search, null, Modifier.size(18.dp), tint = Color.White.copy(alpha = .85f))
                Spacer(Modifier.width(8.dp))
                Box(if (active) Modifier.weight(1f) else Modifier, contentAlignment = Alignment.CenterStart) {
                    if (query.isEmpty()) Text(if (active) "Search apps" else "All Apps", color = Color.White.copy(alpha = .8f), fontSize = 15.sp)
                    field()
                }
            }
        })
}

/**
 * App search opened from Home's search button: the same focused pill, Cancel and results as the All apps search,
 * shown over the wallpaper while Home steps aside. Cancel, Back or opening a result closes it.
 */
@Composable
internal fun HomeSearchPanel(
    state: LauncherState, query: String, onQuery: (String) -> Unit, onClose: () -> Unit,
    onLaunchFrom: (AppEntry, android.graphics.Rect?) -> Unit, onActions: (AppEntry) -> Unit, modifier: Modifier,
) {
    val focusManager = LocalFocusManager.current
    val keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    val focus = remember { FocusRequester() }
    var focused by remember { mutableStateOf(false) }
    val close = { focusManager.clearFocus(); keyboard?.hide(); onClose() }
    val hidden = state.extras.hiddenApps
    val pinned = remember(state.homeSlots, state.leadingSlots) {
        (state.homeSlots.asSequence() + state.leadingSlots.asSequence()).filterNotNull().toSet()
    }
    val groups = remember(state.apps, query, hidden) {
        state.apps.filter { it.id !in hidden && it.label.contains(query.trim(), true) }.groupBy {
            it.label.firstOrNull()?.takeIf(Char::isLetter)?.uppercaseChar()?.toString() ?: "#"
        }
    }
    val extras = rememberSearchExtras(query, onOpened = close)
    LaunchedEffect(Unit) {
        // The field is on screen as the panel rises; retry briefly until it really holds focus.
        repeat(20) {
            androidx.compose.runtime.withFrameNanos { }
            runCatching { focus.requestFocus() }
            androidx.compose.runtime.withFrameNanos { }
            if (focused) return@LaunchedEffect
            kotlinx.coroutines.delay(50)
        }
    }
    BackHandler { close() }
    CompositionLocalProvider(LocalContentColor provides Color.White) {
        // Swallow touches on the empty parts so nothing reaches Home underneath.
        Column(modifier.clickable(remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, null) { }
            .testTag("home-search")) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                LibrarySearchPill(query, onQuery, true, focus, { focused = it }, Modifier.weight(1f), tag = "home-search-field")
                TextButton(close, Modifier.testTag("home-search-cancel"),
                    colors = ButtonDefaults.textButtonColors(contentColor = Color.White)) { Text("Cancel") }
            }
            Spacer(Modifier.height(12.dp))
            LazyColumn(Modifier.weight(1f).fillMaxWidth()
                .background(Color.Black.copy(alpha = .34f), RoundedCornerShape(20.dp)).padding(horizontal = 12.dp)
                .testTag("home-search-list"), contentPadding = PaddingValues(bottom = 12.dp)) {
                searchExtras(extras)
                libraryEntries(groups, null, {}, if (state.loading) "Loading apps…" else "No apps found",
                    letterChip = Color.White.copy(alpha = .2f), letterInk = Color.White, divider = true,
                    hiddenApps = emptyList(), revealHidden = false, onToggleHidden = {},
                ) { app, _ -> LibraryRow(app, pinned, false, null, null, { _, _ -> }, onLaunchFrom, onActions) }
            }
        }
    }
}

/** How a category tile fills its 2×2 grid: four apps show as large icons; more show three plus a 2×2 preview of the next four. */
internal fun <T> categoryTileSlots(apps: List<T>): Pair<List<T>, List<T>> =
    if (apps.size <= 4) apps to emptyList() else apps.take(3) to apps.drop(3).take(4)

@Composable
private fun CategoryTile(group: LibraryGroup<AppEntry>, drag: HomeDragState?, page: Int?,
    onLaunchFrom: (AppEntry, android.graphics.Rect?) -> Unit, onActions: (AppEntry) -> Unit, onOpen: () -> Unit) {
    val (large, preview) = categoryTileSlots(group.apps)
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(24.dp))
            .glassDepth(RoundedCornerShape(24.dp), Color.White.copy(alpha = .16f)).clickable(onClick = onOpen)
            .testTag("library-group-${group.key}"), shape = RoundedCornerShape(24.dp), color = Color.Transparent) {
            BoxWithConstraints(Modifier.padding(8.dp)) {
                val cell = maxWidth / 2
                val icon = cell * .76f
                Column {
                    repeat(2) { row ->
                        Row {
                            repeat(2) { column ->
                                val index = row * 2 + column
                                Box(Modifier.size(cell), contentAlignment = Alignment.Center) {
                                    if (index < large.size) LibraryIcon(large[index], icon, drag, page, onLaunchFrom, onActions)
                                    else if (index == 3 && preview.isNotEmpty()) PreviewGrid(preview, icon, onOpen)
                                }
                            }
                        }
                    }
                }
            }
        }
        Text(group.title, Modifier.padding(top = 6.dp).clickable(onClick = onOpen), fontSize = 12.sp, maxLines = 1,
            overflow = TextOverflow.Ellipsis, style = LabelShadow, color = Color.White)
    }
}

@Composable
private fun PreviewGrid(apps: List<AppEntry>, size: Dp, onOpen: () -> Unit) {
    val small = (size - 4.dp) / 2
    Column(Modifier.size(size).clip(RoundedCornerShape(12.dp)).clickable(onClick = onOpen)
        .semantics { onLongClick("Open category") { onOpen(); true } }, verticalArrangement = Arrangement.SpaceBetween) {
        apps.chunked(2).forEach { pair ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                pair.forEach { app ->
                    Image(app.icon.asImageBitmap(), app.label, Modifier.size(small).clip(LocalIconShape.current))
                }
            }
        }
    }
}

@Composable
private fun LibraryIcon(app: AppEntry, size: Dp, drag: HomeDragState?, page: Int?,
    onLaunchFrom: (AppEntry, android.graphics.Rect?) -> Unit, onActions: (AppEntry) -> Unit) {
    val bounds = remember { android.graphics.Rect() }
    val dragModifier = if (drag != null) Modifier.dropRegion(drag, DropTarget.Library(app.id), app.id, page) else Modifier
    Image(app.icon.asImageBitmap(), app.label, Modifier.size(size).then(dragModifier)
        .onGloballyPositioned { bounds.set(it.boundsInWindow().toAndroidBounds()) }
        .clip(LocalIconShape.current).testTag("library-app-${app.id}")
        .then(if (drag == null) Modifier.combinedClickable(onClick = { onLaunchFrom(app, bounds) }, onLongClick = { onActions(app) })
            else Modifier.clickable { onLaunchFrom(app, bounds) }.semantics { onLongClick("App options") { onActions(app); true } }))
}

/** An opened category: every app in it as an icon grid with names. */
@Composable
private fun CategoryContents(group: LibraryGroup<AppEntry>, drag: HomeDragState?, page: Int?,
    onLaunchFrom: (AppEntry, android.graphics.Rect?) -> Unit, onActions: (AppEntry) -> Unit, onBack: () -> Unit, modifier: Modifier) {
    Column(modifier.fillMaxWidth().glassDepth(RoundedCornerShape(24.dp), Color.White.copy(alpha = .14f)).padding(12.dp)) {
        TextButton(onBack, Modifier.testTag("library-category-back"), colors = ButtonDefaults.textButtonColors(contentColor = Color.White)) {
            Icon(Icons.Rounded.ChevronLeft, null); Spacer(Modifier.width(6.dp))
            Text(group.title, style = MaterialTheme.typography.titleMedium)
        }
        LazyVerticalGrid(GridCells.Adaptive(84.dp), Modifier.weight(1f).testTag("library-category-${group.key}"),
            verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(vertical = 8.dp)) {
            items(group.apps.size, key = { "cat-${group.apps[it].id}" }) { index ->
                val app = group.apps[index]
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    LibraryIcon(app, 52.dp, drag, page, onLaunchFrom, onActions)
                    Text(app.label, Modifier.padding(top = 4.dp, start = 2.dp, end = 2.dp), fontSize = 11.sp, maxLines = 1,
                        overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center, style = LabelShadow, color = Color.White)
                }
            }
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun LibraryRow(app: AppEntry, pinned: Set<String>, editing: Boolean, drag: HomeDragState?, page: Int?,
    onPin: (String, Boolean) -> Unit, onLaunchFrom: (AppEntry, android.graphics.Rect?) -> Unit,
    onActions: (AppEntry) -> Unit, hiddenEntry: Boolean = false) {
    val model = LocalLauncherModel.current
    val isPinned = app.id in pinned
    val launchBounds = remember { android.graphics.Rect() }
    val dragModifier = if (drag != null) Modifier.dropRegion(drag, DropTarget.Library(app.id), app.id, page) else Modifier
    val click = { if (editing) onPin(app.id, !isPinned) else onLaunchFrom(app, launchBounds) }
    Row(Modifier.fillMaxWidth().heightIn(min = 60.dp).then(dragModifier).clip(RoundedCornerShape(14.dp))
        .testTag(if (hiddenEntry) "hidden-app-${app.id}" else "library-app-${app.id}")
        .then(if (drag == null) Modifier.combinedClickable(onClick = click, onLongClick = { onActions(app) })
            else Modifier.clickable(onClick = click).semantics { onLongClick("App options") { onActions(app); true } })
        .padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Image(app.icon.asImageBitmap(), null, Modifier.size(40.dp)
            .onGloballyPositioned { launchBounds.set(it.boundsInWindow().toAndroidBounds()) }.clip(LocalIconShape.current))
        Text(app.label, Modifier.weight(1f).padding(start = 12.dp), maxLines = 2, fontSize = 14.sp)
        if (hiddenEntry) TextButton(onClick = { model?.setHidden(app.id, false) }, Modifier.testTag("unhide-${app.id}"),
            colors = ButtonDefaults.textButtonColors(contentColor = LocalContentColor.current)) { Text("Unhide") }
        if (editing) IconButton(onClick = { onPin(app.id, !isPinned) }, Modifier.testTag("pin-${app.id}")) {
            Icon(if (isPinned) Icons.Rounded.PushPin else Icons.Outlined.PushPin,
                if (isPinned) "Remove ${app.label} from home" else "Pin ${app.label} to home",
                tint = if (isPinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                modifier = Modifier.size(20.dp))
        }
    }
}
