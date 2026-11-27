package com.jake.duolauncher

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.content.pm.LauncherApps
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.net.URLDecoder
import java.net.URLEncoder

/** A pinned shortcut placed on Home. Slots store it as `shortcut:<package>/<encoded id>@<userSerial>`. */
data class ShortcutKey(val packageName: String, val shortcutId: String, val userSerial: Long)

fun shortcutSlotId(key: ShortcutKey): String =
    "$SHORTCUT_PREFIX${key.packageName}/${URLEncoder.encode(key.shortcutId, "UTF-8")}@${key.userSerial}"

fun parseShortcutSlotId(id: String): ShortcutKey? {
    if (!isShortcutId(id)) return null
    val body = id.removePrefix(SHORTCUT_PREFIX)
    val at = body.lastIndexOf('@'); val slash = body.indexOf('/')
    if (slash <= 0 || at <= slash + 1 || at == body.lastIndex) return null
    val serial = body.substring(at + 1).toLongOrNull()?.takeIf { it >= 0 } ?: return null
    val shortcut = runCatching { URLDecoder.decode(body.substring(slash + 1, at), "UTF-8") }.getOrNull() ?: return null
    return ShortcutKey(body.substring(0, slash), shortcut, serial)
}

/**
 * Shortcut ids in a layout that should disappear: their app was uninstalled, or (when the launcher
 * may read pinned shortcuts) the shortcut is no longer pinned. Without that access nothing is dropped.
 */
fun removedShortcutIds(layoutIds: Collection<String>, removedPackages: Set<Pair<Long, String>>,
    pinned: Set<String>?): Set<String> = layoutIds.filterTo(mutableSetOf()) { id ->
    val key = parseShortcutSlotId(id) ?: return@filterTo false
    key.userSerial to key.packageName in removedPackages || (pinned != null && id !in pinned)
}

/** Requests accepted in [ConfirmPinActivity], handed to Home so it can place them. */
sealed interface PendingPin {
    data class Shortcut(val id: String) : PendingPin
    data class Widget(val request: LauncherApps.PinItemRequest) : PendingPin
}

object PinInbox {
    private val queue = MutableStateFlow<List<PendingPin>>(emptyList())
    val pending: StateFlow<List<PendingPin>> = queue.asStateFlow()
    fun post(pin: PendingPin) = queue.update { it + pin }
    fun take(pin: PendingPin) = queue.update { it - pin }
}

/** Shown by Android when another app asks to add a shortcut or widget to Home. */
class ConfirmPinActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val launcherApps = getSystemService(LauncherApps::class.java)
        val request = runCatching { launcherApps.getPinItemRequest(intent) }.getOrNull()
        if (request == null || !request.isValid) { finish(); return }
        val label: String
        val icon: android.graphics.Bitmap?
        when (request.requestType) {
            LauncherApps.PinItemRequest.REQUEST_TYPE_SHORTCUT -> {
                val info = request.shortcutInfo ?: run { finish(); return }
                label = (info.shortLabel ?: info.longLabel ?: info.`package`).toString()
                icon = runCatching { launcherApps.getShortcutIconDrawable(info, resources.displayMetrics.densityDpi)?.toBitmap(144, 144) }.getOrNull()
            }
            LauncherApps.PinItemRequest.REQUEST_TYPE_APPWIDGET -> {
                val info = request.getAppWidgetProviderInfo(this) ?: run { finish(); return }
                label = info.loadLabel(packageManager)
                icon = runCatching { info.loadIcon(this, resources.displayMetrics.densityDpi)?.toBitmap(144, 144) }.getOrNull()
            }
            else -> { finish(); return }
        }
        setContent {
            DuoTheme(rememberSavedAppearance().dark) {
                AlertDialog(onDismissRequest = ::finish,
                    icon = { icon?.let { Image(it.asImageBitmap(), null, Modifier.size(56.dp).clip(RoundedCornerShape(14.dp))) } },
                    title = { Text("Add to Home screen?") },
                    text = { Text(label) },
                    confirmButton = { TextButton({ accept(request) }, Modifier.testTag("pin-accept")) { Text("Add") } },
                    dismissButton = { TextButton(::finish) { Text("Cancel") } })
            }
        }
    }

    private fun accept(request: LauncherApps.PinItemRequest) {
        if (!request.isValid) { finish(); return }
        when (request.requestType) {
            LauncherApps.PinItemRequest.REQUEST_TYPE_SHORTCUT -> {
                val info = request.shortcutInfo
                val serial = getSystemService(android.os.UserManager::class.java).getSerialNumberForUser(info?.userHandle)
                if (info != null && request.accept()) PinInbox.post(PendingPin.Shortcut(
                    shortcutSlotId(ShortcutKey(info.`package`, info.id, serial))))
            }
            // The widget id must come from Home's own host, so Home finishes this one.
            LauncherApps.PinItemRequest.REQUEST_TYPE_APPWIDGET -> PinInbox.post(PendingPin.Widget(request))
        }
        startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .putExtra("duo_destination", "home"))
        finish()
    }
}

/** Binds a pin-requested widget to Home's host and returns its id, or null when the request was rejected. */
fun acceptWidgetPin(controller: WidgetController, request: LauncherApps.PinItemRequest): Int? {
    if (!request.isValid) return null
    val id = controller.host.allocateAppWidgetId()
    val accepted = runCatching { request.accept(Bundle().apply { putInt(AppWidgetManager.EXTRA_APPWIDGET_ID, id) }) }.getOrDefault(false)
    if (!accepted) { controller.host.deleteAppWidgetId(id); return null }
    return id
}
