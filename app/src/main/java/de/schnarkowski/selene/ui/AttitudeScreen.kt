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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.schnarkowski.selene.data.BoatData
import de.schnarkowski.selene.data.fmt
import de.schnarkowski.selene.data.fmtSide
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun AttitudeScreen(b: BoatData, wide: Boolean) {
    var maxStb by remember { mutableDoubleStateOf(0.0) }
    var maxPort by remember { mutableDoubleStateOf(0.0) }
    LaunchedEffect(b.roll) {
        val r = b.roll ?: return@LaunchedEffect
        if (r > maxStb) maxStb = r
        if (-r > maxPort) maxPort = -r
    }

    val heel: @Composable (Modifier) -> Unit = { m -> HeelGauge(b.roll, m) }
    val pitch: @Composable (Modifier) -> Unit = { m -> PitchGauge(b.pitch, m) }
    val info: @Composable () -> Unit = {
        TileGrid(
            2,
            listOf<@Composable (Modifier) -> Unit>(
                { m -> ValueTile("Max. Krängung Bb", fmt(maxPort, 0), "°", m) },
                { m -> ValueTile("Max. Krängung Stb", fmt(maxStb, 0), "°", m) },
            ),
        )
        OutlinedButton(
            onClick = { maxPort = 0.0; maxStb = 0.0 },
            modifier = Modifier.padding(top = 8.dp),
        ) { Text("Maximalwerte zurücksetzen") }
        if (b.roll == null && b.pitch == null) {
            Text(
                "Keine Lagedaten (navigation.attitude) im Signal K. Der DST820 muss dafür PGN 127257 senden.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }

    if (wide) {
        Row(Modifier.fillMaxSize().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            heel(Modifier.weight(1f))
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                pitch(Modifier.fillMaxWidth())
                info()
            }
        }
    } else {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp)) {
            heel(Modifier.fillMaxWidth())
            pitch(Modifier.fillMaxWidth().padding(top = 8.dp))
            info()
        }
    }
}

/** Krängung: Bootsquerschnitt von achtern, dreht mit der Krängung. */
@Composable
private fun HeelGauge(roll: Double?, modifier: Modifier) {
    val inst = LocalInstrumentColors.current
    val onSurf = MaterialTheme.colorScheme.onSurface
    val dim = MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        modifier
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(14.dp))
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Krängung (Roll)", style = MaterialTheme.typography.labelLarge, color = dim)
        Text(
            if (roll == null) "--" else fmtSide(roll),
            fontSize = 48.sp, fontWeight = FontWeight.Medium,
            color = when {
                roll == null -> onSurf
                abs(roll) >= 30 -> inst.port
                abs(roll) >= 22 -> inst.warn
                else -> onSurf
            },
        )
        Canvas(Modifier.fillMaxWidth().aspectRatio(1.4f)) {
            val c = Offset(size.width / 2f, size.height * 0.62f)
            val r = size.width * 0.42f
            // Skala -45..+45 oben
            fun pt(deg: Double, rad: Float) = Offset(
                c.x + rad * sin(Math.toRadians(deg)).toFloat(),
                c.y - rad * cos(Math.toRadians(deg)).toFloat(),
            )
            val tl = Offset(c.x - r, c.y - r)
            drawArc(dim.copy(alpha = 0.35f), -90f - 45f, 90f, false, tl, Size(2 * r, 2 * r), style = Stroke(6f))
            drawArc(inst.warn, -90f + 22f, 8f, false, tl, Size(2 * r, 2 * r), style = Stroke(10f))
            drawArc(inst.warn, -90f - 30f, 8f, false, tl, Size(2 * r, 2 * r), style = Stroke(10f))
            drawArc(inst.port, -90f + 30f, 15f, false, tl, Size(2 * r, 2 * r), style = Stroke(10f))
            drawArc(inst.port, -90f - 45f, 15f, false, tl, Size(2 * r, 2 * r), style = Stroke(10f))
            for (d in -45..45 step 5) {
                val long = d % 15 == 0
                drawLine(dim, pt(d.toDouble(), r * (if (long) 0.88f else 0.93f)), pt(d.toDouble(), r * 1.04f), if (long) 4f else 2f)
            }
            val ang = (roll ?: 0.0).toFloat().coerceIn(-50f, 50f)
            rotate(ang, pivot = c) {
                // Rumpf, Kiel, Mast
                val hull = Path().apply {
                    moveTo(c.x - r * 0.42f, c.y - r * 0.05f)
                    lineTo(c.x + r * 0.42f, c.y - r * 0.05f)
                    quadraticBezierTo(c.x + r * 0.38f, c.y + r * 0.22f, c.x, c.y + r * 0.25f)
                    quadraticBezierTo(c.x - r * 0.38f, c.y + r * 0.22f, c.x - r * 0.42f, c.y - r * 0.05f)
                    close()
                }
                drawPath(hull, onSurf.copy(alpha = 0.85f))
                drawRect(onSurf.copy(alpha = 0.85f), Offset(c.x - r * 0.03f, c.y + r * 0.22f), Size(r * 0.06f, r * 0.28f))
                drawLine(inst.accent, Offset(c.x, c.y - r * 0.05f), Offset(c.x, c.y - r * 0.95f), 7f)
            }
            // Bb/Stb-Markierung
            drawCircle(inst.port, 10f, pt(-50.0, r * 0.75f))
            drawCircle(inst.starboard, 10f, pt(50.0, r * 0.75f))
        }
    }
}

/** Trimm: Seitenansicht, Bug rechts. */
@Composable
private fun PitchGauge(pitch: Double?, modifier: Modifier) {
    val onSurf = MaterialTheme.colorScheme.onSurface
    val dim = MaterialTheme.colorScheme.onSurfaceVariant
    val inst = LocalInstrumentColors.current
    Column(
        modifier
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(14.dp))
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Trimm (Pitch)", style = MaterialTheme.typography.labelLarge, color = dim)
        Text(
            when {
                pitch == null -> "--"
                pitch >= 0 -> "${fmt(pitch, 1)}° Bug hoch"
                else -> "${fmt(-pitch, 1)}° Bug tief"
            },
            fontSize = 32.sp, fontWeight = FontWeight.Medium, color = onSurf,
        )
        Canvas(Modifier.fillMaxWidth().aspectRatio(3f)) {
            val c = Offset(size.width / 2f, size.height / 2f)
            val w = size.width * 0.38f
            drawLine(dim.copy(alpha = 0.5f), Offset(0f, c.y), Offset(size.width, c.y), 2f)
            // Trimm überhöht darstellen (x3), sonst kaum sichtbar
            val ang = -((pitch ?: 0.0) * 3).toFloat().coerceIn(-30f, 30f)
            rotate(ang, pivot = c) {
                val hull = Path().apply {
                    moveTo(c.x - w, c.y - size.height * 0.08f)
                    lineTo(c.x + w, c.y - size.height * 0.12f)
                    quadraticBezierTo(c.x + w * 0.9f, c.y + size.height * 0.12f, c.x + w * 0.4f, c.y + size.height * 0.14f)
                    lineTo(c.x - w * 0.95f, c.y + size.height * 0.1f)
                    close()
                }
                drawPath(hull, onSurf.copy(alpha = 0.85f))
                drawLine(inst.accent, Offset(c.x + w * 0.1f, c.y - size.height * 0.1f), Offset(c.x + w * 0.1f, c.y - size.height * 0.48f), 6f)
            }
        }
        Text("Darstellung 3-fach überhöht", style = MaterialTheme.typography.labelSmall, color = dim)
    }
}
