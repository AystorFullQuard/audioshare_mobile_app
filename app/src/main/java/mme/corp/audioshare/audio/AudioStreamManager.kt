package mme.corp.audioshare.audio

import mme.corp.audioshare.audio.capture.MicrophoneCapture
import mme.corp.audioshare.network.udp.UdpAudioSender
import kotlin.concurrent.thread

class AudioStreamManager(
    private val mic: MicrophoneCapture,
    private val sender: UdpAudioSender,
    private val frameSize: Int
) {

    private var running = false

    fun start() {
        running = true
        mic.start()

        val buffer = ByteArray(frameSize)

        thread(name = "audio-stream-thread") {

            while (running) {
                val read = mic.audioRecord.read(buffer, 0, frameSize)

                if (read == frameSize) {
                    sender.send(buffer, read)
                }

                Thread.sleep(10)
            }
        }
    }

    fun stop() {
        running = false
        mic.stop()
        sender.close()
    }
}