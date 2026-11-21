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
            userAgentValue = packageName
        }
        setContent { SeleneApp(vm) }
    }
}
