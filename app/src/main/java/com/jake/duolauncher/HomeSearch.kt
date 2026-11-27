package com.jake.duolauncher

import android.Manifest
import android.app.SearchManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** A phone setting the search screen can open directly. [keywords] widen matching beyond the title. */
internal data class SettingShortcut(val title: String, val keywords: List<String>, val icon: ImageVector, val tint: Color, val action: String)

internal val SETTING_SHORTCUTS = listOf(
    SettingShortcut("Wi-Fi", listOf("wifi", "wireless", "internet"), Icons.Rounded.Wifi, Color(0xFF1E88FF), Settings.ACTION_WIFI_SETTINGS),
    SettingShortcut("Bluetooth", listOf("pair", "headphones"), Icons.Rounded.Bluetooth, Color(0xFF1E88FF), Settings.ACTION_BLUETOOTH_SETTINGS),
    SettingShortcut("Mobile network", listOf("cellular", "data", "sim", "carrier"), Icons.Rounded.SignalCellularAlt, Color(0xFF34C759), Settings.ACTION_NETWORK_OPERATOR_SETTINGS),
    SettingShortcut("Airplane mode", listOf("flight"), Icons.Rounded.AirplanemodeActive, Color(0xFFFF9500), Settings.ACTION_AIRPLANE_MODE_SETTINGS),
    SettingShortcut("Notifications", listOf("notifications", "alerts"), Icons.Rounded.Notifications, Color(0xFFFF3B30), "android.settings.NOTIFICATION_SETTINGS"),
    SettingShortcut("Display", listOf("brightness", "screen", "dark mode"), Icons.Rounded.BrightnessMedium, Color(0xFF1E88FF), Settings.ACTION_DISPLAY_SETTINGS),
    SettingShortcut("Sound", listOf("volume", "ringtone", "vibration"), Icons.AutoMirrored.Rounded.VolumeUp, Color(0xFFFF2D55), Settings.ACTION_SOUND_SETTINGS),
    SettingShortcut("Position", listOf("location", "gps"), Icons.Rounded.LocationOn, Color(0xFF1E88FF), Settings.ACTION_LOCATION_SOURCE_SETTINGS),
    SettingShortcut("Apps", listOf("applications", "default apps"), Icons.Rounded.Apps, Color(0xFF5856D6), Settings.ACTION_APPLICATION_SETTINGS),
    SettingShortcut("Battery", listOf("power", "charging"), Icons.Rounded.BatteryFull, Color(0xFF34C759), Intent.ACTION_POWER_USAGE_SUMMARY),
    SettingShortcut("Storage", listOf("space", "memory", "files"), Icons.Rounded.Storage, Color(0xFF8E8E93), Settings.ACTION_INTERNAL_STORAGE_SETTINGS),
    SettingShortcut("Security", listOf("lock", "password", "fingerprint", "privacy"), Icons.Rounded.Lock, Color(0xFF8E8E93), Settings.ACTION_SECURITY_SETTINGS),
    SettingShortcut("Accessibility", listOf("talkback", "font size"), Icons.Rounded.AccessibilityNew, Color(0xFF1E88FF), Settings.ACTION_ACCESSIBILITY_SETTINGS),
    SettingShortcut("Languages", listOf("language", "keyboard", "input"), Icons.Rounded.Language, Color(0xFF8E8E93), Settings.ACTION_LOCALE_SETTINGS),
    SettingShortcut("Date & time", listOf("clock", "time zone"), Icons.Rounded.Schedule, Color(0xFF8E8E93), Settings.ACTION_DATE_SETTINGS),
    SettingShortcut("About phone", listOf("device", "version", "model"), Icons.Rounded.Info, Color(0xFF8E8E93), Settings.ACTION_DEVICE_INFO_SETTINGS),
)

/** Settings whose title or keywords contain [query], case-insensitively. */
internal fun matchingSettings(query: String, shortcuts: List<SettingShortcut> = SETTING_SHORTCUTS): List<SettingShortcut> {
    val q = query.trim().lowercase()
    if (q.isEmpty()) return emptyList()
    return shortcuts.filter { s -> s.title.lowercase().contains(q) || s.keywords.any { it.contains(q) } }
}

internal data class ContactHit(val name: String, val uri: Uri)

private fun queryContacts(context: Context, query: String): List<ContactHit> = runCatching {
    val uri = ContactsContract.Contacts.CONTENT_FILTER_URI.buildUpon().appendPath(query).build()
    context.contentResolver.query(uri, arrayOf(ContactsContract.Contacts._ID, ContactsContract.Contacts.LOOKUP_KEY,
        ContactsContract.Contacts.DISPLAY_NAME_PRIMARY), null, null, null)?.use { c ->
        buildList {
            while (c.moveToNext() && size < 5) {
                val name = c.getString(2) ?: continue
                add(ContactHit(name, ContactsContract.Contacts.getLookupUri(c.getLong(0), c.getString(1))))
            }
        }
    } ?: emptyList()
}.getOrDefault(emptyList())

private val SearchMuted = Color.White.copy(alpha = .62f)

/** Contacts, phone settings and a web search for the All apps search, alongside the matching apps. */
internal class SearchExtras(
    val query: String,
    val contactsAllowed: Boolean,
    val contacts: List<ContactHit>,
    val settings: List<SettingShortcut>,
    val requestContacts: () -> Unit,
    val open: (Intent, String) -> Unit,
    val webSearch: () -> Unit,
)

@Composable
internal fun rememberSearchExtras(query: String, onOpened: () -> Unit): SearchExtras {
    val context = LocalContext.current
    val activity = context as? MainActivity
    val keyboard = LocalSoftwareKeyboardController.current
    val q = query.trim()
    var contactsAllowed by remember { mutableStateOf(context.checkSelfPermission(Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        activity?.let { LiveDiscover.setExternalResultPending(it, "main", "contacts-permission", false) }
        contactsAllowed = granted
    }
    val contacts by produceState(emptyList<ContactHit>(), q, contactsAllowed) {
        value = if (q.isEmpty() || !contactsAllowed) emptyList() else withContext(Dispatchers.IO) { queryContacts(context, q) }
    }
    val settings = remember(q) { matchingSettings(q) }
    fun open(intent: Intent, token: String) {
        keyboard?.hide()
        if (activity?.openSystemSettings(intent, token) == true) onOpened()
    }
    return SearchExtras(q, contactsAllowed, contacts, settings,
        requestContacts = {
            activity?.let { LiveDiscover.setExternalResultPending(it, "main", "contacts-permission", true) }
            permission.launch(Manifest.permission.READ_CONTACTS)
        },
        open = ::open,
        webSearch = {
            val search = Intent(Intent.ACTION_WEB_SEARCH).putExtra(SearchManager.QUERY, q)
            open(if (search.resolveActivity(context.packageManager) != null) search
                else Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=" + Uri.encode(q))), "web-search")
        })
}

/** The Contacts, Settings and Web sections shown above the apps while a search has text. */
internal fun LazyListScope.searchExtras(extras: SearchExtras) {
    if (extras.query.isEmpty()) return
    item("contacts-title") { SearchSection("Contacts") }
    item("contacts") {
        SearchCard {
            if (!extras.contactsAllowed) SearchRow(Icons.Rounded.Person, Color(0xFF8E8E93), "Search contacts too", "Tap to allow",
                Modifier.testTag("home-search-contacts-allow"), onClick = extras.requestContacts)
            else if (extras.contacts.isEmpty()) Text("No contacts", Modifier.padding(16.dp), color = SearchMuted)
            else extras.contacts.forEach { contact ->
                SearchRow(null, Color(0xFF8E8E93), contact.name, null, initial = contact.name.take(1).uppercase()) {
                    extras.open(Intent(Intent.ACTION_VIEW, contact.uri), "contact")
                }
            }
        }
    }
    if (extras.settings.isNotEmpty()) {
        item("settings-title") { SearchSection("Settings") }
        item("settings") {
            SearchCard {
                extras.settings.forEach { s ->
                    SearchRow(s.icon, s.tint, s.title, null, Modifier.testTag("home-search-setting-${s.title}")) {
                        extras.open(Intent(s.action), "settings-search")
                    }
                }
            }
        }
    }
    item("web-title") { SearchSection("Web") }
    item("web") {
        SearchCard {
            SearchRow(Icons.Rounded.Public, Color(0xFF1E88FF), "Search the web for ‘${extras.query}’", null,
                Modifier.testTag("home-search-web"), onClick = extras.webSearch)
        }
    }
}

@Composable
private fun SearchSection(title: String) {
    Text(title, Modifier.padding(start = 12.dp, top = 16.dp, bottom = 8.dp), color = SearchMuted, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
}

@Composable
private fun SearchCard(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Color.White.copy(alpha = .1f)), content = content)
}

@Composable
private fun SearchRow(icon: ImageVector?, tint: Color, title: String, subtitle: String?, modifier: Modifier = Modifier,
    initial: String? = null, onClick: () -> Unit) {
    Row(modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(38.dp).background(tint, RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) {
            if (icon != null) Icon(icon, null, Modifier.size(22.dp), tint = Color.White)
            else Text(initial.orEmpty(), color = Color.White, fontWeight = FontWeight.SemiBold)
        }
        Column(Modifier.padding(start = 14.dp)) {
            Text(title, color = Color.White, fontSize = 17.sp)
            if (subtitle != null) Text(subtitle, color = SearchMuted, fontSize = 14.sp)
        }
    }
}
