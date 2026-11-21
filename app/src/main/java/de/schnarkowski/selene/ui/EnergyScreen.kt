package de.schnarkowski.selene.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.schnarkowski.selene.data.Battery
import de.schnarkowski.selene.data.BoatData
import de.schnarkowski.selene.data.fmt

@Composable
fun EnergyScreen(b: BoatData, wide: Boolean) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp)) {
        SectionTitle("Batterien")
        if (b.batteries.isEmpty()) {
            Text(
                "Keine Batteriedaten (electrical.batteries.*) im Signal K.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        val cols = if (wide) 3 else 1
        val batteryTiles: List<@Composable (Modifier) -> Unit> =
            b.batteries.map { bat -> @Composable { m: Modifier -> BatteryCard(bat, m) } }
        TileGrid(cols, batteryTiles)

        SectionTitle("Laden")
        TileGrid(
            if (wide) 3 else 2,
            listOf<@Composable (Modifier) -> Unit>(
                { m -> ValueTile("Solar", fmt(b.solarPower, 0), "W", m) },
                { m -> ValueTile("Solar-Modus", b.solarMode?.replaceFirstChar { it.uppercase() } ?: "--", "", m) },
            ),
        )

        if (b.electricalOther.isNotEmpty()) {
            SectionTitle("Weitere Werte (Quattro, Lader, …)")
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(14.dp))
                    .padding(12.dp),
            ) {
                b.electricalOther.forEach { (k, v) -> DataRow(k, formatAny(k, v)) }
            }
        }
        Text(
            "Batterienamen lassen sich in den Einstellungen zuordnen (z. B. 279=Hausbatterie).",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 12.dp),
        )
    }
}

@Composable
private fun BatteryCard(bat: Battery, modifier: Modifier) {
    val inst = LocalInstrumentColors.current
    val socColor: Color = when {
        bat.soc == null -> inst.dim
        bat.soc < 30 -> inst.port
        bat.soc < 50 -> inst.warn
        else -> inst.starboard
    }
    Column(
        modifier
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(14.dp))
            .padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(bat.name, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.weight(1f))
            Text(
                if (bat.soc != null) "${fmt(bat.soc, 0)} %" else "",
                fontSize = 28.sp, fontWeight = FontWeight.Medium, color = socColor,
            )
        }
        PercentBar(bat.soc, socColor, Modifier.padding(vertical = 6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Small("Spannung", "${fmt(bat.voltage, 2)} V")
            Small("Strom", "${fmt(bat.current, 1)} A")
            Small("Leistung", "${fmt(bat.power, 0)} W")
        }
        if (bat.timeRemainingH != null || bat.temperatureC != null) {
            Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                bat.timeRemainingH?.let { Small("Restlaufzeit", fmtHours(it)) }
                bat.temperatureC?.let { Small("Temperatur", "${fmt(it)} °C") }
            }
        }
    }
}

@Composable
private fun Small(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
fun DataRow(key: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(
            key,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(8.dp))
        Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
    }
}

private fun fmtHours(h: Double): String {
    if (h.isNaN() || h.isInfinite() || h < 0) return "--"
    val hh = h.toInt()
    val mm = ((h - hh) * 60).toInt()
    return if (hh >= 100) "> 99 h" else "$hh h ${mm.toString().padStart(2, '0')} min"
}

/** Rohwert lesbar machen; erkennt typische SI-Einheiten am Pfadende. */
fun formatAny(path: String, v: Any?): String = when (v) {
    null -> "–"
    is Double -> when {
        path.endsWith("temperature") -> "${fmt(v - 273.15)} °C"
        path.endsWith("voltage") -> "${fmt(v, 2)} V"
        path.endsWith("current") -> "${fmt(v, 1)} A"
        path.endsWith("power") || path.endsWith("Power") -> "${fmt(v, 0)} W"
        path.endsWith("stateOfCharge") || path.endsWith("Ratio") -> "${fmt(v * 100, 0)} %"
        else -> fmt(v, 3)
    }
    is Map<*, *> -> v.entries.joinToString(", ") { "${it.key}=${if (it.value is Double) fmt(it.value as Double, 5) else it.value}" }
    else -> v.toString()
}
