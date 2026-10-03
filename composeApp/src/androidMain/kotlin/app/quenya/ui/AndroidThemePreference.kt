package app.quenya.ui

import android.content.Context

class AndroidThemePreference(context: Context) : ThemePreference {
    private val prefs = context.getSharedPreferences("easis_theme", Context.MODE_PRIVATE)

    override fun get(): Boolean? = if (prefs.contains(KEY)) prefs.getBoolean(KEY, false) else null

    override fun set(dark: Boolean?) {
        val editor = prefs.edit()
        if (dark == null) editor.remove(KEY) else editor.putBoolean(KEY, dark)
        editor.apply()
    }

    private companion object { const val KEY = "dark" }
}
