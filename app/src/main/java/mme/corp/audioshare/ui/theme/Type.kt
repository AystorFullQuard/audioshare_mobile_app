package mme.corp.audioshare.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// A compact human-readable scale; rhythm comes from spacing rather than boldness.
val Typography = Typography(
    headlineMedium = TextStyle(
        fontFamily = FontFamily.Default, fontSize = 27.sp,
        lineHeight = 32.sp, fontWeight = FontWeight.SemiBold
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.Default, fontSize = 21.sp,
        lineHeight = 27.sp, fontWeight = FontWeight.Medium
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.Default, fontSize = 17.sp,
        lineHeight = 23.sp, fontWeight = FontWeight.Medium
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default, fontSize = 16.sp,
        lineHeight = 24.sp, fontWeight = FontWeight.Normal
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.Default, fontSize = 14.sp,
        lineHeight = 21.sp, fontWeight = FontWeight.Normal
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.Default, fontSize = 12.sp,
        lineHeight = 18.sp, fontWeight = FontWeight.Normal
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.Default, fontSize = 11.sp,
        lineHeight = 16.sp, fontWeight = FontWeight.Medium
    )
)
