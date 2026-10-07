package app.quenya.ui

import androidx.compose.ui.window.ComposeUIViewController
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.native.NativeSqliteDriver
import app.quenya.db.QuenyaDb
import platform.Foundation.NSDate
import platform.Foundation.NSTimeZone
import platform.Foundation.localTimeZone
import platform.Foundation.secondsFromGMT
import platform.Foundation.timeIntervalSince1970

fun iosSqlDriver(): SqlDriver = NativeSqliteDriver(QuenyaDb.Schema, "quenya.db")

/** Call this from the Xcode wrapper project (see README: iOS). */
fun MainViewController() = ComposeUIViewController {
    App(
        iosSqlDriver(),
        { (NSDate().timeIntervalSince1970 * 1000).toLong() },
        IosAudioPlayer(),
        IosThemePreference(),
        utcOffsetMs = { NSTimeZone.localTimeZone.secondsFromGMT * 1000 },
    )
}
