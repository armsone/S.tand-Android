package com.armsone.stand.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.DeviceFontFamilyName
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.armsone.stand.R
import com.armsone.stand.model.ClockFontChoice
import com.armsone.stand.model.StandDisplayTheme

val StandOrange = Color(0xFFFF8A2A)
val StandAmber = Color(0xFFFFB357)
val StandBlack = Color(0xFF050403)

private val ColorScheme = darkColorScheme(
    primary = StandOrange,
    onPrimary = Color.Black,
    secondary = StandAmber,
    background = StandBlack,
    onBackground = Color.White,
    surface = Color(0xFF17120E),
    onSurface = Color.White,
    surfaceVariant = Color(0xFF2A211A),
    onSurfaceVariant = Color(0xFFE6D7CA),
    error = Color(0xFFFF6B6B),
)

private val GrayscaleScheme = darkColorScheme(
    primary = Color(0xFFE4E4E4),
    onPrimary = Color.Black,
    secondary = Color(0xFFB8B8B8),
    background = Color.Black,
    onBackground = Color.White,
    surface = Color(0xFF181818),
    onSurface = Color.White,
    surfaceVariant = Color(0xFF2A2A2A),
    onSurfaceVariant = Color(0xFFD8D8D8),
)

@Composable
fun STandTheme(
    displayTheme: StandDisplayTheme = StandDisplayTheme.COLOR,
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (displayTheme == StandDisplayTheme.GRAYSCALE) {
            GrayscaleScheme
        } else {
            ColorScheme
        },
        content = content,
    )
}

private val AndroidSystemRounded = FontFamily(
    Font(
        familyName = DeviceFontFamilyName("sans-serif-rounded"),
        weight = FontWeight.Thin,
    ),
)

fun ClockFontChoice.fontFamily(): FontFamily = when (this) {
    ClockFontChoice.SYSTEM_ROUNDED -> AndroidSystemRounded
    ClockFontChoice.PRETENDARD -> FontFamily(Font(R.font.pretendard_regular))
    ClockFontChoice.KAKAO_BIG_SANS -> FontFamily(Font(R.font.kakao_big_sans_regular))
    ClockFontChoice.NANUM_GOTHIC -> FontFamily(Font(R.font.nanum_gothic_regular))
    ClockFontChoice.TENADA -> FontFamily(Font(R.font.tenada, weight = FontWeight.Normal))
    ClockFontChoice.BLACK_HAN_SANS -> FontFamily(Font(R.font.black_han_sans_regular))
    ClockFontChoice.DO_HYEON -> FontFamily(Font(R.font.do_hyeon_regular))
    ClockFontChoice.PAPERLOGY_BOLD -> FontFamily(
        Font(R.font.paperlogy_7_bold, weight = FontWeight.Bold),
    )
    ClockFontChoice.NEXON_LV1_GOTHIC -> FontFamily(Font(R.font.nexon_lv1_gothic_regular))
    ClockFontChoice.POPPINS -> FontFamily(Font(R.font.poppins_regular))
}

fun ClockFontChoice.fontWeight(): FontWeight = when (this) {
    ClockFontChoice.SYSTEM_ROUNDED -> FontWeight.Thin
    else -> FontWeight.Normal
}
