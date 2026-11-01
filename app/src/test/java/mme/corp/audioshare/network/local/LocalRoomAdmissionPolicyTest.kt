package mme.corp.audioshare.network.local

import org.junit.Assert.*
import org.junit.Test

class LocalRoomAdmissionPolicyTest {
    private val fingerprintA = "a".repeat(64)
    private val fingerprintB = "b".repeat(64)
    private val fingerprintC = "c".repeat(64)

    @Test
    fun defaultDenyWithoutOfflineGrant() {
        val policy = LocalRoomAdmissionPolicy()
        assertFalse(policy.open("account", "room"))
        assertFalse(policy.request(fingerprintA, "123456", "room"))
        assertEquals(0, policy.acceptedCount())
    }

    @Test
    fun hostMustApproveEachGuestWithMatchingCode() {
        val policy = LocalRoomAdmissionPolicy(OfflineHostEntitlementVerifier { _, _ -> true })
        assertTrue(policy.open("account", "room"))
        assertFalse(policy.request(fingerprintA, "123456", "different-room"))
        assertFalse(policy.request("unsafe", "123456", "room"))
        assertTrue(policy.request(fingerprintA, "123456", "room"))
        assertFalse(policy.isAdmitted(fingerprintA))
        assertFalse(policy.approve(fingerprintA, "654321"))
        assertTrue(policy.approve(fingerprintA, "123456"))
        assertTrue(policy.isAdmitted(fingerprintA))
        assertTrue(policy.request(fingerprintB, "777777", "room"))
        assertTrue(policy.approve(fingerprintB, "777777"))
        assertTrue(policy.request(fingerprintC, "111111", "room"))
        assertFalse(policy.approve(fingerprintC, "111111")) // one host + two listeners
    }

    @Test
    fun rememberedIdentityNeverAutoApprovesEntry() {
        var now = 1_000L
        val policy =
            LocalRoomAdmissionPolicy(OfflineHostEntitlementVerifier { _, _ -> true }) { now }
        assertTrue(policy.open("account", "room"))
        assertTrue(policy.request(fingerprintA, "123456", "room"))
        assertTrue(policy.approve(fingerprintA, "123456"))
        assertTrue(policy.remember(fingerprintA, now + 1000))
        assertTrue(policy.isRemembered(fingerprintA))
        policy.revoke(fingerprintA)
        assertFalse(policy.isRemembered(fingerprintA))
        assertFalse(policy.isAdmitted(fingerprintA))
        now += 2000
        policy.close()
        assertFalse(policy.isAdmitted(fingerprintA))
    }

    @Test
    fun grantExpiryInvalidatesAdmission() {
        var now = 1_000L
        val policy =
            LocalRoomAdmissionPolicy(OfflineHostEntitlementVerifier { _, time -> time < 2_000 }) { now }
        assertTrue(policy.open("account", "room"))
        assertTrue(policy.request(fingerprintA, "123456", "room"))
        assertTrue(policy.approve(fingerprintA, "123456"))
        now = 2_100
        assertFalse(policy.isAdmitted(fingerprintA))
        assertEquals(0, policy.acceptedCount())
    }
}
