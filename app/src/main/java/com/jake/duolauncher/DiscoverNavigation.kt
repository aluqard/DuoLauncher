package com.jake.duolauncher

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp

/** Positive overscroll from home page one opens the feed without renumbering pinned pages. */
@Composable
internal fun Modifier.discoverSwipe(enabled: Boolean, onOpen: () -> Unit): Modifier {
    val allowed = rememberUpdatedState(enabled)
    val open = rememberUpdatedState(onOpen)
    val threshold = with(LocalDensity.current) { 72.dp.toPx() }
    val connection = remember(threshold) {
        object : NestedScrollConnection {
            var distance = 0f
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (source == NestedScrollSource.UserInput && allowed.value) distance = (distance + available.x).coerceAtLeast(0f)
                else if (!allowed.value) distance = 0f
                return Offset.Zero
            }
            override suspend fun onPreFling(available: Velocity): Velocity {
                val shouldOpen = allowed.value && distance >= threshold
                distance = 0f
                if (shouldOpen) open.value()
                return if (shouldOpen) available else Velocity.Zero
            }
        }
    }
    return nestedScroll(connection)
}

/** Reserve the dock's glass bar for the saved dock width plus its surrounding wallpaper, at any display density. */
internal fun discoverDockFraction(widthDp: Float, dockWidthDp: Float): Float =
    ((sideDockBarWidth(dockWidthDp.coerceIn(56f, 84f)) + 28f) / widthDp.coerceAtLeast(1f)).coerceIn(.05f, .45f)

/** Whether swiping right from the first Home page opens Discover. Off unless turned on in Gestures & search. */
object DiscoverSwipeSetting {
    private const val PREFS = "home_gestures"
    private const val KEY = "discoverSwipe"
    private val enabled = kotlinx.coroutines.flow.MutableStateFlow(false)
    private var loaded = false

    fun enabled(context: android.content.Context): kotlinx.coroutines.flow.StateFlow<Boolean> {
        if (!loaded) {
            loaded = true
            enabled.value = context.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE).getBoolean(KEY, false)
        }
        return enabled
    }

    fun set(context: android.content.Context, on: Boolean) {
        enabled(context); enabled.value = on
        context.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE).edit().putBoolean(KEY, on).apply()
    }
}
