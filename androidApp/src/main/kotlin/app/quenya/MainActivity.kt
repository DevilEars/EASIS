package app.quenya

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import app.quenya.ui.App
import app.quenya.ui.androidSqlDriver

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val driver = androidSqlDriver(applicationContext)
        setContent { App(driver) { System.currentTimeMillis() } }
    }
}
