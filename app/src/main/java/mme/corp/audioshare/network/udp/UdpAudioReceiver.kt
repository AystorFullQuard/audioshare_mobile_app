package mme.corp.audioshare.network.udp

import mme.corp.audioshare.audio.playback.AudioPlayer
import java.net.DatagramPacket
import java.net.DatagramSocket
import kotlin.concurrent.thread

class UdpAudioReceiver(
    private val port: Int,
    private val audioPlayer: AudioPlayer,
    private val onPacketReceived: (Int) -> Unit = {}
) {
    private var running = false
    private var socket: DatagramSocket? = null

    fun start() {
        running = true
        audioPlayer.start()

        thread(name = "udp-audio-receiver-thread") {
            socket = DatagramSocket(port)
            val receiveBuffer = ByteArray(4096)

            while (running) {
                try {
                    val packet = DatagramPacket(receiveBuffer, receiveBuffer.size)
                    socket?.receive(packet)

                    onPacketReceived(packet.length)

                    val headerSize = 12
                    val audioSize = packet.length - headerSize

                    if (audioSize > 0) {
                        val audioData = ByteArray(audioSize)
                        System.arraycopy(packet.data, headerSize, audioData, 0, audioSize)
                        audioPlayer.write(audioData, audioSize)
                    }
                } catch (_: Exception) {
                    break
                }
            }
        }
    }

    fun stop() {
        running = false
        socket?.close()
        audioPlayer.stop()
        audioPlayer.release()
    }
}