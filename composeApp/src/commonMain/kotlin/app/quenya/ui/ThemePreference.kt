package app.quenya.ui

/** Manual dark/light override, persisted across launches. null = follow the system setting. */
interface ThemePreference {
    fun get(): Boolean?
    fun set(dark: Boolean?)
}
