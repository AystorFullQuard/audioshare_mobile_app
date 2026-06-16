package mme.corp.audioshare.network.discovery

data class DiscoveredReceiver(
    val name: String,
    val ip: String,
    val port: Int
) {
    val displayName: String
        get() = "$name ($ip:$port)"
}
