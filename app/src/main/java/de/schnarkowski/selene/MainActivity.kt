package de.schnarkowski.selene

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import de.schnarkowski.selene.ui.SeleneApp
import org.osmdroid.config.Configuration

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // osmdroid braucht einen User-Agent und einen Cache-Ort
        Configuration.getInstance().apply {
            load(applicationContext, getSharedPreferences("osmdroid", MODE_PRIVATE))
            // OSM verlangt einen eindeutigen User-Agent; ein reiner Paketname gilt als
            // "generisch" und wird blockiert (leere Kacheln).
            userAgentValue = "SeleneDataApp/0.1 (Android; +https://github.com/syntacrsc/selene-data-app)"
        }
        setContent { SeleneApp(vm) }
    }
}
