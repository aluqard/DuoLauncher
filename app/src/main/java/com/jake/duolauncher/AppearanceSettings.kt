package com.jake.duolauncher

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.Alignment
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check

@Composable
internal fun AppearanceSettings(state: AppearanceState, onMode: (AppearanceMode) -> Unit,
    onManual: (String, Double, Double) -> Unit, onDeviceLocation: () -> Unit, onClear: () -> Unit) {
    var place by remember(state.place) { mutableStateOf(state.place) }
    var latitude by remember(state.latitude) { mutableStateOf(state.latitude?.toString().orEmpty()) }
    var longitude by remember(state.longitude) { mutableStateOf(state.longitude?.toString().orEmpty()) }
    var inputError by remember { mutableStateOf<String?>(null) }
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.testTag("appearance-settings")) {
        SettingsGroup {
            AppearanceMode.entries.forEachIndexed { index, mode ->
                if (index > 0) HorizontalDivider(Modifier.padding(start = 16.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .5f))
                Row(Modifier.fillMaxWidth().clickable { onMode(mode) }.heightIn(min = 50.dp)
                    .padding(horizontal = 16.dp, vertical = 12.dp).testTag("appearance-${mode.name.lowercase()}"),
                    verticalAlignment = Alignment.CenterVertically) {
                    Text(when (mode) {
                        AppearanceMode.LIGHT -> "Light"; AppearanceMode.DARK -> "Dark"; AppearanceMode.SYSTEM -> "Follow system"
                        AppearanceMode.SUNRISE_SUNSET -> "Match sunrise & sunset"
                    }, Modifier.weight(1f), fontSize = 17.sp)
                    if (state.mode == mode) Icon(Icons.Rounded.Check, "Selected",
                        tint = androidx.compose.ui.graphics.Color(0xFF2F8CFF), modifier = Modifier.size(20.dp))
                }
            }
        }
        if (state.mode == AppearanceMode.SUNRISE_SUNSET) {
            state.fallback?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            OutlinedTextField(place, { place = it }, Modifier.testTag("appearance-place"), label = { Text("Place name") }, singleLine = true)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(latitude, { latitude = it }, Modifier.weight(1f).testTag("appearance-latitude"), label = { Text("Latitude (−90 to 90)") }, singleLine = true)
                OutlinedTextField(longitude, { longitude = it }, Modifier.weight(1f).testTag("appearance-longitude"), label = { Text("Longitude (−180 to 180)") }, singleLine = true)
            }
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Button(onClick = {
                    focusManager.clearFocus(); keyboard?.hide()
                    val lat = latitude.toDoubleOrNull(); val lon = longitude.toDoubleOrNull()
                    if (lat != null && lon != null && lat in -90.0..90.0 && lon in -180.0..180.0) {
                        inputError = null; onManual(place, lat, lon)
                    } else inputError = "Enter a latitude from −90 to 90 and longitude from −180 to 180." },
                    modifier = Modifier.fillMaxWidth().testTag("appearance-save-place")) { Text("Use this place") }
                AppearanceFeedback(inputError, MaterialTheme.colorScheme.error, "appearance-manual-status")
                OutlinedButton(onClick = {
                    focusManager.clearFocus(); keyboard?.hide(); onDeviceLocation()
                }, modifier = Modifier.fillMaxWidth()
                    .testTag("appearance-device-location")) { Text("Use device location") }
                AppearanceFeedback(state.locationStatus, MaterialTheme.colorScheme.onSurfaceVariant,
                    "appearance-location-status")
                if (state.latitude != null) TextButton(onClick = onClear, Modifier.fillMaxWidth()) { Text("Clear location") }
            }
        }
    }
}

@Composable
private fun AppearanceFeedback(message: String?, color: androidx.compose.ui.graphics.Color, tag: String) {
    val bringIntoView = remember { BringIntoViewRequester() }
    LaunchedEffect(message) { if (message != null) bringIntoView.bringIntoView() }
    message?.let {
        Text(it, color = color, modifier = Modifier.bringIntoViewRequester(bringIntoView)
            .semantics { liveRegion = LiveRegionMode.Polite }.testTag(tag))
    }
}
