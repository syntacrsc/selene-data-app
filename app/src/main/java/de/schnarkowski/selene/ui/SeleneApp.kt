package de.schnarkowski.selene.ui

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Anchor
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.schnarkowski.selene.MainViewModel
import de.schnarkowski.selene.data.ConnState
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

private data class Tab(val title: String, val icon: ImageVector)

private val tabs = listOf(
    Tab("Segeln", Icons.Filled.Air),
    Tab("Lage", Icons.Filled.ScreenRotation),
    Tab("Energie", Icons.Filled.BatteryChargingFull),
    Tab("Karte", Icons.Filled.Map),
    Tab("Anker", Icons.Filled.Anchor),
    Tab("Daten", Icons.AutoMirrored.Filled.List),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SeleneApp(vm: MainViewModel) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val boat by vm.boat.collectAsStateWithLifecycle()
    val snapshot by vm.snapshot.collectAsStateWithLifecycle()
    val status by vm.status.collectAsStateWithLifecycle()
    val anchor by vm.anchor.collectAsStateWithLifecycle()
    val track by vm.track.collectAsStateWithLifecycle()

    var tab by rememberSaveable { mutableIntStateOf(0) }
    var showSettings by remember { mutableStateOf(false) }

    // Display an lassen
    val view = LocalView.current
    DisposableEffect(settings.keepScreenOn) {
        view.keepScreenOn = settings.keepScreenOn
        onDispose { view.keepScreenOn = false }
    }

    // Ankeralarm: Ton + Vibration, unabhängig von der angezeigten Seite
    val context = LocalContext.current
    LaunchedEffect(anchor.alarm && !anchor.muted) {
        if (!(anchor.alarm && !anchor.muted)) return@LaunchedEffect
        val tone = runCatching { ToneGenerator(AudioManager.STREAM_ALARM, 100) }.getOrNull()
        @Suppress("DEPRECATION")
        val vib = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        try {
            while (isActive) {
                tone?.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 800)
                vib?.vibrate(VibrationEffect.createOneShot(600, VibrationEffect.DEFAULT_AMPLITUDE))
                delay(1500)
            }
        } finally {
            tone?.release()
        }
    }

    SeleneTheme(settings.theme) {
        BoxWithConstraints(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            val wide = maxWidth >= 600.dp
            Scaffold(
                containerColor = MaterialTheme.colorScheme.background,
                topBar = {
                    TopAppBar(
                        title = { Text("Selene · ${tabs[tab].title}") },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.background,
                            titleContentColor = MaterialTheme.colorScheme.onBackground,
                            actionIconContentColor = MaterialTheme.colorScheme.onBackground,
                        ),
                        actions = {
                            StatusDot(status.state, settings.source.label)
                            IconButton(onClick = { showSettings = true }) {
                                Icon(Icons.Filled.Settings, contentDescription = "Einstellungen")
                            }
                        },
                    )
                },
                bottomBar = {
                    if (!wide) {
                        NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                            tabs.forEachIndexed { i, t ->
                                NavigationBarItem(
                                    selected = tab == i,
                                    onClick = { tab = i },
                                    icon = { Icon(t.icon, contentDescription = t.title) },
                                    label = { Text(t.title, maxLines = 1) },
                                    alwaysShowLabel = false,
                                )
                            }
                        }
                    }
                },
            ) { padding ->
                Row(Modifier.fillMaxSize().padding(padding)) {
                    if (wide) {
                        NavigationRail(containerColor = MaterialTheme.colorScheme.surface) {
                            tabs.forEachIndexed { i, t ->
                                NavigationRailItem(
                                    selected = tab == i,
                                    onClick = { tab = i },
                                    icon = { Icon(t.icon, contentDescription = t.title) },
                                    label = { Text(t.title) },
                                )
                            }
                        }
                    }
                    Column(Modifier.weight(1f).fillMaxSize()) {
                        if (status.state != ConnState.VERBUNDEN) ConnBanner(status.state, status.message)
                        Box(Modifier.weight(1f).fillMaxWidth()) {
                            when (tab) {
                                0 -> SailScreen(boat, wide)
                                1 -> AttitudeScreen(boat, wide)
                                2 -> EnergyScreen(boat, wide)
                                3 -> MapScreen(boat, track, anchor, wide)
                                4 -> AnchorScreen(
                                    boat, anchor,
                                    onDrop = vm::dropAnchor,
                                    onRaise = vm::raiseAnchor,
                                    onRadius = vm::setAnchorRadius,
                                    onMute = vm::muteAlarm,
                                    wide = wide,
                                )
                                else -> DataScreen(snapshot)
                            }
                        }
                    }
                }
            }
            if (showSettings) {
                SettingsDialog(
                    current = settings,
                    onDismiss = { showSettings = false },
                    onSave = { vm.updateSettings(it); showSettings = false },
                )
            }
        }
    }
}

@Composable
private fun StatusDot(state: ConnState, label: String) {
    val inst = LocalInstrumentColors.current
    val color = when (state) {
        ConnState.VERBUNDEN -> inst.starboard
        ConnState.VERBINDE -> inst.warn
        ConnState.FEHLER -> inst.port
        ConnState.GETRENNT -> inst.dim
    }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(end = 4.dp)) {
        Box(Modifier.size(12.dp).background(color, CircleShape))
        Text(
            " $label",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

@Composable
private fun ConnBanner(state: ConnState, message: String) {
    val inst = LocalInstrumentColors.current
    val text = when (state) {
        ConnState.VERBINDE -> "Verbinde mit $message …"
        ConnState.FEHLER -> "Keine Verbindung: $message – neuer Versuch läuft"
        else -> "Getrennt"
    }
    Text(
        text,
        color = Color.Black,
        style = MaterialTheme.typography.labelLarge,
        modifier = Modifier
            .fillMaxWidth()
            .background(if (state == ConnState.FEHLER) inst.port else inst.warn)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    )
}
