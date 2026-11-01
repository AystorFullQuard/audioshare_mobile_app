package mme.corp.audioshare.ui.screens.home

import androidx.compose.ui.graphics.Color
import mme.corp.audioshare.data.dto.UserSettingsDto

/** Future user theme editor may provide validated local overrides. */
internal data class HomeThemeOverrides(
    val backgroundHex: String? = null,
    val secondaryHex: String? = null,
    val style: String? = null
)

/** Home uses the same neutral ramp as the rest of SvRe; accent only marks interaction. */
internal data class HomeAppearance(
    val background: Color,
    val panel: Color,
    val border: Color,
    val text: Color,
    val muted: Color,
    val accent: Color,
    val onAccent: Color,
    val raised: Color,
    val selected: Color,
    val wave: Color,
    val waveSecondary: Color,
    val success: Color
) {
    companion object {
        val DARK = HomeAppearance(
            background = Color(0xFF111617), panel = Color(0xFF1C2324),
            border = Color(0xFF354041), text = Color(0xFFF2F6F4),
            muted = Color(0xFFA8B7B2), accent = Color(0xFF8CD9BD),
            onAccent = Color(0xFF111617), raised = Color(0xFF252E2F),
            selected = Color(0xFF293C35), wave = Color(0xFF25302E),
            waveSecondary = Color(0xFF1C2726), success = Color(0xFF8CD9BD)
        )
        val LIGHT = HomeAppearance(
            background = Color(0xFFF7F9F7), panel = Color.White,
            border = Color(0xFFCFDAD5), text = Color(0xFF1D2926),
            muted = Color(0xFF52625D), accent = Color(0xFF17684E),
            onAccent = Color.White, raised = Color(0xFFEDF2EF),
            selected = Color(0xFFE2F0E8), wave = Color(0xFFE4EDE7),
            waveSecondary = Color(0xFFEEF3EF), success = Color(0xFF17684E)
        )

        fun from(
            settings: UserSettingsDto?,
            overrides: HomeThemeOverrides? = null
        ): HomeAppearance {
            val base =
                if ((overrides?.style ?: settings?.theme)?.uppercase() == "LIGHT") LIGHT else DARK
            return base.copy(
                background = parseHex(overrides?.backgroundHex) ?: base.background,
                raised = parseHex(overrides?.secondaryHex) ?: base.raised
            )
        }

        private fun parseHex(raw: String?): Color? {
            val hex = raw?.removePrefix("#") ?: return null
            if (hex.length != 6 || !hex.all { it.isDigit() || it in 'a'..'f' || it in 'A'..'F' }) return null
            return Color(0xFF000000L or hex.toLong(16))
        }
    }
}
