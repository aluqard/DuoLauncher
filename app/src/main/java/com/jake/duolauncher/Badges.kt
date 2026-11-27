package com.jake.duolauncher

import android.app.Notification
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.UserManager
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Badge key: an app's package in a given profile. Activity names are irrelevant to notifications. */
fun badgeKey(packageName: String, userSerial: Long) = "$packageName@$userSerial"

/**
 * Counts which notifications belong on a badge. Ongoing notifications (music, navigation, uploads)
 * and group summaries are skipped so one conversation does not count twice.
 */
fun countBadges(notifications: List<BadgeNotification>): Map<String, Int> = notifications
    .filter { !it.ongoing && !it.groupSummary }
    .groupingBy { badgeKey(it.packageName, it.userSerial) }.eachCount()

data class BadgeNotification(val packageName: String, val userSerial: Long, val ongoing: Boolean, val groupSummary: Boolean)

/** Process-wide badge counts. The listener writes; Home reads. Notification content is never read or stored. */
object BadgeStore {
    private const val PREFS = "badges"
    private val counts = MutableStateFlow<Map<String, Int>>(emptyMap())
    private val enabled = MutableStateFlow(false)
    private var loaded = false
    val state: StateFlow<Map<String, Int>> = counts.asStateFlow()

    fun enabled(context: Context): StateFlow<Boolean> {
        if (!loaded) { loaded = true; enabled.value = context.getSharedPreferences(PREFS, 0).getBoolean("enabled", false) }
        return enabled.asStateFlow()
    }

    fun setEnabled(context: Context, value: Boolean) {
        enabled(context); enabled.value = value
        context.getSharedPreferences(PREFS, 0).edit().putBoolean("enabled", value).apply()
        if (!value) counts.value = emptyMap()
    }

    internal fun publish(value: Map<String, Int>) { counts.value = value }

    fun accessGranted(context: Context): Boolean = runCatching {
        context.getSystemService(NotificationManager::class.java)
            .isNotificationListenerAccessGranted(ComponentName(context, BadgeListenerService::class.java))
    }.getOrDefault(false)

    fun accessSettingsIntent(context: Context): Intent =
        Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
            .putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME,
                ComponentName(context, BadgeListenerService::class.java).flattenToString())
}

/** Only notification counts per app are kept; titles, text and extras are never accessed. */
class BadgeListenerService : NotificationListenerService() {
    override fun onListenerConnected() = refresh()
    override fun onNotificationPosted(sbn: StatusBarNotification?) = refresh()
    override fun onNotificationRemoved(sbn: StatusBarNotification?) = refresh()
    override fun onListenerDisconnected() = BadgeStore.publish(emptyMap())

    private fun refresh() {
        if (!BadgeStore.enabled(this).value) { BadgeStore.publish(emptyMap()); return }
        val users = getSystemService(UserManager::class.java)
        val active = runCatching { activeNotifications?.toList() }.getOrNull().orEmpty()
        BadgeStore.publish(countBadges(active.map { sbn ->
            BadgeNotification(sbn.packageName, runCatching { users.getSerialNumberForUser(sbn.user) }.getOrDefault(0L),
                sbn.isOngoing, sbn.notification.flags and Notification.FLAG_GROUP_SUMMARY != 0)
        }))
    }
}

val LocalBadges = staticCompositionLocalOf<Map<String, Int>> { emptyMap() }

@Composable
internal fun BadgeCount(count: Int, modifier: Modifier = Modifier) {
    if (count <= 0) return
    Box(modifier.sizeIn(minWidth = 18.dp, minHeight = 18.dp).background(Color(0xFFFF453A), CircleShape)
        .padding(horizontal = 5.dp).testTag("badge"), contentAlignment = Alignment.Center) {
        Text(if (count > 99) "99+" else count.toString(), color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
    }
}

internal fun Map<String, Int>.countFor(app: AppEntry) = this[badgeKey(app.packageName, app.userSerial)] ?: 0
