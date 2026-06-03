package mme.corp.audioshare.network.udp

import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicInteger

class UdpAudioSender(
    private val hostIp: String,
    private val port: Int,
    private val onPacketSent: (Int) -> Unit = {}
) {
    private val socket = DatagramSocket()
    private val address = InetAddress.getByName(hostIp)
    private val seq = AtomicInteger(0)
    private val sendBuffer = ByteArray(4096)

    fun send(pcm: ByteArray, size: Int) {
        val sequence = seq.incrementAndGet()
        val timestamp = System.nanoTime()

        val buffer = ByteBuffer.wrap(sendBuffer)
        buffer.order(ByteOrder.BIG_ENDIAN)
        buffer.clear()

        buffer.putInt(sequence)
        buffer.putLong(timestamp)
        buffer.put(pcm, 0, size)

        val packetSize = 4 + 8 + size

        val packet = DatagramPacket(
            sendBuffer,
            packetSize,
            address,
            port
        )

        socket.send(packet)
        onPacketSent(packetSize)
    }

    fun close() {
        socket.close()
    }
}