package com.jake.duolauncher

import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.LauncherApps
import android.hardware.biometrics.BiometricManager.Authenticators
import android.hardware.biometrics.BiometricPrompt
import android.net.Uri
import android.os.CancellationSignal
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Small device-local privacy preferences (not part of layout backups). */
object PrivacyStore {
    private const val PREFS = "privacy"
    private val lock = MutableStateFlow(true)
    private var loaded = false

    fun lockHidden(context: Context): StateFlow<Boolean> {
        if (!loaded) { loaded = true; lock.value = context.getSharedPreferences(PREFS, 0).getBoolean("lockHidden", true) }
        return lock.asStateFlow()
    }

    fun setLockHidden(context: Context, value: Boolean) {
        lockHidden(context); lock.value = value
        context.getSharedPreferences(PREFS, 0).edit().putBoolean("lockHidden", value).apply()
    }

    fun deviceSecure(context: Context) = context.getSystemService(KeyguardManager::class.java)?.isDeviceSecure == true
}

/**
 * Asks for the device's own unlock (fingerprint, face, PIN, pattern or password). Without a screen
 * lock there is nothing to verify against, so the action proceeds.
 */
fun authenticate(activity: Activity, title: String, onSuccess: () -> Unit) {
    if (!PrivacyStore.deviceSecure(activity)) { onSuccess(); return }
    val prompt = BiometricPrompt.Builder(activity).setTitle(title)
        .setAllowedAuthenticators(Authenticators.BIOMETRIC_STRONG or Authenticators.DEVICE_CREDENTIAL).build()
    prompt.authenticate(CancellationSignal(), activity.mainExecutor, object : BiometricPrompt.AuthenticationCallback() {
        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult?) = onSuccess()
    })
}

/** System apps (and their updates) cannot be removed by a launcher; only offer uninstall for others. */
fun canUninstall(context: Context, app: AppEntry): Boolean = runCatching {
    val info = context.getSystemService(LauncherApps::class.java).getApplicationInfo(app.packageName, 0, app.user)
    info.flags and ApplicationInfo.FLAG_SYSTEM == 0
}.getOrDefault(false)

fun uninstallIntent(app: AppEntry): Intent = Intent(Intent.ACTION_DELETE, Uri.fromParts("package", app.packageName, null))
    .putExtra(Intent.EXTRA_USER, app.user)

@Composable
internal fun AppEditDialog(app: AppEntry, currentIcon: String?, onDismiss: () -> Unit, onSave: (String?, String?) -> Unit) {
    var label by remember { mutableStateOf(app.label) }
    var icon by remember { mutableStateOf(currentIcon) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Edit ${app.originalLabel}") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(label, { label = it.take(60) }, label = { Text("Name on Home") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("app-edit-label"))
                if (app.label != app.originalLabel) Text("Original name: ${app.originalLabel}",
                    style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
                if (app.shortcutId == null) { Spacer(Modifier.height(12.dp)); IconChoiceSection(app, icon) { icon = it } }
            }
        },
        confirmButton = { TextButton({ onSave(label.takeIf { it.isNotBlank() && it != app.originalLabel }, icon) },
            Modifier.testTag("app-edit-save")) { Text("Save") } },
        dismissButton = { Row {
            if (app.label != app.originalLabel || currentIcon != null) TextButton({ onSave(null, null) }) { Text("Reset") }
            TextButton(onDismiss) { Text("Cancel") }
        } })
}
