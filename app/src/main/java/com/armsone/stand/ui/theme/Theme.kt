package com.armsone.stand.ui.theme

import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.node.DelegatableNode
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

private val MidnightScheme = darkColorScheme(
    primary = Color(0xFF61ADFF),
    onPrimary = Color.Black,
    secondary = Color(0xFF7DBBFF),
    background = Color(0xFF030517),
    onBackground = Color.White,
    surface = Color(0xFF10172D),
    onSurface = Color.White,
    surfaceVariant = Color(0xFF1B2A50),
    onSurfaceVariant = Color(0xFFD9E8FF),
)

private val SageScheme = darkColorScheme(
    primary = Color(0xFF8CC69E),
    onPrimary = Color.Black,
    secondary = Color(0xFFA4D3B0),
    background = Color(0xFF06130E),
    onBackground = Color.White,
    surface = Color(0xFF12231B),
    onSurface = Color.White,
    surfaceVariant = Color(0xFF203A2B),
    onSurfaceVariant = Color(0xFFDDEDE1),
)

fun lampGradientColors(theme: StandDisplayTheme, intensity: Float): List<Color> {
    val level = intensity.takeIf { it.isFinite() }?.coerceIn(0f, 1f) ?: 0f
    return when (theme) {
        StandDisplayTheme.COLOR -> listOf(
            Color(0xFFFF9E47).copy(alpha = level),
            Color(0xFFF2450F).copy(alpha = level * 0.72f),
            Color.Black.copy(alpha = 1f - level * 0.22f),
        )
        StandDisplayTheme.GRAYSCALE -> listOf(
            Color(0xFFB8B8B8).copy(alpha = level * 0.72f),
            Color(0xFF4D4D4D).copy(alpha = level * 0.64f),
            Color.Black.copy(alpha = 1f - level * 0.18f),
        )
        StandDisplayTheme.MIDNIGHT -> listOf(
            Color(0xFF4794FF).copy(alpha = level * 0.86f),
            Color(0xFF143394).copy(alpha = level * 0.78f),
            Color(0xFF030517).copy(alpha = 1f - level * 0.18f),
        )
        StandDisplayTheme.SAGE -> listOf(
            Color(0xFF99D1A3).copy(alpha = level * 0.80f),
            Color(0xFF336E4D).copy(alpha = level * 0.72f),
            Color(0xFF06130E).copy(alpha = 1f - level * 0.18f),
        )
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun STandTheme(
    displayTheme: StandDisplayTheme = StandDisplayTheme.COLOR,
    disablePressIndications: Boolean = false,
    content: @Composable () -> Unit,
) {
    val themedContent = @Composable {
        MaterialTheme(
            colorScheme = when (displayTheme) {
                StandDisplayTheme.COLOR -> ColorScheme
                StandDisplayTheme.GRAYSCALE -> GrayscaleScheme
                StandDisplayTheme.MIDNIGHT -> MidnightScheme
                StandDisplayTheme.SAGE -> SageScheme
            },
            content = content,
        )
    }
    if (disablePressIndications) {
        CompositionLocalProvider(
            LocalIndication provides NoPressIndication,
            LocalRippleConfiguration provides null,
            content = themedContent,
        )
    } else {
        themedContent()
    }
}

private object NoPressIndication : IndicationNodeFactory {
    override fun create(interactionSource: InteractionSource): DelegatableNode =
        object : Modifier.Node() {}

    override fun equals(other: Any?): Boolean = other === this

    override fun hashCode(): Int = -1
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
