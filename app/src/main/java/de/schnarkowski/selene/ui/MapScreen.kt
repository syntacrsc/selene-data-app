package de.schnarkowski.selene.ui

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Point
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.LocationSearching
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import de.schnarkowski.selene.AnchorWatch
import de.schnarkowski.selene.data.AisTarget
import de.schnarkowski.selene.data.BoatData
import de.schnarkowski.selene.data.Geo
import de.schnarkowski.selene.data.LatLon
import de.schnarkowski.selene.data.fmt
import org.osmdroid.tileprovider.MapTileProviderBasic
import org.osmdroid.tileprovider.tilesource.XYTileSource
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Overlay
import org.osmdroid.views.overlay.TilesOverlay
import kotlin.math.hypot

@Composable
fun MapScreen(b: BoatData, track: List<LatLon>, anchor: AnchorWatch, wide: Boolean) {
    var follow by remember { mutableStateOf(true) }
    if (wide) {
        Row(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1.6f).fillMaxHeight()) {
                SeaMap(b, track, anchor, follow) { follow = it }
            }
            AisList(b.ais, Modifier.weight(1f).fillMaxHeight())
        }
    } else {
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1.8f).fillMaxWidth()) {
                SeaMap(b, track, anchor, follow) { follow = it }
            }
            AisList(b.ais, Modifier.weight(1f).fillMaxWidth())
        }
    }
}

@Composable
private fun SeaMap(
    b: BoatData,
    track: List<LatLon>,
    anchor: AnchorWatch,
    follow: Boolean,
    setFollow: (Boolean) -> Unit,
) {
    val context = LocalContext.current
    val inst = LocalInstrumentColors.current
    val overlay = remember { BoatOverlay() }
    var initialZoomDone by remember { mutableStateOf(false) }
    val mapView = remember {
        MapView(context).apply {
            // OSM-Kacheln direkt (ohne Umleitung über alte Subdomains)
            setTileSource(
                XYTileSource(
                    "OSM", 0, 19, 256, ".png",
                    arrayOf("https://tile.openstreetmap.org/"),
                    "© OpenStreetMap",
                ),
            )
            setMultiTouchControls(true)
            controller.setZoom(13.0)
            // OpenSeaMap-Seezeichen als zweite Ebene
            val seamarks = XYTileSource(
                "OpenSeaMap", 3, 18, 256, ".png",
                arrayOf("https://tiles.openseamap.org/seamark/"),
            )
            val seaOverlay = TilesOverlay(MapTileProviderBasic(context, seamarks), context).apply {
                setLoadingBackgroundColor(android.graphics.Color.TRANSPARENT)
                setLoadingLineColor(android.graphics.Color.TRANSPARENT)
            }
            overlays.add(seaOverlay)
            overlays.add(overlay)
        }
    }
    DisposableEffect(Unit) {
        mapView.onResume()
        onDispose {
            mapView.onPause()
            mapView.onDetach()
        }
    }
    Box(Modifier.fillMaxSize()) {
        AndroidView(
            factory = { mapView },
            modifier = Modifier.fillMaxSize(),
            update = { mv ->
                overlay.self = b.position
                overlay.heading = b.heading ?: b.cog
                overlay.track = track
                overlay.ais = b.ais
                overlay.anchor = anchor
                overlay.boatColor = inst.accent.toArgb()
                overlay.aisColor = inst.warn.toArgb()
                overlay.anchorColor = (if (anchor.alarm) inst.port else inst.starboard).toArgb()
                val p = b.position
                if (p != null && (follow || !initialZoomDone)) {
                    if (!initialZoomDone) {
                        mv.controller.setZoom(14.0)
                        initialZoomDone = true
                    }
                    mv.controller.setCenter(GeoPoint(p.lat, p.lon))
                }
                mv.invalidate()
            },
        )
        FloatingActionButton(
            onClick = { setFollow(!follow) },
            modifier = Modifier.align(Alignment.BottomEnd).padding(12.dp),
        ) {
            Icon(
                if (follow) Icons.Filled.MyLocation else Icons.Filled.LocationSearching,
                contentDescription = if (follow) "Folgen aus" else "Folgen an",
            )
        }
        // Verschieben der Karte von Hand beendet das Folgen
        DisposableEffect(mapView) {
            mapView.setOnTouchListener { _, ev ->
                if (ev.actionMasked == android.view.MotionEvent.ACTION_MOVE) setFollow(false)
                false
            }
            onDispose { mapView.setOnTouchListener(null) }
        }
        Text(
            "© OpenStreetMap, OpenSeaMap",
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(4.dp)
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.7f), RoundedCornerShape(4.dp))
                .padding(horizontal = 4.dp),
        )
    }
}

/** Zeichnet eigenes Boot, Kurs, Track, AIS-Ziele und Ankerkreis. */
private class BoatOverlay : Overlay() {
    var self: LatLon? = null
    var heading: Double? = null
    var track: List<LatLon> = emptyList()
    var ais: List<AisTarget> = emptyList()
    var anchor: AnchorWatch = AnchorWatch()
    var boatColor = android.graphics.Color.CYAN
    var aisColor = android.graphics.Color.YELLOW
    var anchorColor = android.graphics.Color.GREEN

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pt = Point()
    private val pt2 = Point()

    override fun draw(c: Canvas, mv: MapView, shadow: Boolean) {
        if (shadow) return
        val pj = mv.projection

        // Track
        if (track.size > 1) {
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 5f
            paint.color = boatColor
            paint.alpha = 140
            var first = true
            var lx = 0f
            var ly = 0f
            for (p in track) {
                pj.toPixels(GeoPoint(p.lat, p.lon), pt)
                if (!first) c.drawLine(lx, ly, pt.x.toFloat(), pt.y.toFloat(), paint)
                lx = pt.x.toFloat(); ly = pt.y.toFloat(); first = false
            }
            paint.alpha = 255
        }

        // Ankerkreis
        anchor.position?.let { ap ->
            pj.toPixels(GeoPoint(ap.lat, ap.lon), pt)
            val edge = Geo.destination(ap, 0.0, anchor.radiusM)
            pj.toPixels(GeoPoint(edge.lat, edge.lon), pt2)
            val rpx = hypot((pt2.x - pt.x).toFloat(), (pt2.y - pt.y).toFloat())
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 5f
            paint.color = anchorColor
            c.drawCircle(pt.x.toFloat(), pt.y.toFloat(), rpx, paint)
            paint.style = Paint.Style.FILL
            c.drawCircle(pt.x.toFloat(), pt.y.toFloat(), 10f, paint)
        }

        // AIS-Ziele
        for (t in ais) {
            val p = t.position ?: continue
            pj.toPixels(GeoPoint(p.lat, p.lon), pt)
            drawShip(c, pt.x.toFloat(), pt.y.toFloat(), (t.cogDeg ?: 0.0).toFloat(), 22f, aisColor)
            paint.style = Paint.Style.FILL
            paint.color = aisColor
            paint.textSize = 30f
            c.drawText(t.name, pt.x + 26f, pt.y - 10f, paint)
            // Kursvektor 6 Minuten
            val sog = t.sogKn ?: 0.0
            if (sog > 0.3 && t.cogDeg != null) {
                val end = Geo.destination(p, t.cogDeg, sog * 1852.0 / 10.0)
                pj.toPixels(GeoPoint(end.lat, end.lon), pt2)
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 3f
                c.drawLine(pt.x.toFloat(), pt.y.toFloat(), pt2.x.toFloat(), pt2.y.toFloat(), paint)
            }
        }

        // Eigenes Boot
        self?.let { s ->
            pj.toPixels(GeoPoint(s.lat, s.lon), pt)
            drawShip(c, pt.x.toFloat(), pt.y.toFloat(), (heading ?: 0.0).toFloat(), 32f, boatColor)
        }
    }

    private fun drawShip(c: Canvas, x: Float, y: Float, deg: Float, size: Float, color: Int) {
        c.save()
        c.rotate(deg, x, y)
        val path = android.graphics.Path().apply {
            moveTo(x, y - size)
            lineTo(x + size * 0.5f, y + size * 0.7f)
            lineTo(x, y + size * 0.4f)
            lineTo(x - size * 0.5f, y + size * 0.7f)
            close()
        }
        paint.style = Paint.Style.FILL
        paint.color = color
        c.drawPath(path, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 3f
        paint.color = android.graphics.Color.BLACK
        c.drawPath(path, paint)
        c.restore()
    }
}

@Composable
private fun AisList(targets: List<AisTarget>, modifier: Modifier) {
    val inst = LocalInstrumentColors.current
    Column(modifier.padding(8.dp)) {
        Text(
            "AIS-Ziele (${targets.size})",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
        )
        LazyColumn {
            items(targets, key = { it.context }) { t ->
                val danger = (t.cpaNm ?: 99.0) < 0.5 && (t.tcpaMin ?: -1.0) in 0.0..30.0
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp)
                        .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(10.dp))
                        .padding(8.dp),
                ) {
                    Text(
                        t.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = if (danger) inst.port else MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        "${fmt(t.distanceNm, 2)} sm · ${fmt(t.bearingDeg, 0)}° · " +
                            "${fmt(t.sogKn)} kn · CPA ${fmt(t.cpaNm, 2)} sm" +
                            (t.tcpaMin?.takeIf { it >= 0 }?.let { " in ${fmt(it, 0)} min" } ?: ""),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
