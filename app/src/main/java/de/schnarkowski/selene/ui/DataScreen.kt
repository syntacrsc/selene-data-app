package de.schnarkowski.selene.ui

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import de.schnarkowski.selene.data.SkSnapshot
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Alle empfangenen Signal-K-Werte des eigenen Boots – mit Bereichsfilter,
 * Textsuche und Export als Textdatei.
 */
@Composable
fun DataScreen(s: SkSnapshot) {
    val context = LocalContext.current
    var filter by rememberSaveable { mutableStateOf("") }
    var group by rememberSaveable { mutableStateOf("") }

    val groups = s.self.keys.map { it.substringBefore(".") }
        .groupingBy { it }.eachCount().toSortedMap()
    val rows = s.self.entries
        .filter { group.isEmpty() || it.key.substringBefore(".") == group }
        .filter { filter.isBlank() || it.key.contains(filter.trim(), ignoreCase = true) }
        .sortedBy { it.key }

    Column(Modifier.fillMaxSize().padding(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = filter,
                onValueChange = { filter = it },
                label = { Text("Suche, z. B. autopilot") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            Button(
                onClick = { exportData(context, s) },
                modifier = Modifier.padding(start = 8.dp),
            ) {
                Icon(Icons.Filled.Share, contentDescription = null)
                Text(" Export")
            }
        }
        Row(
            Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            FilterChip(selected = group.isEmpty(), onClick = { group = "" }, label = { Text("Alle (${s.self.size})") })
            groups.forEach { (g, n) ->
                FilterChip(selected = group == g, onClick = { group = if (group == g) "" else g }, label = { Text("$g ($n)") })
            }
        }
        Text(
            "${rows.size} Werte angezeigt · ${s.others.size} andere Schiffe (AIS)",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 4.dp),
        )
        SelectionContainer {
            LazyColumn(Modifier.fillMaxWidth()) {
                items(rows, key = { it.key }) { e -> DataRow(e.key, formatAny(e.key, e.value)) }
            }
        }
    }
}

/** Schreibt alle Werte in eine Textdatei und öffnet den Teilen-Dialog. */
private fun exportData(context: Context, s: SkSnapshot) {
    try {
        val ts = SimpleDateFormat("yyyy-MM-dd_HH-mm", Locale.GERMANY).format(Date())
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val file = File(dir, "selene-signalk-$ts.txt")
        file.bufferedWriter().use { w ->
            w.appendLine("Selene Signal-K-Export $ts")
            w.appendLine("Eigenes Boot: ${s.self.size} Werte; andere Schiffe: ${s.others.size}")
            w.appendLine()
            w.appendLine("# Übersicht nach Bereichen")
            s.self.keys.groupingBy { it.substringBefore(".") }.eachCount().toSortedMap()
                .forEach { (g, n) -> w.appendLine("$g: $n") }
            w.appendLine()
            w.appendLine("# Werte (Pfad | Anzeige | Rohwert SI)")
            s.self.entries.sortedBy { it.key }.forEach { (k, v) ->
                w.appendLine("$k | ${formatAny(k, v)} | ${raw(v)}")
            }
            if (s.others.isNotEmpty()) {
                w.appendLine()
                w.appendLine("# Andere Schiffe (AIS)")
                s.others.forEach { (ctx, o) ->
                    w.appendLine("$ctx: ${o["name"] ?: ""} – ${o.keys.sorted().joinToString(", ")}")
                }
            }
        }
        val uri = FileProvider.getUriForFile(context, context.packageName + ".files", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "Selene Signal-K-Daten $ts")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, "Daten teilen"))
    } catch (e: Exception) {
        Toast.makeText(context, "Export fehlgeschlagen: ${e.message}", Toast.LENGTH_LONG).show()
    }
}

private fun raw(v: Any?): String = when (v) {
    null -> "null"
    is Map<*, *> -> v.entries.joinToString(", ", "{", "}") { "${it.key}=${raw(it.value)}" }
    is List<*> -> v.joinToString(", ", "[", "]") { raw(it) }
    else -> v.toString()
}
