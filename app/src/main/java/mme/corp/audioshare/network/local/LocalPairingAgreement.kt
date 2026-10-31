package mme.corp.audioshare.network.local

import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.PublicKey
import java.security.SecureRandom
import java.security.spec.ECGenParameterSpec
import java.security.spec.X509EncodedKeySpec
import android.util.Base64
import javax.crypto.KeyAgreement

/** Volatile, explicitly-approved two-party ECDH pairing groundwork.
 * The short verification code must be compared out of band (QR + human verification),
 * not accepted from the same untrusted network message. No media flows until approved.
 * Persistent trust, prior-login host grant, QR scanner and transport are not yet implemented.
 */
class LocalPairingAgreement(private val isHost: Boolean) {
    private val keyPair = KeyPairGenerator.getInstance("EC").apply {
        initialize(ECGenParameterSpec("secp256r1"), SecureRandom())
    }.generateKeyPair()
    val publicKeyBase64: String = Base64.encodeToString(keyPair.public.encoded, Base64.NO_WRAP)
    private var approved = false

    data class PairingCandidate(val shortCode: String, val peerFingerprint: String)

    /** Produces a human-comparable short code, no authorization yet. */
    fun inspectPeer(peerKeyBase64: String): PairingCandidate {
        val peer = decode(peerKeyBase64)
        val digest = MessageDigest.getInstance("SHA-256")
        val transcript = transcript(peer.encoded)
        val sha = digest.digest(transcript)
        val sas = ((sha[0].toInt() and 255) shl 16) or
                ((sha[1].toInt() and 255) shl 8) or (sha[2].toInt() and 255)
        val fingerprint = digest.digest(peer.encoded)
            .take(8).joinToString("") { "%02x".format(it.toInt() and 255) }
        return PairingCandidate("%06d".format(sas % 1_000_000), fingerprint)
    }

    /** Called only after the authenticated host explicitly approves the matching displayed code. */
    fun approve(peerKeyBase64: String, verifiedCode: String): SessionKeys {
        check(!approved) { "Pairing agreement already consumed" }
        val candidate = inspectPeer(peerKeyBase64)
        require(
            verifiedCode.matches(Regex("[0-9]{6}")) &&
                    MessageDigest.isEqual(
                        candidate.shortCode.toByteArray(),
                        verifiedCode.toByteArray()
                    )
        ) {
            "Pairing verification failed"
        }
        val peer = decode(peerKeyBase64)
        val agreement = KeyAgreement.getInstance("ECDH")
        agreement.init(keyPair.private)
        agreement.doPhase(peer, true)
        val secret = agreement.generateSecret()
        val salt = MessageDigest.getInstance("SHA-256").digest(transcript(peer.encoded))
        val hostKey =
            LocalMediaCipher.derive(secret, salt, "SvRe|media|host->guest|v1".toByteArray(), 36)
        val guestKey =
            LocalMediaCipher.derive(secret, salt, "SvRe|media|guest->host|v1".toByteArray(), 36)
        secret.fill(0)
        approved = true
        val result = if (isHost) SessionKeys(
            hostKey.copyOfRange(0, 32), hostKey.copyOfRange(32, 36),
            guestKey.copyOfRange(0, 32), guestKey.copyOfRange(32, 36)
        )
        else SessionKeys(
            guestKey.copyOfRange(0, 32), guestKey.copyOfRange(32, 36),
            hostKey.copyOfRange(0, 32), hostKey.copyOfRange(32, 36)
        )
        hostKey.fill(0)
        guestKey.fill(0)
        return result
    }

    private fun decode(b64: String): PublicKey {
        require(b64.length in 80..400)
        val bytes = Base64.decode(b64, Base64.NO_WRAP)
        require(bytes.size in 75..250)
        val peer = KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(bytes))
        require(peer.algorithm == "EC" && !peer.encoded.contentEquals(keyPair.public.encoded))
        return peer
    }

    private fun transcript(peer: ByteArray): ByteArray {
        val mine = keyPair.public.encoded
        val left = if (compareBytes(mine, peer) <= 0) mine else peer
        val right = if (left === mine) peer else mine
        return "SvRe|pairing|v1".toByteArray() + left + right
    }

    private fun compareBytes(a: ByteArray, b: ByteArray): Int {
        for (i in 0 until minOf(a.size, b.size)) {
            val diff = (a[i].toInt() and 255) - (b[i].toInt() and 255)
            if (diff != 0) return diff
        }
        return a.size - b.size
    }

    data class SessionKeys(
        val sendKey: ByteArray, val sendNoncePrefix: ByteArray,
        val receiveKey: ByteArray, val receiveNoncePrefix: ByteArray
    ) {
        fun sender(): LocalMediaCipher =
            LocalMediaCipher.fromDirectionalKey(sendKey, sendNoncePrefix)

        fun receiver(): LocalMediaCipher =
            LocalMediaCipher.fromDirectionalKey(receiveKey, receiveNoncePrefix)

        fun clear() {
            sendKey.fill(0); sendNoncePrefix.fill(0)
            receiveKey.fill(0); receiveNoncePrefix.fill(0)
        }
    }
}
