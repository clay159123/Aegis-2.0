package com.example.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.domain.TimeMode
import com.example.ui.theme.LocalAegisColors

/** Item 85: game settings. */
@Composable
fun SettingsDialog(
    keepScreenOn: Boolean,
    onKeepScreenOn: (Boolean) -> Unit,
    smartDutyDress: Boolean,
    onSmartDutyDress: (Boolean) -> Unit,
    showBanners: Boolean,
    onShowBanners: (Boolean) -> Unit,
    timeMode: TimeMode,
    onTimeMode: (TimeMode) -> Unit,
    onNewGame: () -> Unit,
    onDismiss: () -> Unit
) {
    val extra = LocalAegisColors.current
    var mode by remember { mutableStateOf(timeMode) }
    var confirmNewGame by remember { mutableStateOf(false) }

    if (confirmNewGame) {
        AlertDialog(
            onDismissRequest = { confirmNewGame = false },
            title = { Text("Start a new game?") },
            text = { Text("This erases your command: soldiers, squads, buildings, awards and progress. It can't be undone.") },
            confirmButton = {
                Button(
                    onClick = { confirmNewGame = false; onNewGame() },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.testTag("confirm_new_game")
                ) { Text("Start over") }
            },
            dismissButton = { TextButton(onClick = { confirmNewGame = false }) { Text("Cancel") } }
        )
        return
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text("Settings", color = extra.brassBright) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.testTag("settings_dialog")) {
                SettingSwitch("Keep screen on", "The screen stays awake while the game is open", keepScreenOn, onKeepScreenOn, "setting_keep_screen_on")
                SettingSwitch("Smart Duty-Dress", "Soldiers change into the right uniform for each duty", smartDutyDress, onSmartDutyDress, "setting_smart_dress")
                SettingSwitch("Notification banners", "Short messages at the bottom of the screen", showBanners, onShowBanners, "setting_banners")
                Text("Game speed", style = MaterialTheme.typography.labelLarge, color = extra.brassBright)
                TimeMode.entries.forEach { m ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 48.dp).testTag("setting_speed_${m.name.lowercase()}")
                    ) {
                        RadioButton(selected = mode == m, onClick = { mode = m; onTimeMode(m) })
                        Text(m.label)
                    }
                }
                Text("Speed changes apply the next time the game starts.", style = MaterialTheme.typography.labelSmall, color = extra.textMuted)
                Spacer(Modifier.height(4.dp))
                OutlinedButton(
                    onClick = { confirmNewGame = true },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 48.dp).testTag("setting_new_game")
                ) { Text("Start a new game") }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss, modifier = Modifier.defaultMinSize(minHeight = 48.dp)) { Text("Done") } }
    )
}

@Composable
private fun SettingSwitch(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit, tag: String) {
    val extra = LocalAegisColors.current
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 48.dp)) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            Text(subtitle, style = MaterialTheme.typography.labelSmall, color = extra.textMuted)
        }
        Switch(checked = checked, onCheckedChange = onChange, modifier = Modifier.testTag(tag))
    }
}
