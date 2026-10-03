package app.quenya.ui

import platform.Foundation.NSUserDefaults

class IosThemePreference : ThemePreference {
    private val defaults = NSUserDefaults.standardUserDefaults

    override fun get(): Boolean? =
        if (defaults.objectForKey(KEY) == null) null else defaults.boolForKey(KEY)

    override fun set(dark: Boolean?) {
        if (dark == null) defaults.removeObjectForKey(KEY) else defaults.setBool(dark, KEY)
    }

    private companion object { const val KEY = "easis_theme_dark" }
}
