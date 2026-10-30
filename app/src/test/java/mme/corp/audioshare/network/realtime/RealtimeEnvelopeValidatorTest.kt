package mme.corp.audioshare.network.realtime

import org.junit.Assert.*
import org.junit.Test

class RealtimeEnvelopeValidatorTest {
    private val uuid = "09d5ff45-6ffc-46e5-ab79-337970bda234"
    private fun json(type: String, version: String = "1") =
        """{"type":"$type","version":"$version","eventId":"$uuid","timestamp":"2026-10-30T10:00:00Z","payload":{}}"""

    @Test
    fun acceptsBackendReady() {
        assertEquals("SERVER_READY", RealtimeEnvelopeValidator.decode(json("SERVER_READY"))?.type)
    }

    @Test
    fun rejectsUnknownVersion() {
        assertNull(RealtimeEnvelopeValidator.decode(json("SERVER_READY", "2")))
    }

    @Test
    fun rejectsUnknownType() {
        assertNull(RealtimeEnvelopeValidator.decode(json("SEND_MEDIA")))
    }

    @Test
    fun rejectsMalformedJson() {
        assertNull(RealtimeEnvelopeValidator.decode("{broken"))
    }

    @Test
    fun rejectsOversizedMessages() {
        assertNull(RealtimeEnvelopeValidator.decode("x".repeat(16385)))
    }

    @Test
    fun rejectsNonUuidEventId() {
        assertNull(RealtimeEnvelopeValidator.decode(json("SERVER_READY").replace(uuid, "invalid")))
    }
}
