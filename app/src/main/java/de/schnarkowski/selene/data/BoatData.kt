package de.schnarkowski.selene.data

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

const val MS_TO_KN = 1.943844
private const val RAD_TO_DEG = 180.0 / PI

data class LatLon(val lat: Double, val lon: Double)

data class Battery(
    val id: String,
    val name: String,
    val voltage: Double?,
    val current: Double?,
    val soc: Double?,          // 0..100 %
    val timeRemainingH: Double?,
    val temperatureC: Double?,
) {
    val power: Double? get() = if (voltage != null && current != null) voltage * current else null
}

data class Tank(
    val id: String,
    val name: String,
    val type: String,
    val levelPct: Double?,
    val capacityL: Double?,
    val remainingL: Double?,
)

data class ActiveAlarm(val path: String, val state: String, val message: String)

data class AisTarget(
    val context: String,
    val name: String,
    val mmsi: String,
    val position: LatLon?,
    val sogKn: Double?,
    val cogDeg: Double?,
    val distanceNm: Double?,
    val bearingDeg: Double?,
    val cpaNm: Double?,
    val tcpaMin: Double?,
)

/** Alle Werte in Anzeigeeinheiten (kn, Grad, m, °C, %). */
data class BoatData(
    val aws: Double? = null,
    val awa: Double? = null,           // -180..180, + = Steuerbord
    val tws: Double? = null,
    val twa: Double? = null,
    val twd: Double? = null,
    val stw: Double? = null,
    val sog: Double? = null,
    val cog: Double? = null,
    val heading: Double? = null,
    val depth: Double? = null,
    val waterTemp: Double? = null,
    val roll: Double? = null,          // + = Krängung nach Steuerbord
    val pitch: Double? = null,         // + = Bug hoch
    val position: LatLon? = null,
    val logNm: Double? = null,
    val tripNm: Double? = null,
    val apState: String? = null,
    val apTargetHeading: Double? = null,
    val apTargetWind: Double? = null,
    val rudder: Double? = null,
    val batteries: List<Battery> = emptyList(),
    val solarPower: Double? = null,
    val solarMode: String? = null,
    val solarYieldTodayKWh: Double? = null,
    val solarYieldYesterdayKWh: Double? = null,
    val tanks: List<Tank> = emptyList(),
    val alarms: List<ActiveAlarm> = emptyList(),
    val electricalOther: List<Pair<String, Any?>> = emptyList(),
    val ais: List<AisTarget> = emptyList(),
) {
    companion object {
        fun from(s: SkSnapshot, batteryNames: Map<String, String>): BoatData {
            val v = s.self
            fun d(path: String): Double? = v[path] as? Double
            fun str(path: String): String? = v[path]?.toString()

            val aws = d("environment.wind.speedApparent")?.times(MS_TO_KN)
            val awa = d("environment.wind.angleApparent")?.let { norm180(it * RAD_TO_DEG) }
            val stw = d("navigation.speedThroughWater")?.times(MS_TO_KN)
            val sog = d("navigation.speedOverGround")?.times(MS_TO_KN)
            val heading = (d("navigation.headingMagnetic") ?: d("navigation.headingTrue"))
                ?.let { norm360(it * RAD_TO_DEG) }

            var tws = d("environment.wind.speedTrue")?.times(MS_TO_KN)
            var twa = (d("environment.wind.angleTrueWater") ?: d("environment.wind.angleTrueGround"))
                ?.let { norm180(it * RAD_TO_DEG) }
            // Wahren Wind selbst berechnen, falls der Bus ihn nicht liefert
            if ((tws == null || twa == null) && aws != null && awa != null) {
                val boat = stw ?: sog ?: 0.0
                val a = awa / RAD_TO_DEG
                val x = aws * cos(a) - boat
                val y = aws * sin(a)
                tws = sqrt(x * x + y * y)
                twa = norm180(atan2(y, x) * RAD_TO_DEG)
            }
            val twd = d("environment.wind.directionTrue")?.let { norm360(it * RAD_TO_DEG) }
                ?: if (twa != null && heading != null) norm360(heading + twa) else null

            val depth = d("environment.depth.belowTransducer")
                ?: d("environment.depth.belowSurface")
                ?: d("environment.depth.belowKeel")

            @Suppress("UNCHECKED_CAST")
            val att = v["navigation.attitude"] as? Map<String, Any?>
            val roll = (att?.get("roll") as? Double)?.times(RAD_TO_DEG)
            val pitch = (att?.get("pitch") as? Double)?.times(RAD_TO_DEG)

            val position = toLatLon(v["navigation.position"])

            // Batterien: electrical.batteries.<id>.<wert>
            val battIds = v.keys.filter { it.startsWith("electrical.batteries.") }
                .map { it.removePrefix("electrical.batteries.").substringBefore(".") }
                .distinct().sorted()
            val batteries = battIds.filter { batteryNames[it] != "-" }.map { id ->
                val p = "electrical.batteries.$id"
                Battery(
                    id = id,
                    name = batteryNames[id] ?: translateName(v["$p.name"] as? String) ?: defaultBatteryName(id),
                    voltage = d("$p.voltage"),
                    current = d("$p.current"),
                    soc = (d("$p.capacity.stateOfCharge") ?: d("$p.stateOfCharge"))?.times(100.0),
                    timeRemainingH = (d("$p.capacity.timeRemaining") ?: d("$p.timeRemaining"))?.div(3600.0),
                    temperatureC = d("$p.temperature")?.minus(273.15),
                )
            }.sortedBy { if (batteryNames.containsKey(it.id) || v.containsKey("electrical.batteries.${it.id}.name")) 0 else 1 }
            val solarPower = v.keys.filter { it.startsWith("electrical.solar.") && it.endsWith(".panelPower") }
                .mapNotNull { v[it] as? Double }.takeIf { it.isNotEmpty() }?.sum()
            val solarMode = v.keys.firstOrNull {
                it.startsWith("electrical.solar.") && (it.endsWith(".chargingMode") || it.endsWith(".controllerMode"))
            }?.let { translateMode(v[it]?.toString()) }
            fun sumJ(suffix: String) = v.keys.filter { it.startsWith("electrical.solar.") && it.endsWith(suffix) }
                .mapNotNull { v[it] as? Double }.takeIf { it.isNotEmpty() }?.sum()?.div(3_600_000.0)

            // Tanks: tanks.<typ>.<id>.currentLevel (0..1), capacity/remaining in m³
            val tankKeys = v.keys.filter { it.startsWith("tanks.") }
                .map { it.split(".") }.filter { it.size >= 4 }
                .map { it[1] to it[2] }.distinct()
            val tanks = tankKeys.filter { batteryNames[it.second] != "-" }.map { (type, id) ->
                val p = "tanks.$type.$id"
                Tank(
                    id = id,
                    name = batteryNames[id] ?: translateName(v["$p.name"] as? String)
                        ?: translateName(v["electrical.tanks.$id.name"] as? String)
                        ?: "${tankType(type)} $id",
                    type = tankType(type),
                    levelPct = d("$p.currentLevel")?.times(100.0),
                    capacityL = d("$p.capacity")?.times(1000.0),
                    remainingL = d("$p.remaining")?.times(1000.0),
                )
            }.sortedBy { it.name }

            // Aktive Alarme: notifications.* mit Zustand ungleich normal
            val alarms = v.entries.filter { it.key.startsWith("notifications.") }.mapNotNull { (k, value) ->
                val m = value as? Map<*, *> ?: return@mapNotNull null
                val state = m["state"]?.toString() ?: return@mapNotNull null
                if (state == "normal" || state == "nominal") null
                else ActiveAlarm(k.removePrefix("notifications."), state, m["message"]?.toString() ?: k)
            }
            val electricalOther = v.entries
                .filter { it.key.startsWith("electrical.") && !it.key.startsWith("electrical.batteries.") }
                .sortedBy { it.key }
                .map { it.key.removePrefix("electrical.") to it.value }

            val ais = s.others.map { (ctx, o) -> aisTarget(ctx, o, position, sog, d("navigation.courseOverGroundTrue")) }
                .sortedBy { it.distanceNm ?: Double.MAX_VALUE }

            return BoatData(
                aws = aws, awa = awa, tws = tws, twa = twa, twd = twd,
                stw = stw, sog = sog,
                cog = d("navigation.courseOverGroundTrue")?.let { norm360(it * RAD_TO_DEG) },
                heading = heading,
                depth = depth,
                waterTemp = d("environment.water.temperature")?.minus(273.15),
                roll = roll, pitch = pitch,
                position = position,
                logNm = d("navigation.log")?.div(1852.0),
                tripNm = d("navigation.trip.log")?.div(1852.0),
                apState = str("steering.autopilot.state"),
                apTargetHeading = (d("steering.autopilot.target.headingMagnetic")
                    ?: d("steering.autopilot.target.headingTrue"))?.let { norm360(it * RAD_TO_DEG) },
                apTargetWind = d("steering.autopilot.target.windAngleApparent")?.let { norm180(it * RAD_TO_DEG) },
                rudder = d("steering.rudderAngle")?.times(RAD_TO_DEG),
                batteries = batteries,
                solarPower = solarPower,
                solarMode = solarMode,
                solarYieldTodayKWh = sumJ(".yieldToday"),
                solarYieldYesterdayKWh = sumJ(".yieldYesterday"),
                tanks = tanks,
                alarms = alarms,
                electricalOther = electricalOther,
                ais = ais,
            )
        }

        /** Englische Namen aus dem Cerbo übersetzen. */
        private fun translateName(n: String?): String? = when (n?.trim()?.lowercase()) {
            null, "" -> null
            "house battery" -> "Hausbatterie"
            "genset battery", "generator battery" -> "Generatorbatterie"
            "engine battery", "starter battery", "start battery" -> "Motorbatterie"
            "fresh water port" -> "Frischwasser Bb"
            "fresh water stb", "fresh water starboard" -> "Frischwasser Stb"
            else -> n.trim()
        }

        private fun translateMode(m: String?): String? = when (m?.lowercase()) {
            null -> null
            "not charging" -> "lädt nicht"
            "bulk" -> "Bulk"
            "absorption" -> "Absorption"
            "float" -> "Erhaltung"
            "off" -> "aus"
            else -> m
        }

        private fun tankType(t: String) = when (t) {
            "freshWater" -> "Frischwasser"
            "wasteWater" -> "Grauwasser"
            "blackWater" -> "Fäkalien"
            "fuel" -> "Diesel"
            else -> t
        }

        private fun defaultBatteryName(id: String) = when (id.lowercase()) {
            "haus", "house" -> "Hausbatterie"
            "motor", "engine", "starter" -> "Motorbatterie"
            "generator" -> "Generatorbatterie"
            else -> "Batterie $id"
        }

        private fun aisTarget(
            ctx: String, o: Map<String, Any?>, own: LatLon?, ownSogKn: Double?, ownCogRad: Double?,
        ): AisTarget {
            val pos = toLatLon(o["navigation.position"])
            val sog = (o["navigation.speedOverGround"] as? Double)?.times(MS_TO_KN)
            val cogRad = (o["navigation.courseOverGroundTrue"] as? Double) ?: (o["navigation.headingTrue"] as? Double)
            val mmsi = o["mmsi"]?.toString() ?: ctx.substringAfterLast(":")
            var dist: Double? = null
            var brg: Double? = null
            var cpa: Double? = null
            var tcpa: Double? = null
            if (own != null && pos != null) {
                dist = Geo.distanceM(own, pos) / 1852.0
                brg = Geo.bearingDeg(own, pos)
                val r = Geo.cpa(own, ownSogKn ?: 0.0, ownCogRad ?: 0.0, pos, sog ?: 0.0, cogRad ?: 0.0)
                cpa = r.first
                tcpa = r.second
            }
            return AisTarget(
                context = ctx,
                name = (o["name"] as? String)?.trim()?.ifEmpty { null } ?: mmsi,
                mmsi = mmsi,
                position = pos,
                sogKn = sog,
                cogDeg = cogRad?.let { norm360(it * RAD_TO_DEG) },
                distanceNm = dist, bearingDeg = brg, cpaNm = cpa, tcpaMin = tcpa,
            )
        }
    }
}

fun toLatLon(v: Any?): LatLon? {
    val m = v as? Map<*, *> ?: return null
    val lat = m["latitude"] as? Double ?: return null
    val lon = m["longitude"] as? Double ?: return null
    return LatLon(lat, lon)
}

fun norm180(d: Double): Double {
    var x = d % 360.0
    if (x > 180) x -= 360.0
    if (x < -180) x += 360.0
    return x
}

fun norm360(d: Double): Double {
    var x = d % 360.0
    if (x < 0) x += 360.0
    return x
}

object Geo {
    private const val R = 6_371_000.0

    fun distanceM(a: LatLon, b: LatLon): Double {
        val dLat = Math.toRadians(b.lat - a.lat)
        val dLon = Math.toRadians(b.lon - a.lon)
        val h = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(a.lat)) * cos(Math.toRadians(b.lat)) * sin(dLon / 2) * sin(dLon / 2)
        return 2 * R * asin(sqrt(h))
    }

    fun bearingDeg(a: LatLon, b: LatLon): Double {
        val la1 = Math.toRadians(a.lat)
        val la2 = Math.toRadians(b.lat)
        val dLon = Math.toRadians(b.lon - a.lon)
        val y = sin(dLon) * cos(la2)
        val x = cos(la1) * sin(la2) - sin(la1) * cos(la2) * cos(dLon)
        return norm360(Math.toDegrees(atan2(y, x)))
    }

    /** Punkt in Entfernung [meters] und Richtung [bearingDeg] von [a]. */
    fun destination(a: LatLon, bearingDeg: Double, meters: Double): LatLon {
        val dr = meters / R
        val br = Math.toRadians(bearingDeg)
        val la1 = Math.toRadians(a.lat)
        val lo1 = Math.toRadians(a.lon)
        val la2 = asin(sin(la1) * cos(dr) + cos(la1) * sin(dr) * cos(br))
        val lo2 = lo1 + atan2(sin(br) * sin(dr) * cos(la1), cos(dr) - sin(la1) * sin(la2))
        return LatLon(Math.toDegrees(la2), Math.toDegrees(lo2))
    }

    /** CPA (sm) und TCPA (min); TCPA negativ = Annäherung vorbei. */
    fun cpa(
        own: LatLon, ownSogKn: Double, ownCogRad: Double,
        tgt: LatLon, tgtSogKn: Double, tgtCogRad: Double,
    ): Pair<Double?, Double?> {
        // lokale ebene Projektion in Seemeilen
        val px = (tgt.lon - own.lon) * 60.0 * cos(Math.toRadians(own.lat))
        val py = (tgt.lat - own.lat) * 60.0
        val vx = tgtSogKn * sin(tgtCogRad) - ownSogKn * sin(ownCogRad)
        val vy = tgtSogKn * cos(tgtCogRad) - ownSogKn * cos(ownCogRad)
        val v2 = vx * vx + vy * vy
        if (v2 < 1e-6) return sqrt(px * px + py * py) to null
        val tH = -(px * vx + py * vy) / v2
        val t = max(tH, 0.0)
        val cx = px + vx * t
        val cy = py + vy * t
        return sqrt(cx * cx + cy * cy) to tH * 60.0
    }
}

fun fmt(v: Double?, decimals: Int = 1): String =
    if (v == null || v.isNaN()) "--" else String.format(java.util.Locale.GERMANY, "%.${decimals}f", v)

/** Winkel mit Seite: "32° Stb" / "45° Bb". */
fun fmtSide(v: Double?): String =
    if (v == null) "--" else "${fmt(abs(v), 0)}° ${if (v >= 0) "Stb" else "Bb"}"
