package mme.corp.audioshare.network.local

import android.content.Context
import mme.corp.audioshare.data.storage.AndroidTokenCipher
import org.json.JSONArray
import org.json.JSONObject

/** Encrypted, opt-in peer-identity memory. Never grants room admission by itself.
 * Uses a separate AndroidKeyStore alias from session credentials, without backup.
 * Call from a background dispatcher; no token, code or private key is stored.
 */
class AndroidLocalTrustStore(context: Context, preferencesName: String = DEFAULT_PREFERENCES) {
    private val preferences = context.applicationContext.getSharedPreferences(
        preferencesName, Context.MODE_PRIVATE
    )
    private val cipher = AndroidTokenCipher("svremobile.local.trust.v1")

    data class Record(val accountId: String, val fingerprint: String, val expiresAtMillis: Long)

    /** The caller must already have verified this peer's full SHA-256 public-key fingerprint
     * by an authenticated pairing handshake and explicit user consent.
     */
    @Synchronized
    fun save(record: Record, nowMillis: Long): Boolean {
        if (!valid(record, nowMillis)) return false
        val existing =
            readAll(nowMillis).filterNot { it.accountId == record.accountId && it.fingerprint == record.fingerprint }
        if (existing.size >= MAX_RECORDS) return false
        return persist(existing + record)
    }

    @Synchronized
    fun lookup(accountId: String, fingerprint: String, nowMillis: Long): Boolean =
        validId(accountId) && validFingerprint(fingerprint) &&
                readAll(nowMillis).any { it.accountId == accountId && it.fingerprint == fingerprint }

    @Synchronized
    fun revoke(accountId: String, fingerprint: String, nowMillis: Long): Boolean =
        persist(readAll(nowMillis).filterNot { it.accountId == accountId && it.fingerprint == fingerprint })

    /** Required on logout, account switch, key revocation or local-data reset. */
    @Synchronized
    fun clear(): Boolean = preferences.edit().remove(STORE_KEY).commit()

    @Synchronized
    fun list(accountId: String, nowMillis: Long): List<Record> =
        if (validId(accountId)) readAll(nowMillis).filter { it.accountId == accountId } else emptyList()

    private fun readAll(nowMillis: Long): List<Record> {
        val envelope = preferences.getString(STORE_KEY, null) ?: return emptyList()
        val decoded =
            cipher.open(envelope) ?: return emptyList() // corrupt or invalidated key => deny all
        return runCatching {
            val arr = JSONArray(decoded)
            if (arr.length() > MAX_RECORDS) return@runCatching emptyList()
            (0 until arr.length()).mapNotNull { index ->
                val obj = arr.getJSONObject(index)
                Record(obj.getString("account"), obj.getString("key"), obj.getLong("until"))
                    .takeIf { valid(it, nowMillis) }
            }
        }.getOrDefault(emptyList())
    }

    private fun persist(records: List<Record>): Boolean {
        if (records.size > MAX_RECORDS) return false
        val data = JSONArray()
        records.forEach { entry ->
            data.put(
                JSONObject().put("account", entry.accountId)
                    .put("key", entry.fingerprint).put("until", entry.expiresAtMillis)
            )
        }
        return preferences.edit().putString(STORE_KEY, cipher.seal(data.toString())).commit()
    }

    private fun valid(record: Record, nowMillis: Long): Boolean =
        validId(record.accountId) && validFingerprint(record.fingerprint) &&
                record.expiresAtMillis > nowMillis && record.expiresAtMillis - nowMillis <= MAX_TRUST_MILLIS

    private fun validId(value: String) = value.isNotBlank() && value.length <= 128
    private fun validFingerprint(value: String) = value.length == 64 &&
            value.all { it in '0'..'9' || it in 'a'..'f' }

    companion object {
        private const val DEFAULT_PREFERENCES = "svre.local.trusted-peers.v1"
        private const val STORE_KEY = "encrypted-identity-list"
        private const val MAX_RECORDS = 64
        private const val MAX_TRUST_MILLIS = 30L * 24 * 60 * 60 * 1000
    }
}
