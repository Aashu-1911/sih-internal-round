package app.swarsetu.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val LightColorScheme =
    lightColorScheme(
        primary = BrandPrimaryLight,
        onPrimary = BrandOnPrimaryLight,
        primaryContainer = BrandPrimaryContainerLight,
        onPrimaryContainer = BrandOnPrimaryContainerLight,
        secondary = BrandSecondaryLight,
        onSecondary = BrandOnSecondaryLight,
        secondaryContainer = BrandSecondaryContainerLight,
        onSecondaryContainer = BrandOnSecondaryContainerLight,
        tertiary = BrandTertiaryLight,
        onTertiary = BrandOnTertiaryLight,
        tertiaryContainer = BrandTertiaryContainerLight,
        onTertiaryContainer = BrandOnTertiaryContainerLight,
        background = BackgroundLight,
        onBackground = OnBackgroundLight,
        surface = SurfaceLight,
        onSurface = OnSurfaceLight,
        surfaceVariant = SurfaceVariantLight,
        onSurfaceVariant = OnSurfaceVariantLight,
        outline = OutlineLight,
        outlineVariant = OutlineVariantLight,
        error = ErrorLight,
        onError = OnErrorLight,
    )

private val DarkColorScheme =
    darkColorScheme(
        primary = BrandPrimaryDark,
        onPrimary = BrandOnPrimaryDark,
        primaryContainer = BrandPrimaryContainerDark,
        onPrimaryContainer = BrandOnPrimaryContainerDark,
        secondary = BrandSecondaryDark,
        onSecondary = BrandOnSecondaryDark,
        secondaryContainer = BrandSecondaryContainerDark,
        onSecondaryContainer = BrandOnSecondaryContainerDark,
        tertiary = BrandTertiaryDark,
        onTertiary = BrandOnTertiaryDark,
        tertiaryContainer = BrandTertiaryContainerDark,
        onTertiaryContainer = BrandOnTertiaryContainerDark,
        background = BackgroundDark,
        onBackground = OnBackgroundDark,
        surface = SurfaceDark,
        onSurface = OnSurfaceDark,
        surfaceVariant = SurfaceVariantDark,
        onSurfaceVariant = OnSurfaceVariantDark,
        outline = OutlineDark,
        outlineVariant = OutlineVariantDark,
        error = ErrorDark,
        onError = OnErrorDark,
    )

private val DarkChatColors =
    SwarSetuChatColors(
        outgoingBubble = Color(0xFF282215),
        outgoingBubbleBorder = Color(0xFF4A3D22),
        outgoingText = Color(0xFFFAF7EE),
        outgoingMeta = Color(0xFFB5A88E),
        incomingBubble = Color(0xFF161A22),
        incomingBubbleBorder = Color(0xFF262C38),
        incomingText = Color(0xFFECEFF4),
        incomingMeta = Color(0xFF8E95A2),
        voiceBadgeBackground = Color(0xFF382D16),
        voiceBadgeText = Color(0xFFFDE8BC),
        voiceWaveformActive = Color(0xFFE5B558),
        voiceWaveformInactive = Color(0xFF474134),
        dateSeparatorBackground = Color(0xFF1C2029),
        dateSeparatorText = Color(0xFF9AA0AC),
        composerBackground = Color(0xFF15181E),
        composerBorder = Color(0xFF262C38),
        composerText = Color(0xFFECEFF4),
        micActive = Color(0xFFEF4444),
    )

private val LightChatColors =
    SwarSetuChatColors(
        outgoingBubble = Color(0xFFFFF7E6),
        outgoingBubbleBorder = Color(0xFFF0DEC0),
        outgoingText = Color(0xFF241B08),
        outgoingMeta = Color(0xFF7A6B4E),
        incomingBubble = Color(0xFFFFFFFF),
        incomingBubbleBorder = Color(0xFFE5E7EB),
        incomingText = Color(0xFF181C20),
        incomingMeta = Color(0xFF6B7280),
        voiceBadgeBackground = Color(0xFFFFF2D6),
        voiceBadgeText = Color(0xFF7A4E08),
        voiceWaveformActive = Color(0xFFC4861C),
        voiceWaveformInactive = Color(0xFFD6D1C7),
        dateSeparatorBackground = Color(0xFFE9ECF0),
        dateSeparatorText = Color(0xFF4B5563),
        composerBackground = Color(0xFFFFFFFF),
        composerBorder = Color(0xFFDDE1E6),
        composerText = Color(0xFF181C20),
        micActive = Color(0xFFDC2626),
    )

object SwarSetuTheme {
    val chatColors: SwarSetuChatColors
        @Composable
        get() = LocalSwarSetuChatColors.current
}

@Composable
fun SwarSetuTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme =
        when {
            dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                val context = LocalContext.current
                if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            }
            darkTheme -> DarkColorScheme
            else -> LightColorScheme
        }

    val chatColors = if (darkTheme) DarkChatColors else LightChatColors

    CompositionLocalProvider(LocalSwarSetuChatColors provides chatColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content,
        )
    }
}
