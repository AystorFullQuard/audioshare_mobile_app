package mme.corp.audioshare.network.local

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class AndroidLocalTrustStoreTest {
    private val name = "svre-trust-test-${UUID.randomUUID()}"
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val store by lazy { AndroidLocalTrustStore(context, name) }
    private val fingerprint = "c".repeat(64)

    @Before
    fun setUp() {
        store.clear()
    }

    @After
    fun tearDown() {
        store.clear()
        context.deleteSharedPreferences(name)
    }

    @Test
    fun storedFingerprintIsEncryptedAndNeverGrantsAdmission() {
        val now = 1000L
        assertTrue(
            store.save(
                AndroidLocalTrustStore.Record("owner", fingerprint, now + 30_000L),
                now
            )
        )
        assertTrue(store.lookup("owner", fingerprint, now))
        assertFalse(store.lookup("different-owner", fingerprint, now))
        val disk = context.getSharedPreferences(name, 0).all.values.joinToString()
        assertFalse(disk.contains(fingerprint))
        assertFalse(disk.contains("owner"))
        assertFalse(store.lookup("owner", fingerprint, now + 31_000L))
        assertTrue(store.revoke("owner", fingerprint, now))
        assertFalse(store.lookup("owner", fingerprint, now))
    }
}
