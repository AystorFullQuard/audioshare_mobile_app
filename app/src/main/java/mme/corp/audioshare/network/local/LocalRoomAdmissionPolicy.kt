package mme.corp.audioshare.network.local

import java.security.MessageDigest
import java.util.LinkedHashMap

/** Offline authorization is independent of online room ACLs and JWT validity.
 * The caller must supply a device-bound, unexpired grant verified by GAP-15. A previously
 * successful login *alone* is not a grant. Default implementation denies all hosting.
 */
fun interface OfflineHostEntitlementVerifier {
    fun isAllowed(accountId: String, nowMillis: Long): Boolean

    companion object {
        val DENY_ALL = OfflineHostEntitlementVerifier { _, _ -> false }
    }
}

/** Bounded, in-memory authority for one local audio-share room.
 * Trusting a fingerprint only avoids repeating identity enrollment; it never skips
 * explicit host approval for entry. No cloud ACL is inferred from this state.
 * Call [close] on logout, account switch, room teardown or grant expiry.
 */
class LocalRoomAdmissionPolicy(
    private val entitlement: OfflineHostEntitlementVerifier = OfflineHostEntitlementVerifier.DENY_ALL,
    private val nowMillis: () -> Long = System::currentTimeMillis
) {
    data class TrustedPeer(
        val fingerprint: String,
        val accountId: String,
        val expiresAtMillis: Long
    )

    data class GuestRequest(
        val fingerprint: String,
        val verificationCode: String,
        val roomId: String
    )

    private var host: String? = null
    private var room: String? = null
    private val pending = LinkedHashMap<String, GuestRequest>()
    private val admitted = LinkedHashMap<String, GuestRequest>()
    private val trusted = LinkedHashMap<String, TrustedPeer>()

    /** Explicit start from an offline entitlement issued before disconnection. */
    @Synchronized
    fun open(accountId: String, roomId: String): Boolean {
        close()
        if (!validId(accountId) || !validId(roomId) || !entitlement.isAllowed(
                accountId,
                nowMillis()
            )
        ) return false
        host = accountId
        room = roomId
        return true
    }

    @Synchronized
    fun request(fingerprint: String, code: String, roomId: String): Boolean {
        if (!hostAllowed() || room != roomId || !validFingerprint(fingerprint) || !code.matches(
                Regex("[0-9]{6}")
            )
        ) return false
        if (pending.size >= MAX_PENDING || pending.containsKey(fingerprint) || admitted.containsKey(
                fingerprint
            )
        ) return false
        pending[fingerprint] = GuestRequest(fingerprint, code, roomId)
        return true
    }

    /** Host confirms the code out-of-band after QR/manual comparison.
     * A client/network message must never call this automatically as its own approval.
     */
    @Synchronized
    fun approve(fingerprint: String, comparedCode: String): Boolean {
        if (!hostAllowed() || admitted.size >= MAX_LISTENERS) return false
        val request = pending[fingerprint] ?: return false
        if (!MessageDigest.isEqual(
                request.verificationCode.toByteArray(),
                comparedCode.toByteArray()
            )
        ) return false
        pending.remove(fingerprint)
        admitted[fingerprint] = request
        return true
    }

    @Synchronized
    fun remember(fingerprint: String, expiresAtMillis: Long): Boolean {
        val account = host ?: return false
        if (!hostAllowed() || !admitted.containsKey(fingerprint)) return false
        if (expiresAtMillis <= nowMillis() || expiresAtMillis - nowMillis() > MAX_TRUST_MILLIS) return false
        if (trusted.size >= MAX_TRUSTED && !trusted.containsKey(fingerprint)) return false
        trusted[fingerprint] = TrustedPeer(fingerprint, account, expiresAtMillis)
        return true
    }

    @Synchronized
    fun isRemembered(fingerprint: String): Boolean {
        val record = trusted[fingerprint] ?: return false
        return hostAllowed() && record.accountId == host && record.expiresAtMillis > nowMillis()
    }

    @Synchronized
    fun isAdmitted(fingerprint: String): Boolean =
        hostAllowed() && admitted.containsKey(fingerprint)

    @Synchronized
    fun revoke(fingerprint: String) {
        pending.remove(fingerprint)
        admitted.remove(fingerprint)
        trusted.remove(fingerprint)
    }

    @Synchronized
    fun close() {
        pending.clear()
        admitted.clear()
        trusted.clear()
        host = null
        room = null
    }

    @Synchronized
    fun acceptedCount(): Int = if (hostAllowed()) admitted.size else 0

    private fun hostAllowed(): Boolean {
        val account = host ?: return false
        if (!entitlement.isAllowed(account, nowMillis())) {
            // Avoid mutating collections while their iterators are in use.
            pending.clear(); admitted.clear(); trusted.clear()
            host = null; room = null
            return false
        }
        return true
    }

    private fun validId(value: String) = value.isNotBlank() && value.length <= 128
    private fun validFingerprint(value: String) = value.length == 64 &&
            value.all { it in '0'..'9' || it in 'a'..'f' }

    companion object {
        const val MAX_LISTENERS = 2
        private const val MAX_PENDING = 8
        private const val MAX_TRUSTED = 64
        private const val MAX_TRUST_MILLIS = 30L * 24 * 60 * 60 * 1000
    }
}
