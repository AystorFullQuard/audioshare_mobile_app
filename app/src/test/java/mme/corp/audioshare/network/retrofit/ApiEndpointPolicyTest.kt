package mme.corp.audioshare.network.retrofit

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ApiEndpointPolicyTest {
    @Test fun productionRequiresHttps() {
        assertEquals(
            "https://relay.example.org/",
            ApiEndpointPolicy.approvedBaseUrl("https://relay.example.org/", false)
        )
        assertThrows(IllegalArgumentException::class.java) {
            ApiEndpointPolicy.approvedBaseUrl("http://192.168.1.5:8090/", false)
        }
    }

    @Test fun debugAllowsLocalDockerAndEmulatorButNotPublicHttp() {
        for (host in listOf("10.0.2.2", "192.168.100.4", "172.19.0.1", "localhost")) {
            assertEquals(
                "http://$host:8090/",
                ApiEndpointPolicy.approvedBaseUrl("http://$host:8090/", true)
            )
        }
        for (url in listOf("http://example.org/", "http://8.8.8.8/", "http://172.32.0.1/")) {
            assertThrows(IllegalArgumentException::class.java) {
                ApiEndpointPolicy.approvedBaseUrl(url, true)
            }
        }
    }

    @Test fun rejectsConfigurationLeaksAndInvalidUrls() {
        for (url in listOf(
            "", "https://relay.example.org/path", "https://relay.example.org/?q=1",
            "https://relay.example.org/#secret", "https://user:pass@relay.example.org/",
            "javascript:alert(1)", "http://192.168.0.999/", "http://10.0.2.2:0/"
        )) {
            assertThrows("URL must be rejected: $url", IllegalArgumentException::class.java) {
                ApiEndpointPolicy.approvedBaseUrl(url, true)
            }
        }
    }

    @Test fun acceptsTlsAlternatePortAndIpv6Loopback() {
        assertEquals(
            "https://relay.example.org:8443/",
            ApiEndpointPolicy.approvedBaseUrl("HTTPS://relay.example.org:8443/", false)
        )
        assertEquals(
            "http://[::1]:8090/",
            ApiEndpointPolicy.approvedBaseUrl("http://[::1]:8090/", true)
        )
    }

    @Test fun deniesUnsafeHttpTargetsAndDeceptiveOrigins() {
        for (url in listOf(
            "http://relay.example.org/", "http://10.0.2.2.evil.example/",
            "http://192.169.0.1/", "http://172.15.0.2/",
            "http://172.32.0.2/", "http://[2001:4860:4860::8888]/",
            "http://user@10.0.2.2:8090/", "http://10.0.2.2:8090/api/v1/",
            "http://10.0.2.2:8090/?secret=1", "http://10.0.2.2:65536/"
        )) {
            assertThrows("Unsafe endpoint accepted: $url", IllegalArgumentException::class.java) {
                ApiEndpointPolicy.approvedBaseUrl(url, true)
            }
        }
    }

}
