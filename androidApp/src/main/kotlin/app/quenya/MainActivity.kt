package app.quenya

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import app.quenya.ui.AndroidAudioPlayer
import app.quenya.ui.AndroidThemePreference
import app.quenya.ui.App
import app.quenya.ui.androidSqlDriver
import java.util.TimeZone

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val driver = androidSqlDriver(applicationContext)
        val audioPlayer = AndroidAudioPlayer(applicationContext)
        val themePreference = AndroidThemePreference(applicationContext)
        setContent {
            App(driver, { System.currentTimeMillis() }, audioPlayer, themePreference, utcOffsetMs = {
                TimeZone.getDefault().getOffset(System.currentTimeMillis()).toLong()
            }) { enabled, onBack ->
                BackHandler(enabled, onBack)
            }
        }
    }
}
