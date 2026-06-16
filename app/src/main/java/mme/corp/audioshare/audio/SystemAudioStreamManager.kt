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
    private val onCaptureStatus: (String) -> Unit = {},
    private val onError: (Throwable) -> Unit = {}
) {

    private val running = AtomicBoolean(false)
    private var worker: Thread? = null
    private var frameCounter = 0
    private var silentFrameCounter = 0
    private var activeFrameCounter = 0
    private var lastStatus: String? = null

    fun start() {
        if (!running.compareAndSet(false, true)) {
            return
        }

        try {
            capture.start()
            publishStatus("System audio capture started. Play audio in a supported app.")
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
                        processCaptureLevel(buffer, read)
                        sender.send(buffer, read)
                    } else {
                        val message = "System audio capture is running, but no audio frames are being read."
                        Log.w("SystemAudioStreamManager", "$message read=$read")
                        publishStatus(message)
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

    private fun processCaptureLevel(buffer: ByteArray, size: Int) {
        frameCounter++

        val peak = calculatePeakPcm16Le(buffer, size)
        if (peak <= SILENCE_PEAK_THRESHOLD) {
            silentFrameCounter++
        } else {
            activeFrameCounter++
        }

        if (frameCounter % LOG_EVERY_N_FRAMES != 0) {
            return
        }

        Log.i(
            "SystemAudioStreamManager",
            "System audio frames=$frameCounter activeFrames=$activeFrameCounter " +
                "silentFrames=$silentFrameCounter lastPeak=$peak"
        )

        if (activeFrameCounter > 0) {
            publishStatus("System audio is being captured and sent.")
            activeFrameCounter = 0
            silentFrameCounter = 0
            return
        }

        if (silentFrameCounter >= LOG_EVERY_N_FRAMES) {
            val message = "System audio capture is running, but the source is silent. " +
                "The browser, page, media, or protected content may block playback capture."
            Log.w("SystemAudioStreamManager", message)
            publishStatus(message)
            silentFrameCounter = 0
        }
    }

    private fun publishStatus(message: String) {
        if (message == lastStatus) {
            return
        }

        lastStatus = message
        onCaptureStatus(message)
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
