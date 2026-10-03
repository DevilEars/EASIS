package app.quenya.ui

/** Plays one short clip at a time; a new call replaces whatever is already playing. */
interface AudioPlayer {
    fun play(bytes: ByteArray)
}
