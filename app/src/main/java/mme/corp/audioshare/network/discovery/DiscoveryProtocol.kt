package mme.corp.audioshare.network.discovery

object DiscoveryProtocol {
    const val DISCOVERY_PORT = 50006

    private const val REQUEST_MESSAGE = "AUDIOSHARE_DISCOVER_V1"
    private const val RESPONSE_MESSAGE = "AUDIOSHARE_RECEIVER_V1"
    private const val DELIMITER = ";"

    fun requestPayload(): ByteArray = REQUEST_MESSAGE.toByteArray(Charsets.UTF_8)

    fun isDiscoveryRequest(payload: String): Boolean {
        return payload.trim() == REQUEST_MESSAGE
    }

    fun responsePayload(receiverName: String, receiverIp: String, audioPort: Int): ByteArray {
        val safeName = receiverName.replace(DELIMITER, " ").trim().ifBlank {
            "AudioShare Receiver"
        }

        val message = listOf(
            RESPONSE_MESSAGE,
            safeName,
            receiverIp,
            audioPort.toString()
        ).joinToString(DELIMITER)

        return message.toByteArray(Charsets.UTF_8)
    }

    fun parseReceiverResponse(payload: String): DiscoveredReceiver? {
        val parts = payload.trim().split(DELIMITER)
        if (parts.size != 4 || parts[0] != RESPONSE_MESSAGE) {
            return null
        }

        val port = parts[3].toIntOrNull() ?: return null
        if (parts[2].isBlank() || port <= 0) {
            return null
        }

        return DiscoveredReceiver(
            name = parts[1].ifBlank { "AudioShare Receiver" },
            ip = parts[2],
            port = port
        )
    }
}
