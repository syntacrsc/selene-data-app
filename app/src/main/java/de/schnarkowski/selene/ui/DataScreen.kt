package de.schnarkowski.selene.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.schnarkowski.selene.data.SkSnapshot

/**
 * Alle empfangenen Signal-K-Werte des eigenen Boots als Liste –
 * zum Prüfen, welche Daten tatsächlich ankommen (z. B. Autopilot).
 */
@Composable
fun DataScreen(s: SkSnapshot) {
    var filter by rememberSaveable { mutableStateOf("") }
    val rows = s.self.entries
        .filter { filter.isBlank() || it.key.contains(filter.trim(), ignoreCase = true) }
        .sortedBy { it.key }
    Column(Modifier.fillMaxSize().padding(12.dp)) {
        OutlinedTextField(
            value = filter,
            onValueChange = { filter = it },
            label = { Text("Filter, z. B. autopilot") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            "${rows.size} von ${s.self.size} Werten · ${s.others.size} andere Schiffe",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 6.dp),
        )
        LazyColumn {
            items(rows, key = { it.key }) { e -> DataRow(e.key, formatAny(e.key, e.value)) }
        }
    }
}
