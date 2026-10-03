package app.quenya.ui

import android.content.Context
import android.media.MediaPlayer
import java.io.File

/** Writes each clip to a cache file and plays it — MediaPlayer has no simple in-memory
 * byte-array data source, and clips are a few KB each, so the file write is negligible. */
class AndroidAudioPlayer(context: Context) : AudioPlayer {
    private val file = File(context.cacheDir, "reader_clip.m4a")
    private var current: MediaPlayer? = null

    override fun play(bytes: ByteArray) {
        current?.release()
        file.writeBytes(bytes)
        current = MediaPlayer().apply {
            setDataSource(file.absolutePath)
            setOnCompletionListener { it.release() }
            prepare()
            start()
        }
    }
}
