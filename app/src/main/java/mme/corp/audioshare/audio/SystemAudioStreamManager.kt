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
    private var frameCounter = 0
    private var silentFrameCounter = 0

    fun start() {
        if (!running.compareAndSet(false, true)) {
            return
        }

        try {
            capture.start()
        } catch (error: Throwable) {
            running.set(false)
            sender.close()
            onError(error)
            return
        }

        val buffer = ByteArray(frameSize)

        worker = thread(name = "system-audio-stream-thread") {
            try {
                while (running.get()) {
                    val read = capture.read(buffer)

                    if (read > 0) {
                        logCaptureLevel(buffer, read)
                        sender.send(buffer, read)
                    } else {
                        Log.w(
                            "SystemAudioStreamManager",
                            "System audio capture started, but no audio frames were read: $read"
                        )
                    }
                }
            } catch (error: Throwable) {
                if (running.get()) {
                    onError(error)
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

    private fun logCaptureLevel(buffer: ByteArray, size: Int) {
        frameCounter++

        val peak = calculatePeakPcm16Le(buffer, size)
        if (peak <= SILENCE_PEAK_THRESHOLD) {
            silentFrameCounter++
        }

        if (frameCounter % LOG_EVERY_N_FRAMES == 0) {
            Log.i(
                "SystemAudioStreamManager",
                "System audio frames=$frameCounter silentFrames=$silentFrameCounter lastPeak=$peak"
            )

            if (silentFrameCounter >= LOG_EVERY_N_FRAMES) {
                Log.w(
                    "SystemAudioStreamManager",
                    "System audio capture is running but frames are silent. The source app/browser/content may block playback capture."
                )
                silentFrameCounter = 0
            }
        }
    }

    private fun calculatePeakPcm16Le(buffer: ByteArray, size: Int): Int {
        var peak = 0
        var index = 0

        while (index + 1 < size) {
            val low = buffer[index].toInt() and 0xFF
            val high = buffer[index + 1].toInt()
            val sample = ((high shl 8) or low).toShort().toInt()
            val absolute = kotlin.math.abs(sample)

            if (absolute > peak) {
                peak = absolute
            }

            index += 2
        }

        return peak
    }

    private companion object {
        const val SILENCE_PEAK_THRESHOLD = 32
        const val LOG_EVERY_N_FRAMES = 100
    }
}
