package app.quenya.ui

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.allocArrayOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import platform.AVFAudio.AVAudioPlayer
import platform.Foundation.NSData
import platform.Foundation.create

class IosAudioPlayer : AudioPlayer {
    private var current: AVAudioPlayer? = null

    // NSData.create (without "NoCopy") copies the bytes into its own storage, so the
    // result stays valid once memScoped ends — unlike usePinned+addressOf, which only
    // references Kotlin-owned memory for the pinned block's lifetime.
    @OptIn(ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)
    override fun play(bytes: ByteArray) {
        val data = memScoped {
            NSData.create(bytes = allocArrayOf(bytes), length = bytes.size.convert())
        }
        current = AVAudioPlayer(data = data, error = null).apply { play() }
    }
}
