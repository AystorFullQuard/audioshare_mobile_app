package mme.corp.audioshare.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

/** Stable SvRe palette across Home, Login, Profile, Rooms and Call controls. */
private val DarkColors = darkColorScheme(
    primary = SvReAccent,
    onPrimary = SvReInk,
    secondary = SvReMuted,
    onSecondary = SvReInk,
    background = SvReInk,
    onBackground = SvReText,
    surface = SvReSurface,
    onSurface = SvReText,
    surfaceVariant = SvReRaised,
    onSurfaceVariant = SvReMuted,
    outline = SvReBorder,
    outlineVariant = SvReBorder,
    error = androidx.compose.ui.graphics.Color(0xFFF2A59F)
)
private val LightColors = lightColorScheme(
    primary = SvReLightAccent,
    onPrimary = androidx.compose.ui.graphics.Color.White,
    secondary = androidx.compose.ui.graphics.Color(0xFF49645B),
    onSecondary = androidx.compose.ui.graphics.Color.White,
    background = SvReLightBackground,
    onBackground = SvReLightText,
    surface = SvReLightSurface,
    onSurface = SvReLightText,
    surfaceVariant = androidx.compose.ui.graphics.Color(0xFFEDF2EF),
    onSurfaceVariant = androidx.compose.ui.graphics.Color(0xFF52625D),
    outline = androidx.compose.ui.graphics.Color(0xFFCFDAD5),
    outlineVariant = androidx.compose.ui.graphics.Color(0xFFDAE3DF)
)

@Composable
fun AudioShareTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Kept for call-site compatibility; dynamic wallpaper colors would break the SvRe system.
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = Typography,
        shapes = Shapes(
            small = RoundedCornerShape(10.dp),
            medium = RoundedCornerShape(14.dp),
            large = RoundedCornerShape(20.dp)
        ),
        content = content
    )
}
