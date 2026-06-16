package mme.corp.audioshare.network.discovery

import android.util.Log
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

class UdpDiscoveryResponder(
    private val receiverName: String,
    private val audioPort: Int,
    private val getLocalIpAddress: () -> String
) {
    private val running = AtomicBoolean(false)
    private var socket: DatagramSocket? = null
    private var worker: Thread? = null

    fun start() {
        if (!running.compareAndSet(false, true)) {
            return
        }

        worker = thread(name = "udp-discovery-responder-thread") {
            try {
                socket = DatagramSocket(null).apply {
                    reuseAddress = true
                    bind(InetSocketAddress(DiscoveryProtocol.DISCOVERY_PORT))
                }

                val buffer = ByteArray(1024)

                while (running.get()) {
                    val requestPacket = DatagramPacket(buffer, buffer.size)
                    socket?.receive(requestPacket)

                    val payload = String(
                        requestPacket.data,
                        requestPacket.offset,
                        requestPacket.length,
                        Charsets.UTF_8
                    )

                    if (!DiscoveryProtocol.isDiscoveryRequest(payload)) {
                        continue
                    }

                    val localIp = getLocalIpAddress()
                    val response = DiscoveryProtocol.responsePayload(
                        receiverName = receiverName,
                        receiverIp = localIp,
                        audioPort = audioPort
                    )

                    val responsePacket = DatagramPacket(
                        response,
                        response.size,
                        requestPacket.address,
                        requestPacket.port
                    )

                    socket?.send(responsePacket)
                }
            } catch (error: Exception) {
                if (running.get()) {
                    Log.w("UdpDiscoveryResponder", "Discovery responder stopped: ${error.message}")
                }
            } finally {
                running.set(false)
                socket?.close()
                socket = null
            }
        }
    }

    fun stop() {
        running.set(false)
        socket?.close()
        worker?.interrupt()
        worker = null
        socket = null
    }
}
