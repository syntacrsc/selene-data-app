package de.schnarkowski.selene.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.schnarkowski.selene.AnchorWatch
import de.schnarkowski.selene.data.BoatData
import de.schnarkowski.selene.data.fmt
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun AnchorScreen(
    b: BoatData,
    a: AnchorWatch,
    onDrop: () -> Unit,
    onRaise: () -> Unit,
    onRadius: (Double) -> Unit,
    onMute: () -> Unit,
    wide: Boolean,
) {
    val inst = LocalInstrumentColors.current
    val controls: @Composable () -> Unit = {
        Column {
            Text(
                if (a.active) "Ankerwache aktiv" else "Ankerwache aus",
                fontSize = 26.sp, fontWeight = FontWeight.Medium,
                color = when {
                    a.alarm -> inst.port
                    a.active -> inst.starboard
                    else -> MaterialTheme.colorScheme.onSurface
                },
            )
            if (a.alarm) {
                Text(
                    "ALARM – Boot außerhalb des Radius!",
                    fontSize = 22.sp, fontWeight = FontWeight.Bold, color = inst.port,
                    modifier = Modifier.padding(vertical = 6.dp),
                )
                if (!a.muted) {
                    Button(
                        onClick = onMute,
                        colors = ButtonDefaults.buttonColors(containerColor = inst.port),
                    ) { Text("Alarmton aus") }
                }
            }
            TileGrid(
                2,
                listOf<@Composable (Modifier) -> Unit>(
                    { m -> ValueTile("Abstand zum Anker", fmt(a.distanceM, 0), "m", m) },
                    { m -> ValueTile("Max. Abstand", if (a.active) fmt(a.maxDistanceM, 0) else "--", "m", m) },
                    { m -> ValueTile("Tiefe", fmt(b.depth), "m", m) },
                    { m -> ValueTile("Wahrer Wind", fmt(b.tws), "kn", m) },
                ),
            )
            Text(
                "Alarmradius: ${a.radiusM.toInt()} m",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = 12.dp),
            )
            Slider(
                value = a.radiusM.toFloat(),
                onValueChange = { onRadius(it.toDouble()) },
                valueRange = 10f..200f,
                steps = 37,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = onDrop, enabled = b.position != null) {
                    Text(if (a.active) "Anker neu setzen" else "Anker fallen lassen")
                }
                if (a.active) OutlinedButton(onClick = onRaise) { Text("Anker auf") }
            }
            Text(
                "Die Ankerposition ist die aktuelle GPS-Position beim Drücken (Bugposition, nicht die des Ankers). " +
                    "Radius daher etwa Kettenlänge + Bootslänge wählen.\n" +
                    "Hinweis: Der Alarm funktioniert nur, solange die App läuft. " +
                    "Ein Alarm über den Bordserver, der auch bei geschlossener App aufs Handy kommt, folgt später.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 12.dp),
            )
        }
    }

    if (wide) {
        Row(Modifier.fillMaxSize().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            AnchorPlot(a, Modifier.weight(1f))
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) { controls() }
        }
    } else {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp)) {
            AnchorPlot(a, Modifier.fillMaxWidth())
            controls()
        }
    }
}

/** Ankerkreis mit Bootsposition relativ zum Anker. */
@Composable
private fun AnchorPlot(a: AnchorWatch, modifier: Modifier) {
    val inst = LocalInstrumentColors.current
    val dim = MaterialTheme.colorScheme.onSurfaceVariant
    Canvas(
        modifier
            .aspectRatio(1f)
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(14.dp))
            .padding(16.dp),
    ) {
        val c = Offset(size.width / 2f, size.height / 2f)
        val full = size.minDimension / 2f
        // Maßstab: Alarmradius = 70 % der Fläche
        val scale = full * 0.7f / a.radiusM.toFloat()
        drawCircle(dim.copy(alpha = 0.3f), full * 0.7f / 2, c, style = Stroke(2f))
        drawCircle(if (a.alarm) inst.port else inst.starboard, full * 0.7f, c, style = Stroke(6f))
        drawLine(dim.copy(alpha = 0.4f), Offset(c.x, 0f), Offset(c.x, size.height), 2f)
        drawLine(dim.copy(alpha = 0.4f), Offset(0f, c.y), Offset(size.width, c.y), 2f)
        if (a.active) {
            drawCircle(inst.accent, 12f, c)
            val d = (a.distanceM ?: 0.0).toFloat() * scale
            val br = Math.toRadians(a.bearingDeg ?: 0.0)
            val bp = Offset(
                (c.x + d * sin(br).toFloat()).coerceIn(0f, size.width),
                (c.y - d * cos(br).toFloat()).coerceIn(0f, size.height),
            )
            drawCircle(if (a.alarm) inst.port else inst.warn, 16f, bp)
        }
    }
}
