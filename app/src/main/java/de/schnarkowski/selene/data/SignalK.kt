package de.schnarkowski.selene.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Momentaufnahme aller empfangenen Signal-K-Werte.
 * Werte sind Double, String, Boolean oder Map<String, Any?> (z. B. Position).
 */
data class SkSnapshot(
    val self: Map<String, Any?> = emptyMap(),
    val others: Map<String, Map<String, Any?>> = emptyMap(),
    val lastUpdate: Long = 0L,
)

/** Sammelt Deltas und veröffentlicht sie gedrosselt als [SkSnapshot]. */
class SignalKStore {
    private val lock = Any()
    private val self = HashMap<String, Any?>()
    private val others = HashMap<String, HashMap<String, Any?>>()
    private val otherSeen = HashMap<String, Long>()

    @Volatile
    private var dirty = false

    @Volatile
    var selfContext: String? = null

    private val _snapshot = MutableStateFlow(SkSnapshot())
    val snapshot: StateFlow<SkSnapshot> = _snapshot

    fun clear() {
        synchronized(lock) {
            self.clear()
            others.clear()
            otherSeen.clear()
            selfContext = null
            dirty = true
        }
        publishIfDirty()
    }

    fun put(context: String?, path: String, value: Any?) {
        synchronized(lock) {
            val isSelf = context == null || context == "vessels.self" || context == selfContext
            if (isSelf) {
                self[path] = value
            } else if (context!!.startsWith("vessels.")) {
                others.getOrPut(context) { HashMap() }[path] = value
                otherSeen[context] = System.currentTimeMillis()
            }
            dirty = true
        }
    }

    fun publishIfDirty() {
        if (!dirty) return
        synchronized(lock) {
            dirty = false
            // AIS-Ziele, von denen 10 Minuten nichts kam, verwerfen
            val now = System.currentTimeMillis()
            val stale = otherSeen.filterValues { now - it > 10 * 60_000L }.keys
            stale.forEach { others.remove(it); otherSeen.remove(it) }
            _snapshot.value = SkSnapshot(
                self = HashMap(self),
                others = others.mapValues { HashMap(it.value) },
                lastUpdate = now,
            )
        }
    }

    /** Verarbeitet eine Signal-K-Nachricht (Hello oder Delta). */
    fun handleMessage(text: String) {
        val json = try {
            JSONObject(text)
        } catch (e: Exception) {
            return
        }
        if (json.has("self") && !json.has("updates")) {
            val s = json.optString("self")
            if (s.isNotEmpty()) selfContext = if (s.startsWith("vessels.")) s else "vessels.$s"
            return
        }
        val updates = json.optJSONArray("updates") ?: return
        val context = if (json.has("context")) json.optString("context") else null
        for (i in 0 until updates.length()) {
            val update = updates.optJSONObject(i) ?: continue
            val values = update.optJSONArray("values") ?: continue
            for (j in 0 until values.length()) {
                val v = values.optJSONObject(j) ?: continue
                val path = v.optString("path")
                val value = jsonToKotlin(v.opt("value"))
                if (path.isEmpty()) {
                    // Wurzelwerte wie name, mmsi
                    if (value is Map<*, *>) {
                        value.forEach { (k, vv) -> put(context, k.toString(), vv) }
                    }
                } else {
                    put(context, path, value)
                }
            }
        }
    }
}

fun jsonToKotlin(v: Any?): Any? = when (v) {
    null, JSONObject.NULL -> null
    is JSONObject -> {
        val m = HashMap<String, Any?>()
        v.keys().forEach { k -> m[k] = jsonToKotlin(v.opt(k)) }
        m
    }
    is JSONArray -> (0 until v.length()).map { jsonToKotlin(v.opt(it)) }
    is Number -> v.toDouble()
    else -> v
}

enum class ConnState { GETRENNT, VERBINDE, VERBUNDEN, FEHLER }

data class ConnStatus(val state: ConnState = ConnState.GETRENNT, val message: String = "")

/** WebSocket-Verbindung zum Signal-K-Server mit automatischer Wiederverbindung. */
class SignalKClient(
    private val store: SignalKStore,
    private val url: String,
    private val scope: CoroutineScope,
) {
    private val http = OkHttpClient.Builder()
        .pingInterval(20, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .connectTimeout(8, TimeUnit.SECONDS)
        .build()

    private val _status = MutableStateFlow(ConnStatus())
    val status: StateFlow<ConnStatus> = _status

    private var socket: WebSocket? = null
    private var job: Job? = null

    @Volatile
    private var closedSignal = false

    fun start() {
        job = scope.launch {
            var backoff = 1_000L
            while (isActive) {
                closedSignal = false
                _status.value = ConnStatus(ConnState.VERBINDE, url)
                val request = Request.Builder().url(url).build()
                socket = http.newWebSocket(request, object : WebSocketListener() {
                    override fun onOpen(webSocket: WebSocket, response: Response) {
                        _status.value = ConnStatus(ConnState.VERBUNDEN, url)
                    }

                    override fun onMessage(webSocket: WebSocket, text: String) {
                        store.handleMessage(text)
                    }

                    override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                        closedSignal = true
                    }

                    override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                        _status.value = ConnStatus(ConnState.FEHLER, t.message ?: t.javaClass.simpleName)
                        closedSignal = true
                    }
                })
                // warten, bis die Verbindung endet
                while (isActive && !closedSignal) {
                    delay(500)
                    if (_status.value.state == ConnState.VERBUNDEN) backoff = 1_000L
                }
                socket?.cancel()
                if (!isActive) break
                delay(backoff)
                backoff = (backoff * 2).coerceAtMost(30_000L)
            }
        }
    }

    fun stop() {
        job?.cancel()
        socket?.close(1000, "bye")
        socket = null
        _status.value = ConnStatus(ConnState.GETRENNT)
    }
}
