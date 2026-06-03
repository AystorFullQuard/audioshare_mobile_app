package mme.corp.audioshare.util

import java.net.NetworkInterface

object NetworkUtils {

    fun getLocalIpAddress(): String {
        return try {
            val interfaces = NetworkInterface.getNetworkInterfaces()

            for (networkInterface in interfaces) {
                val addresses = networkInterface.inetAddresses

                for (address in addresses) {
                    val ip = address.hostAddress ?: continue

                    if (
                        !address.isLoopbackAddress &&
                        ip.indexOf(':') < 0 &&
                        (
                                ip.startsWith("192.168.") ||
                                        ip.startsWith("10.") ||
                                        ip.startsWith("172.")
                                )
                    ) {
                        return ip
                    }
                }
            }

            "IP not found"
        } catch (e: Exception) {
            "IP error: ${e.message}"
        }
    }
}