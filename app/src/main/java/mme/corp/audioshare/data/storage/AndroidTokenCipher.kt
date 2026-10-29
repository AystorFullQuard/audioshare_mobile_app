package mme.corp.audioshare.data.storage

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * AES/GCM envelope protected by a non-exportable AndroidKeyStore key (API 23+).
 * IVs are randomized by the platform for each encryption. Neither token nor key is logged.
 * This protects tokens at rest; an unlocked/compromised device is not a security boundary.
 */
internal class AndroidTokenCipher {
    @Volatile
    private var cachedKey: SecretKey? = null
    fun seal(plain: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val bytes = cipher.doFinal(plain.toByteArray(StandardCharsets.UTF_8))
        return PREFIX + Base64.encodeToString(cipher.iv + bytes, Base64.NO_WRAP)
    }

    fun open(stored: String?): String? {
        if (stored == null || !stored.startsWith(PREFIX)) return null
        return runCatching {
            val bytes = Base64.decode(stored.removePrefix(PREFIX), Base64.NO_WRAP)
            require(bytes.size > IV_BYTES + 16 && bytes.size <= MAX_ENVELOPE_BYTES)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(
                Cipher.DECRYPT_MODE,
                secretKey(),
                GCMParameterSpec(128, bytes, 0, IV_BYTES)
            )
            String(
                cipher.doFinal(bytes, IV_BYTES, bytes.size - IV_BYTES),
                StandardCharsets.UTF_8
            )
        }.getOrNull() // Invalidated key or tampered ciphertext => require reauthentication.
    }

    fun isEnvelope(value: String): Boolean = value.startsWith(PREFIX)

    @Synchronized
    private fun secretKey(): SecretKey {
        cachedKey?.let { return it }
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(KEY_ALIAS, null) as? SecretKey)?.let { cachedKey = it; return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .setKeySize(256)
                .build()
        )
        return generator.generateKey().also { cachedKey = it }
    }

    private companion object {
        const val KEY_ALIAS = "svremobile.auth.tokens.v1"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val PREFIX = "enc:v1:"
        const val IV_BYTES = 12
        const val MAX_ENVELOPE_BYTES = 32768
    }
}
