package mme.corp.audioshare.network.local

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LocalPairingAgreementTest {
    @Test
    fun codeApprovalDerivesCompatibleDirectionalKeysAndAuthenticatedFrames() {
        val host = LocalPairingAgreement(isHost = true)
        val guest = LocalPairingAgreement(isHost = false)
        val codeHost = host.inspectPeer(guest.publicKeyBase64).shortCode
        val codeGuest = guest.inspectPeer(host.publicKeyBase64).shortCode
        assertEquals(codeHost, codeGuest)
        try {
            host.approve(guest.publicKeyBase64, "999xyz")
            fail("Unexpectedly accepted unverified code")
        } catch (_: IllegalArgumentException) {
        }
        val hostKeys = host.approve(guest.publicKeyBase64, codeHost)
        val guestKeys = guest.approve(host.publicKeyBase64, codeGuest)
        val aad = "room:stream:epoch".toByteArray()
        val packet = hostKeys.sender().seal("pcm".toByteArray(), aad)
        assertEquals("pcm", String(guestKeys.receiver().open(packet, aad)!!))
        val reply = guestKeys.sender().seal("ack".toByteArray(), aad)
        assertEquals("ack", String(hostKeys.receiver().open(reply, aad)!!))
        hostKeys.clear(); guestKeys.clear()
    }
}
