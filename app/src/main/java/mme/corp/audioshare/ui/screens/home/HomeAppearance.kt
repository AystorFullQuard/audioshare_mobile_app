package mme.corp.audioshare.ui.screens.home

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import mme.corp.audioshare.data.dto.UserSettingsDto

/** Optional, validated local color overrides for a future settings UI / persisted preferences. */
internal data class HomeThemeOverrides(
    val backgroundHex: String? = null,
    val secondaryHex: String? = null,
    val style: String? = null
)

/** Centralized design tokens; layout uses these rather than hardcoded colors. */
internal data class HomeAppearance(
    val background: Color,
    val panel: Color,
    val panelBorder: Color,
    val text: Color,
    val muted: Color,
    val accent: Color,
    val accentSecondary: Color,
    val panelTop: Color,
    val inactiveTop: Color,
    val inactiveBottom: Color,
    val activeTop: Color,
    val activeBottom: Color,
    val waveTop: Color,
    val waveBottom: Color,
    val ambient: Color,
    val roomSurface: Color,
    val actionSurface: Color,
    val avatarSurface: Color,
    val arrowSurface: Color,
    val arrowText: Color,
    val online: Color,
    val sectionIcon: Color,
    val logoTop: Color,
    val selectedSubtitle: Color,
    val selectedIcon: Color,
    val arrowLight: Color
) {
    val accentGradient get() = Brush.horizontalGradient(listOf(accent, accentSecondary))

    companion object {
        /** Server currently exposes theme only. Optional future custom colors remain local until API supports them. */
        fun from(
            settings: UserSettingsDto?,
            overrides: HomeThemeOverrides? = null
        ): HomeAppearance {
            val base = when ((overrides?.style ?: settings?.theme)?.uppercase()) {
                "LIGHT" -> LIGHT
                else -> DARK
            }
            return base.copy(
                background = parseColor(overrides?.backgroundHex) ?: base.background,
                accentSecondary = parseColor(overrides?.secondaryHex) ?: base.accentSecondary
            )
        }

        private fun parseColor(hex: String?): Color? {
            val sanitized = hex?.removePrefix("#") ?: return null
            if (sanitized.length != 6 || !sanitized.all { it.isDigit() || it in 'A'..'F' || it in 'a'..'f' }) return null
            return Color(0xFF000000L or sanitized.toLong(16))
        }

        val DARK = HomeAppearance(
            background = Color(0xFF0A0E19), panel = Color(0xFF191E30),
            panelBorder = Color(0xFF303750), text = Color(0xFFE9E9FA),
            muted = Color(0xFFABB4CE), accent = Color(0xFF7566F1),
            accentSecondary = Color(0xFF4D59CE), panelTop = Color(0xFF1C2033),
            inactiveTop = Color(0xFF24293B), inactiveBottom = Color(0xFF191E30),
            activeTop = Color(0xFF4C52BA), activeBottom = Color(0xFF2D3480),
            waveTop = Color(0xFF4448A5), waveBottom = Color(0xFF1A2251),
            ambient = Color(0x364E5BCE), roomSurface = Color(0xFF24293C),
            actionSurface = Color(0xFF242A3B), avatarSurface = Color(0xFF292C48),
            arrowSurface = Color(0xFF343A50), arrowText = Color(0xFF3945A2),
            online = Color(0xFF5FDCAB), sectionIcon = Color(0xFFA6A6FF),
            logoTop = Color(0xFFBE8DF9), selectedSubtitle = Color(0xFFD2D4F9),
            selectedIcon = Color(0xFFD9D8FF), arrowLight = Color(0xFFDCE1FF)
        )
        val LIGHT = DARK.copy(
            background = Color(0xFFF5F6FF), panel = Color(0xFFFFFFFF),
            panelBorder = Color(0xFFD5D9EF), text = Color(0xFF20243F),
            muted = Color(0xFF596079), panelTop = Color(0xFFECEEFE),
            inactiveTop = Color(0xFFECEFFB), inactiveBottom = Color(0xFFF8F9FF),
            avatarSurface = Color(0xFFE4E8F8), roomSurface = Color(0xFFE9ECF8),
            actionSurface = Color(0xFFF2F3FC)
        )
    }
}
