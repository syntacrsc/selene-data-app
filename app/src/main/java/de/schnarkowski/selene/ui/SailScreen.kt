package de.schnarkowski.selene.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.schnarkowski.selene.data.BoatData
import de.schnarkowski.selene.data.fmt
import de.schnarkowski.selene.data.fmtSide
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun SailScreen(b: BoatData, wide: Boolean) {
    val tiles: List<@Composable (Modifier) -> Unit> = listOf<@Composable (Modifier) -> Unit>(
        { m -> ValueTile("Fahrt (STW)", fmt(b.stw), "kn", m) },
        { m -> ValueTile("Fahrt (SOG)", fmt(b.sog), "kn", m) },
        { m -> ValueTile("Wahrer Wind", fmt(b.tws), "kn", m) },
        { m -> ValueTile("Wahrer Windw.", fmtSide(b.twa), "", m) },
        { m -> ValueTile("Tiefe", fmt(b.depth), "m", m, valueColor = depthColor(b.depth)) },
        { m -> ValueTile("Kurs (Heading)", fmt(b.heading, 0), "°", m) },
        { m -> ValueTile("Kurs ü. Grund", fmt(b.cog, 0), "°", m) },
        { m -> ValueTile("Windrichtung", fmt(b.twd, 0), "°", m) },
        { m -> ValueTile("Wassertemp.", fmt(b.waterTemp), "°C", m) },
        { m -> ValueTile("Tagesweg", fmt(b.tripNm), "sm", m) },
    )
    val scroll = rememberScrollState()
    if (wide) {
        Row(
            Modifier.fillMaxSize().padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                WindDial(b, Modifier.fillMaxWidth())
                AutopilotCard(b)
            }
            Column(Modifier.weight(1.3f).verticalScroll(scroll)) {
                TileGrid(3, tiles)
            }
        }
    } else {
        Column(Modifier.fillMaxSize().verticalScroll(scroll).padding(12.dp)) {
            WindDial(b, Modifier.fillMaxWidth())
            TileGrid(2, tiles)
            AutopilotCard(b)
        }
    }
}

@Composable
private fun depthColor(depth: Double?): Color {
    val c = LocalInstrumentColors.current
    return when {
        depth == null -> MaterialTheme.colorScheme.onSurface
        depth < 4.0 -> c.port
        depth < 8.0 -> c.warn
        else -> MaterialTheme.colorScheme.onSurface
    }
}

/** Windanzeige: scheinbarer Wind als Pfeil, wahrer Wind als Marke am Rand. */
@Composable
fun WindDial(b: BoatData, modifier: Modifier = Modifier) {
    val inst = LocalInstrumentColors.current
    val ring = MaterialTheme.colorScheme.onSurfaceVariant
    val face = MaterialTheme.colorScheme.surface
    Box(modifier.aspectRatio(1f).padding(8.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val r = size.minDimension / 2f
            val c = Offset(size.width / 2f, size.height / 2f)
            fun pt(deg: Double, rad: Float) = Offset(
                c.x + rad * sin(Math.toRadians(deg)).toFloat(),
                c.y - rad * cos(Math.toRadians(deg)).toFloat(),
            )
            drawCircle(face, r, c)
            // Sektoren hoch am Wind: Bb rot, Stb grün
            val arcR = r * 0.92f
            val tl = Offset(c.x - arcR, c.y - arcR)
            val sz = Size(arcR * 2, arcR * 2)
            // Compose: 0° = 3 Uhr, im Uhrzeigersinn; Bug (0° Wind) = -90°
            drawArc(inst.starboard, -90f + 20f, 40f, false, tl, sz, style = Stroke(r * 0.07f))
            drawArc(inst.port, -90f - 60f, 40f, false, tl, sz, style = Stroke(r * 0.07f))
            // Skalenstriche
            for (d in 0 until 360 step 10) {
                val long = d % 30 == 0
                drawLine(
                    ring, pt(d.toDouble(), r * (if (long) 0.80f else 0.86f)), pt(d.toDouble(), r * 0.97f),
                    strokeWidth = if (long) 5f else 2f,
                )
            }
            // Bootssymbol
            val boat = Path().apply {
                moveTo(c.x, c.y - r * 0.38f)
                quadraticBezierTo(c.x + r * 0.17f, c.y - r * 0.05f, c.x + r * 0.11f, c.y + r * 0.32f)
                lineTo(c.x - r * 0.11f, c.y + r * 0.32f)
                quadraticBezierTo(c.x - r * 0.17f, c.y - r * 0.05f, c.x, c.y - r * 0.38f)
                close()
            }
            drawPath(boat, ring.copy(alpha = 0.25f))
            // wahrer Wind: Dreieck am Rand
            b.twa?.let { twa ->
                val tip = pt(twa, r * 0.72f)
                val l = pt(twa - 6, r * 0.97f)
                val rr = pt(twa + 6, r * 0.97f)
                drawPath(Path().apply { moveTo(tip.x, tip.y); lineTo(l.x, l.y); lineTo(rr.x, rr.y); close() }, inst.warn)
            }
            // scheinbarer Wind: Zeiger
            b.awa?.let { awa ->
                val tip = pt(awa, r * 0.95f)
                val base = pt(awa, r * 0.45f)
                drawLine(inst.accent, base, tip, strokeWidth = r * 0.05f)
                val l = pt(awa - 7, r * 0.78f)
                val rr = pt(awa + 7, r * 0.78f)
                drawPath(Path().apply { moveTo(tip.x, tip.y); lineTo(l.x, l.y); lineTo(rr.x, rr.y); close() }, inst.accent)
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("AWS", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(fmt(b.aws), fontSize = 44.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
            Text("AWA ${fmtSide(b.awa)}", fontSize = 18.sp, color = inst.accent)
        }
    }
}

@Composable
private fun AutopilotCard(b: BoatData) {
    val inst = LocalInstrumentColors.current
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(14.dp))
            .padding(12.dp),
    ) {
        Text("Autopilot", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (b.apState == null && b.apTargetHeading == null && b.rudder == null) {
            Text(
                "Keine Autopilot-Daten im Signal K",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@Column
        }
        Row(verticalAlignment = Alignment.Bottom) {
            Text(apLabel(b.apState), fontSize = 30.sp, fontWeight = FontWeight.Medium, color = inst.accent)
            Text(
                when {
                    b.apState == "wind" && b.apTargetWind != null -> "   Ziel ${fmtSide(b.apTargetWind)}"
                    b.apTargetHeading != null -> "   Ziel ${fmt(b.apTargetHeading, 0)}°"
                    else -> ""
                },
                fontSize = 20.sp,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = 4.dp),
            )
        }
        b.rudder?.let { rudder ->
            Text(
                "Ruderlage ${fmtSide(rudder)}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp),
            )
            RudderBar(rudder)
        }
    }
}

private fun apLabel(s: String?) = when (s?.lowercase()) {
    null -> "--"
    "standby" -> "Standby"
    "auto" -> "Auto"
    "wind" -> "Wind"
    "route", "track" -> "Track"
    else -> s
}

@Composable
private fun RudderBar(rudderDeg: Double) {
    val inst = LocalInstrumentColors.current
    val bg = MaterialTheme.colorScheme.surfaceVariant
    Canvas(Modifier.fillMaxWidth().aspectRatio(12f).padding(top = 4.dp)) {
        val mid = size.width / 2f
        drawRect(bg)
        val f = (rudderDeg / 35.0).coerceIn(-1.0, 1.0).toFloat()
        val w = mid * kotlin.math.abs(f)
        val color = if (f >= 0) inst.starboard else inst.port
        if (f >= 0) drawRect(color, Offset(mid, 0f), Size(w, size.height))
        else drawRect(color, Offset(mid - w, 0f), Size(w, size.height))
        drawLine(Color.Gray, Offset(mid, 0f), Offset(mid, size.height), 3f)
    }
}
