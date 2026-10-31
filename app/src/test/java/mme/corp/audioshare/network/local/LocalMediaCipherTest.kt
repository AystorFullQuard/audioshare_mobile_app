package mme.corp.audioshare.network.local

import org.junit.Assert.*
import kotlin.experimental.xor
import org.junit.Test

class LocalMediaCipherTest {
    private val key = ByteArray(32) { (it * 7).toByte() }
    private val prefix = byteArrayOf(1, 2, 3, 4)

    @Test
    fun encryptedFrameIsAuthenticatedAndCannotBeReplayed() {
        val sender = LocalMediaCipher.fromDirectionalKey(key, prefix)
        val receiver = LocalMediaCipher.fromDirectionalKey(key, prefix)
        val aad = "room-id/epoch/stream-direction".toByteArray()
        val packet = sender.seal("audio frame".toByteArray(), aad)
        assertEquals("audio frame", String(receiver.open(packet, aad)!!))
        assertNull(receiver.open(packet, aad))
        val packet2 = sender.seal("next".toByteArray(), aad)
        assertNull(receiver.open(packet2, "wrong room".toByteArray()))
        val tampered = packet2.copyOf().also { it[it.lastIndex] = (it.last() xor 1) }
        assertNull(receiver.open(tampered, aad))
        assertEquals("next", String(receiver.open(packet2, aad)!!))
        sender.destroy(); receiver.destroy()
    }

    @Test
    fun deterministicSeparatedHkdfAndBoundedFrames() {
        val secret = ByteArray(32) { it.toByte() }
        val salt = ByteArray(16) { 7 }
        val hostKey = LocalMediaCipher.derive(secret, salt, "host".toByteArray(), 36)
        val guestKey = LocalMediaCipher.derive(secret, salt, "guest".toByteArray(), 36)
        assertEquals(36, hostKey.size)
        assertFalse(hostKey.contentEquals(guestKey))
        val sender = LocalMediaCipher.fromDirectionalKey(key, prefix)
        assertThrows(IllegalArgumentException::class.java) {
            sender.seal(
                ByteArray(4097),
                byteArrayOf(1)
            )
        }
        sender.destroy()
    }
}
