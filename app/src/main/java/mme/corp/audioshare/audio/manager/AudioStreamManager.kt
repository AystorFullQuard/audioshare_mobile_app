package mme.corp.audioshare.audio.manager

import android.util.Log
import mme.corp.audioshare.audio.capture.MicrophoneCapture
import mme.corp.audioshare.audio.transport.UdpAudioSender
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

class AudioStreamManager(
    private val mic: MicrophoneCapture,
    private val sender: UdpAudioSender,
    private val frameSize: Int,
    private val onError: (Throwable) -> Unit = {}
) {

    private val running = AtomicBoolean(false)
    private var worker: Thread? = null

    fun start() {
        if (!running.compareAndSet(false, true)) {
            return
        }

        try {
            mic.start()
        } catch (t: Throwable) {
            running.set(false)
            sender.close()
            onError(t)
            return
        }

        val buffer = ByteArray(frameSize)

        worker = thread(name = "audio-stream-thread") {
            try {
                while (running.get()) {
                    val read = mic.read(buffer)

                    if (read > 0) {
                        sender.send(buffer, read)
                    } else {
                        Log.w("AudioStreamManager", "AudioRecord read returned: $read")
                    }
                }
            } catch (t: Throwable) {
                if (running.get()) {
                    onError(t)
                }
            } finally {
                running.set(false)
                mic.stop()
                sender.close()
            }
        }
    }

    fun stop() {
        running.set(false)
        worker?.interrupt()
        mic.stop()
        sender.close()
        worker = null
    }
}