package mme.corp.audioshare.audio

import mme.corp.audioshare.network.udp.UdpAudioSender
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

class TestToneStreamManager(
    private val sender: UdpAudioSender,
    private val frameSize: Int,
    private val onError: (Throwable) -> Unit = {}
) {
    private val running = AtomicBoolean(false)
    private var worker: Thread? = null
    private val tone = TestToneGenerator()

    fun start() {
        if (!running.compareAndSet(false, true)) return

        worker = thread(name = "test-tone-stream-thread") {
            try {
                while (running.get()) {
                    val frame = tone.nextFrame(frameSize)
                    sender.send(frame, frame.size)

                    // frameSize = 960 bytes = 10 ms at 48kHz mono PCM16
                    Thread.sleep(10)
                }
            } catch (t: Throwable) {
                if (running.get()) onError(t)
            } finally {
                running.set(false)
                sender.close()
            }
        }
    }

    fun stop() {
        running.set(false)
        worker?.interrupt()
        sender.close()
        worker = null
    }
}