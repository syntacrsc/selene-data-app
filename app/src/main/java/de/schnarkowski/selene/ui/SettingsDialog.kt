package de.schnarkowski.selene.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.schnarkowski.selene.AppSettings
import de.schnarkowski.selene.SourceType
import de.schnarkowski.selene.ThemeMode

@Composable
fun SettingsDialog(current: AppSettings, onDismiss: () -> Unit, onSave: (AppSettings) -> Unit) {
    var s by remember { mutableStateOf(current) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Einstellungen") },
        confirmButton = { TextButton(onClick = { onSave(s) }) { Text("Speichern") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text("Datenquelle", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                SourceType.entries.forEach { t ->
                    RadioRow(t.label, s.source == t) { s = s.copy(source = t) }
                }
                if (s.source == SourceType.BOAT) {
                    OutlinedTextField(
                        value = s.boatHost,
                        onValueChange = { s = s.copy(boatHost = it) },
                        label = { Text("Signal-K-Server (Host:Port)") },
                        supportingText = { Text("z. B. einstein:3000 oder 192.168.1.20:3000") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Text(
                    "Darstellung",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 12.dp),
                )
                ThemeMode.entries.forEach { m ->
                    RadioRow(m.label, s.theme == m) { s = s.copy(theme = m) }
                }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                    Text("Display bleibt an", modifier = Modifier.weight(1f))
                    Switch(checked = s.keepScreenOn, onCheckedChange = { s = s.copy(keepScreenOn = it) })
                }
                Text(
                    "Namen (Batterien, Tanks)",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 12.dp),
                )
                OutlinedTextField(
                    value = s.batteryNames,
                    onValueChange = { s = s.copy(batteryNames = it) },
                    label = { Text("Kennung=Name, je Zeile") },
                    supportingText = { Text("Kennung siehe Seite „Daten“, z. B. 279=Hausbatterie; \"-\" blendet aus") },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.padding(4.dp))
            }
        },
    )
}

@Composable
private fun RadioRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onClick)
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Text(label)
    }
}
