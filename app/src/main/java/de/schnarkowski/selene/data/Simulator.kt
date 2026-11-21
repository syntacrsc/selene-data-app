package de.schnarkowski.selene.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Erzeugt plausible Bootsdaten (in Signal-K-SI-Einheiten), damit alle Seiten
 * der App ohne Bordverbindung getestet werden können.
 */
class Simulator(private val store: SignalKStore, private val scope: CoroutineScope) {
    private var job: Job? = null

    fun start() {
        store.selfContext = "vessels.self"
        job = scope.launch {
            var lat = 43.8350   // vor Santo Stefano al Mare
            var lon = 7.9000
            var t = 0.0
            var soc = 0.86
            val ais = mutableListOf(
                SimTarget("vessels.urn:mrn:imo:mmsi:247123450", "AURORA", 43.86, 7.88, 6.0, 120.0),
                SimTarget("vessels.urn:mrn:imo:mmsi:227654320", "MISTRAL II", 43.80, 7.95, 11.0, 300.0),
            )
            while (isActive) {
                val dt = 1.0
                t += dt
                val headingDeg = 210.0 + 6 * sin(t / 40.0)
                val twdDeg = 265.0 + 8 * sin(t / 90.0)
                val tws = (14.0 + 3 * sin(t / 25.0) + Random.nextDouble(-0.8, 0.8)) * KN
                val stw = (6.8 + 0.6 * sin(t / 30.0)) * KN
                val twaRad = norm180(twdDeg - headingDeg).toRad()
                // scheinbarer Wind aus wahrem Wind und Fahrt
                val wx = tws * cos(twaRad) + stw
                val wy = tws * sin(twaRad)
                val aws = sqrt(wx * wx + wy * wy)
                val awa = atan2(wy, wx)

                val hdg = headingDeg.toRad()
                lat += (stw * cos(hdg) * dt) / 111_120.0
                lon += (stw * sin(hdg) * dt) / (111_120.0 * cos(lat.toRad()))

                val roll = (-14.0 + 3 * sin(t / 6.0) + Random.nextDouble(-1.0, 1.0)).toRad()
                val pitch = (1.5 * sin(t / 3.0)).toRad()
                soc = (soc - 0.00002).coerceAtLeast(0.2)

                with(store) {
                    put(null, "navigation.position", mapOf("latitude" to lat, "longitude" to lon))
                    put(null, "navigation.headingMagnetic", hdg)
                    put(null, "navigation.courseOverGroundTrue", (headingDeg + 3).toRad())
                    put(null, "navigation.speedOverGround", stw * 1.04)
                    put(null, "navigation.speedThroughWater", stw)
                    put(null, "navigation.attitude", mapOf("roll" to roll, "pitch" to pitch, "yaw" to hdg))
                    put(null, "navigation.log", 52_300_000.0 + t * stw)
                    put(null, "navigation.trip.log", 41_000.0 + t * stw)
                    put(null, "environment.wind.speedApparent", aws)
                    put(null, "environment.wind.angleApparent", awa)
                    put(null, "environment.wind.speedTrue", tws)
                    put(null, "environment.wind.angleTrueWater", twaRad)
                    put(null, "environment.wind.directionTrue", twdDeg.toRad())
                    put(null, "environment.depth.belowTransducer", 38.0 + 6 * sin(t / 50.0))
                    put(null, "environment.water.temperature", 273.15 + 19.4)
                    put(null, "steering.autopilot.state", "auto")
                    put(null, "steering.autopilot.target.headingMagnetic", 210.0.toRad())
                    put(null, "steering.rudderAngle", (4 * sin(t / 5.0)).toRad())

                    put(null, "electrical.batteries.haus.voltage", 12.9 + 0.1 * sin(t / 20.0))
                    put(null, "electrical.batteries.haus.current", -18.5 + 4 * sin(t / 15.0))
                    put(null, "electrical.batteries.haus.stateOfCharge", soc)
                    put(null, "electrical.batteries.haus.timeRemaining", soc * 400 * 3600 / 18.5)
                    put(null, "electrical.batteries.motor.voltage", 12.85)
                    put(null, "electrical.batteries.motor.current", 0.1)
                    put(null, "electrical.batteries.motor.stateOfCharge", 0.98)
                    put(null, "electrical.batteries.generator.voltage", 12.78)
                    put(null, "electrical.batteries.generator.current", 0.0)
                    put(null, "electrical.batteries.generator.stateOfCharge", 0.95)
                    put(null, "electrical.solar.mppt.panelPower", 240.0 + 40 * sin(t / 60.0))
                    put(null, "electrical.solar.mppt.chargingMode", "bulk")
                    put(null, "electrical.inverterCharger.quattro.acin.power", 0.0)
                    put(null, "electrical.inverterCharger.quattro.mode", "inverting")
                }

                ais.forEach { a ->
                    a.move(dt)
                    store.put(a.ctx, "name", a.name)
                    store.put(a.ctx, "mmsi", a.ctx.substringAfterLast(":"))
                    store.put(a.ctx, "navigation.position", mapOf("latitude" to a.lat, "longitude" to a.lon))
                    store.put(a.ctx, "navigation.speedOverGround", a.sogKn * KN)
                    store.put(a.ctx, "navigation.courseOverGroundTrue", a.cogDeg.toRad())
                }
                delay(1000)
            }
        }
    }

    fun stop() {
        job?.cancel()
    }

    private class SimTarget(
        val ctx: String, val name: String,
        var lat: Double, var lon: Double, val sogKn: Double, val cogDeg: Double,
    ) {
        fun move(dt: Double) {
            val v = sogKn * KN
            lat += v * cos(cogDeg.toRad()) * dt / 111_120.0
            lon += v * sin(cogDeg.toRad()) * dt / (111_120.0 * cos(lat.toRad()))
        }
    }

    private companion object {
        const val KN = 0.514444
        fun Double.toRad() = this * PI / 180.0
        fun norm180(d: Double): Double {
            var x = d % 360.0
            if (x > 180) x -= 360.0
            if (x < -180) x += 360.0
            return x
        }
    }
}
