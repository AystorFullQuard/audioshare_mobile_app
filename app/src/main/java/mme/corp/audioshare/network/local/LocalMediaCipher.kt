package mme.corp.audioshare.network.local

import java.nio.ByteBuffer
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/** One-direction media packet protection *after* authenticated QR/PIN pairing and explicit host admission.
 * This class does not provide peer identity or grant authorization by itself. Do not enable UDP from it alone.
 * The caller MUST supply independently derived 32-byte send/receive keys (never reuse key between directions).
 */
class LocalMediaCipher private constructor(
    private val key: ByteArray,
    private val prefix: ByteArray
) {
    private var nextSequence = 0L
    private var highestReceived = -1L
    private var seen = 0L
    private val lock = Any()
    private var destroyed = false

    companion object {
        private const val TAG_BITS = 128
        private const val MAX_FRAME = 4096
        private const val WINDOW = 64
        fun fromDirectionalKey(key: ByteArray, noncePrefix: ByteArray): LocalMediaCipher {
            require(key.size == 32 && noncePrefix.size == 4) { "Invalid media key material" }
            return LocalMediaCipher(key.copyOf(), noncePrefix.copyOf())
        }

        /** HKDF-SHA256, separate keys/nonces must be derived for each endpoint and direction. */
        fun derive(secret: ByteArray, salt: ByteArray, info: ByteArray, size: Int): ByteArray {
            require(secret.size >= 32 && salt.size >= 16 && size in 1..64)
            val hmac = Mac.getInstance("HmacSHA256")
            hmac.init(SecretKeySpec(salt, "HmacSHA256"))
            val prk = hmac.doFinal(secret)
            var previous = byteArrayOf()
            val output = ArrayList<Byte>()
            var counter = 1
            while (output.size < size) {
                hmac.init(SecretKeySpec(prk, "HmacSHA256"))
                previous = hmac.doFinal(previous + info + byteArrayOf(counter.toByte()))
                output.addAll(previous.toList())
                counter++
            }
            prk.fill(0)
            return output.take(size).toByteArray()
        }
    }

    /** Packet: sequence (8 bytes) + AES-GCM ciphertext/tag. Nonce = 4-byte direction prefix + sequence. */
    fun seal(frame: ByteArray, associatedData: ByteArray): ByteArray = synchronized(lock) {
        check(!destroyed) { "Media key erased" }
        require(frame.size in 1..MAX_FRAME && associatedData.size in 1..128)
        check(nextSequence < Long.MAX_VALUE) { "Media sequence exhausted: rekey required" }
        val sequence = nextSequence++
        val nonce = nonce(sequence)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            Cipher.ENCRYPT_MODE,
            SecretKeySpec(key, "AES"),
            GCMParameterSpec(TAG_BITS, nonce)
        )
        cipher.updateAAD(associatedData)
        ByteBuffer.allocate(8).putLong(sequence).array() + cipher.doFinal(frame)
    }

    /** AEAD and replay are checked before delivering any decrypted audio data. */
    fun open(packet: ByteArray, associatedData: ByteArray): ByteArray? = synchronized(lock) {
        if (destroyed || packet.size !in 25..(8 + MAX_FRAME + 16) || associatedData.size !in 1..128) return null
        val seq = ByteBuffer.wrap(packet, 0, 8).long
        if (seq < 0 || (highestReceived >= 0 && (seq > highestReceived && seq - highestReceived < 0))) return null
        if (highestReceived >= 0 && seq <= highestReceived) {
            val distance = highestReceived - seq
            if (distance >= WINDOW || (seen ushr distance.toInt() and 1L) != 0L) return null
        }
        val decoded = runCatching {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(
                Cipher.DECRYPT_MODE,
                SecretKeySpec(key, "AES"),
                GCMParameterSpec(TAG_BITS, nonce(seq))
            )
            cipher.updateAAD(associatedData)
            cipher.doFinal(packet, 8, packet.size - 8)
        }.getOrNull() ?: return null
        if (seq > highestReceived) {
            val shift =
                if (highestReceived < 0) WINDOW else (seq - highestReceived).coerceAtMost(WINDOW.toLong())
                    .toInt()
            seen = (if (shift >= WINDOW) 0L else seen shl shift) or 1L
            highestReceived = seq
        } else seen = seen or (1L shl (highestReceived - seq).toInt())
        decoded
    }

    fun destroy() = synchronized(lock) {
        destroyed = true
        key.fill(0)
        prefix.fill(0)
    }

    private fun nonce(sequence: Long): ByteArray =
        ByteBuffer.allocate(12).put(prefix).putLong(sequence).array()
}
