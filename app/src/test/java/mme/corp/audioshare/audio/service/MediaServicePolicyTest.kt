package mme.corp.audioshare.audio.service

import org.junit.Assert.*
import org.junit.Test

class MediaServicePolicyTest {
    @Test
    fun rejectsMissingMicPermissionOrBackgroundStartOnAndroid14() {
        assertFalse(
            MediaServicePolicy.canBegin(
                MediaServicePolicy.Mode.MICROPHONE_CALL,
                34, false, false, true
            )
        )
        assertFalse(
            MediaServicePolicy.canBegin(
                MediaServicePolicy.Mode.MICROPHONE_CALL,
                34, true, false, false
            )
        )
        assertTrue(
            MediaServicePolicy.canBegin(
                MediaServicePolicy.Mode.MICROPHONE_CALL,
                34, true, false, true
            )
        )
    }

    @Test
    fun projectionMustBeNewlyAuthorizedFromVisibleApp() {
        assertFalse(
            MediaServicePolicy.canBegin(
                MediaServicePolicy.Mode.SYSTEM_AUDIO_SHARE,
                36, false, false, true
            )
        )
        assertFalse(
            MediaServicePolicy.canBegin(
                MediaServicePolicy.Mode.SYSTEM_AUDIO_SHARE,
                36, false, true, false
            )
        )
        assertTrue(
            MediaServicePolicy.canBegin(
                MediaServicePolicy.Mode.SYSTEM_AUDIO_SHARE,
                36, false, true, true
            )
        )
    }
}
