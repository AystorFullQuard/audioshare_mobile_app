package mme.corp.audioshare.audio.service

/** Foreground-service prerequisites are evaluated before an authorized media transport starts.
 * Local unencrypted transport remains disabled independently of these platform checks.
 */
object MediaServicePolicy {
    enum class Mode { MICROPHONE_CALL, SYSTEM_AUDIO_SHARE, RECEIVE_AUDIO }
    data class Requirements(
        val serviceType: String, val needsMicPermission: Boolean,
        val needsProjectionConsent: Boolean, val requiresVisibleStart: Boolean
    )

    fun forMode(mode: Mode, sdk: Int): Requirements = when (mode) {
        Mode.MICROPHONE_CALL -> Requirements("microphone", true, false, sdk >= 34)
        Mode.SYSTEM_AUDIO_SHARE -> Requirements("mediaProjection", false, true, sdk >= 34)
        Mode.RECEIVE_AUDIO -> Requirements("mediaPlayback", false, false, false)
    }

    fun canBegin(
        mode: Mode, sdk: Int, hasMicrophone: Boolean, hasFreshProjectionConsent: Boolean,
        appVisible: Boolean
    ): Boolean {
        val rules = forMode(mode, sdk)
        return (!rules.needsMicPermission || hasMicrophone) &&
                (!rules.needsProjectionConsent || (sdk >= 29 && hasFreshProjectionConsent)) &&
                (!rules.requiresVisibleStart || appVisible)
    }
}
