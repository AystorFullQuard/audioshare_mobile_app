package mme.corp.audioshare.network.discovery

import android.util.Log
import mme.corp.audioshare.util.NetworkUtils
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

class UdpDiscoveryClient(
    private val onReceiversFound: (List<DiscoveredReceiver>) -> Unit,
    private val onError: (Throwable) -> Unit = {}
) {
    private val running = AtomicBoolean(false)
    private var socket: DatagramSocket? = null
    private var worker: Thread? = null

    fun findReceivers(timeoutMs: Long = 1_500L) {
        if (!running.compareAndSet(false, true)) {
            return
        }

        worker = thread(name = "udp-discovery-client-thread") {
            val foundReceivers = linkedMapOf<String, DiscoveredReceiver>()

            try {
                socket = DatagramSocket().apply {
                    broadcast = true
                    soTimeout = 250
                }

                sendDiscoveryRequests()

                val deadline = System.currentTimeMillis() + timeoutMs
                val buffer = ByteArray(1024)

                while (running.get() && System.currentTimeMillis() < deadline) {
                    try {
                        val responsePacket = DatagramPacket(buffer, buffer.size)
                        socket?.receive(responsePacket)

                        val payload = String(
                            responsePacket.data,
                            responsePacket.offset,
                            responsePacket.length,
                            Charsets.UTF_8
                        )

                        val receiver = DiscoveryProtocol.parseReceiverResponse(payload)
                        if (receiver != null) {
                            foundReceivers[receiver.ip] = receiver
                        }
                    } catch (_: java.net.SocketTimeoutException) {
                        // Continue until the discovery deadline expires.
                    }
                }

                onReceiversFound(foundReceivers.values.toList())
            } catch (error: Exception) {
                Log.w("UdpDiscoveryClient", "Receiver discovery failed: ${error.message}")
                onError(error)
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

    private fun sendDiscoveryRequests() {
        val request = DiscoveryProtocol.requestPayload()

        for (address in getBroadcastAddresses()) {
            val packet = DatagramPacket(
                request,
                request.size,
                address,
                DiscoveryProtocol.DISCOVERY_PORT
            )
            socket?.send(packet)
        }
    }

    private fun getBroadcastAddresses(): List<InetAddress> {
        val addresses = linkedSetOf<String>()
        addresses.add("255.255.255.255")

        val localIp = NetworkUtils.getLocalIpAddress()
        val lastDotIndex = localIp.lastIndexOf('.')
        if (lastDotIndex > 0) {
            addresses.add(localIp.substring(0, lastDotIndex + 1) + "255")
        }

        return addresses.mapNotNull { address ->
            runCatching { InetAddress.getByName(address) }.getOrNull()
        }
    }
}
