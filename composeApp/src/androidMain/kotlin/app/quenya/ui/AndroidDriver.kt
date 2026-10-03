package app.quenya.ui

import android.content.Context
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import app.quenya.db.QuenyaDb

fun androidSqlDriver(context: Context): SqlDriver = AndroidSqliteDriver(QuenyaDb.Schema, context, "quenya.db")
