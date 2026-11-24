package de.schnarkowski.selene

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import de.schnarkowski.selene.data.BoatData
import de.schnarkowski.selene.data.ConnState
import de.schnarkowski.selene.data.ConnStatus
import de.schnarkowski.selene.data.Geo
import de.schnarkowski.selene.data.LatLon
import de.schnarkowski.selene.data.SignalKClient
import de.schnarkowski.selene.data.SignalKStore
import de.schnarkowski.selene.data.Simulator
import de.schnarkowski.selene.data.SkSnapshot
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

enum class SourceType(val label: String) {
    SIMULATOR("Simulator"),
    DEMO("Signal-K-Demoserver"),
    BOAT("Selene (Bordnetz)"),
}

enum class ThemeMode(val label: String) { DAY("Tag"), DARK("Dunkel"), NIGHT("Nacht (rot)") }

data class AppSettings(
    val source: SourceType = SourceType.SIMULATOR,
    val boatHost: String = "einstein:3000",
    val theme: ThemeMode = ThemeMode.DARK,
    val keepScreenOn: Boolean = true,
    val batteryNames: String = "",
) {
    /** "279=Hausbatterie" je Zeile → Map */
    fun batteryNameMap(): Map<String, String> = batteryNames.lines()
        .mapNotNull { line ->
            val i = line.indexOf('=')
            if (i <= 0) null else line.substring(0, i).trim() to line.substring(i + 1).trim()
        }.toMap()

    fun streamUrl(): String = when (source) {
        SourceType.DEMO -> "wss://demo.signalk.org/signalk/v1/stream?subscribe=all"
        else -> {
            val h = boatHost.trim().removeSuffix("/")
            if (h.startsWith("ws://") || h.startsWith("wss://")) {
                if (h.contains("/signalk/")) h else "$h/signalk/v1/stream?subscribe=all"
            } else {
                "ws://$h/signalk/v1/stream?subscribe=all"
            }
        }
    }
}

data class AnchorWatch(
    val position: LatLon? = null,
    val radiusM: Double = 40.0,
    val distanceM: Double? = null,
    val bearingDeg: Double? = null,
    val maxDistanceM: Double = 0.0,
    val alarm: Boolean = false,
    val muted: Boolean = false,
) {
    val active: Boolean get() = position != null
}

/** Zuordnung passend zum Export vom 24.11.2026; "-" blendet doppelte Quellen aus. */
const val DEFAULT_NAMES = "279=Hausbatterie\n278=Generatorbatterie\n0=Batterie am Solarregler\n1=-\n239=-\n6=-\n7=-"

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val prefs = app.getSharedPreferences("selene", Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(loadSettings())
    val settings: StateFlow<AppSettings> = _settings

    private val store = SignalKStore()
    val snapshot: StateFlow<SkSnapshot> = store.snapshot

    private val _boat = MutableStateFlow(BoatData())
    val boat: StateFlow<BoatData> = _boat

    private val _status = MutableStateFlow(ConnStatus())
    val status: StateFlow<ConnStatus> = _status

    private val _anchor = MutableStateFlow(AnchorWatch())
    val anchor: StateFlow<AnchorWatch> = _anchor

    private val _track = MutableStateFlow<List<LatLon>>(emptyList())
    val track: StateFlow<List<LatLon>> = _track

    private var client: SignalKClient? = null
    private var simulator: Simulator? = null
    private var statusJob: Job? = null

    init {
        // Werte gedrosselt (4x pro Sekunde) veröffentlichen
        viewModelScope.launch {
            while (isActive) {
                store.publishIfDirty()
                delay(250)
            }
        }
        viewModelScope.launch {
            store.snapshot.collectLatest { snap ->
                val b = BoatData.from(snap, _settings.value.batteryNameMap())
                _boat.value = b
                b.position?.let { updateTrack(it); updateAnchor(it) }
            }
        }
        connect()
    }

    fun updateSettings(new: AppSettings) {
        val old = _settings.value
        _settings.value = new
        saveSettings(new)
        if (old.source != new.source || old.boatHost != new.boatHost) {
            _track.value = emptyList()
            connect()
        }
    }

    fun reconnect() = connect()

    private fun connect() {
        client?.stop()
        simulator?.stop()
        statusJob?.cancel()
        client = null
        simulator = null
        store.clear()
        val s = _settings.value
        if (s.source == SourceType.SIMULATOR) {
            simulator = Simulator(store, viewModelScope).also { it.start() }
            _status.value = ConnStatus(ConnState.VERBUNDEN, "Simulator")
        } else {
            val c = SignalKClient(store, s.streamUrl(), viewModelScope)
            client = c
            c.start()
            statusJob = viewModelScope.launch { c.status.collectLatest { _status.value = it } }
        }
    }

    private fun updateTrack(p: LatLon) {
        val t = _track.value
        val last = t.lastOrNull()
        if (last == null || Geo.distanceM(last, p) > 15.0) {
            _track.value = (t + p).takeLast(2000)
        }
    }

    // ---------- Ankerwache ----------

    fun dropAnchor() {
        val p = _boat.value.position ?: return
        _anchor.value = _anchor.value.copy(position = p, distanceM = 0.0, maxDistanceM = 0.0, alarm = false, muted = false)
    }

    fun raiseAnchor() {
        _anchor.value = AnchorWatch(radiusM = _anchor.value.radiusM)
    }

    fun setAnchorRadius(r: Double) {
        _anchor.value = _anchor.value.copy(radiusM = r)
        _boat.value.position?.let { updateAnchor(it) }
    }

    fun muteAlarm() {
        _anchor.value = _anchor.value.copy(muted = true)
    }

    private fun updateAnchor(p: LatLon) {
        val a = _anchor.value
        val ap = a.position ?: return
        val dist = Geo.distanceM(ap, p)
        val alarm = dist > a.radiusM
        _anchor.value = a.copy(
            distanceM = dist,
            bearingDeg = Geo.bearingDeg(ap, p),
            maxDistanceM = maxOf(a.maxDistanceM, dist),
            alarm = alarm,
            muted = if (alarm) a.muted else false,
        )
    }

    // ---------- Einstellungen ----------

    private fun loadSettings() = AppSettings(
        source = runCatching { SourceType.valueOf(prefs.getString("source", null) ?: "") }.getOrDefault(SourceType.SIMULATOR),
        boatHost = prefs.getString("boatHost", null) ?: "einstein:3000",
        theme = runCatching { ThemeMode.valueOf(prefs.getString("theme", null) ?: "") }.getOrDefault(ThemeMode.DARK),
        keepScreenOn = prefs.getBoolean("keepScreenOn", true),
        batteryNames = prefs.getString("batteryNames", null) ?: DEFAULT_NAMES,
    )

    private fun saveSettings(s: AppSettings) {
        prefs.edit()
            .putString("source", s.source.name)
            .putString("boatHost", s.boatHost)
            .putString("theme", s.theme.name)
            .putBoolean("keepScreenOn", s.keepScreenOn)
            .putString("batteryNames", s.batteryNames)
            .apply()
    }

    override fun onCleared() {
        client?.stop()
        simulator?.stop()
    }
}
