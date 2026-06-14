package mme.corp.audioshare.audio

import android.util.Log
import mme.corp.audioshare.audio.capture.SystemAudioCapture
import mme.corp.audioshare.network.udp.UdpAudioSender
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

class SystemAudioStreamManager(
    private val capture: SystemAudioCapture,
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
            capture.start()
        } catch (t: Throwable) {
            running.set(false)
            sender.close()
            onError(t)
            return
        }

        val buffer = ByteArray(frameSize)

        worker = thread(name = "system-audio-stream-thread") {
            try {
                while (running.get()) {
                    val read = capture.read(buffer)

                    if (read > 0) {
                        sender.send(buffer, read)
                    } else {
                        Log.w(
                            "SystemAudioStreamManager",
                            "AudioRecord read returned: $read"
                        )
                    }
                }
            } catch (t: Throwable) {
                if (running.get()) {
                    onError(t)
                }
            } finally {
                running.set(false)
                capture.stop()
                sender.close()
            }
        }
    }

    fun stop() {
        running.set(false)
        worker?.interrupt()
        capture.stop()
        sender.close()
        worker = null
    }
}