package app.swarsetu.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// ==========================================
// SwarSetu Brand Palette
// Voice • Connect • Understand
// ==========================================

// --- Light Palette ---
val BrandPrimaryLight = Color(0xFFC4861C)           // Refined warm gold
val BrandOnPrimaryLight = Color(0xFFFFFFFF)
val BrandPrimaryContainerLight = Color(0xFFFFF2D6)
val BrandOnPrimaryContainerLight = Color(0xFF332000)

val BrandSecondaryLight = Color(0xFF4A5568)         // Slate
val BrandOnSecondaryLight = Color(0xFFFFFFFF)
val BrandSecondaryContainerLight = Color(0xFFE2E8F0)
val BrandOnSecondaryContainerLight = Color(0xFF1A202C)

val BrandTertiaryLight = Color(0xFF059669)          // Secure / Online emerald
val BrandOnTertiaryLight = Color(0xFFFFFFFF)
val BrandTertiaryContainerLight = Color(0xFFD1FAE5)
val BrandOnTertiaryContainerLight = Color(0xFF064E3B)

val BackgroundLight = Color(0xFFF8F9FA)             // Warm off-white
val OnBackgroundLight = Color(0xFF1A1D21)
val SurfaceLight = Color(0xFFFFFFFF)                // Clean crisp card
val OnSurfaceLight = Color(0xFF1A1D21)
val SurfaceVariantLight = Color(0xFFF0F2F5)
val OnSurfaceVariantLight = Color(0xFF5E6573)
val OutlineLight = Color(0xFFD6D9E0)
val OutlineVariantLight = Color(0xFFE8EAEF)
val ErrorLight = Color(0xFFDC2626)
val OnErrorLight = Color(0xFFFFFFFF)

// --- Dark Palette ---
val BrandPrimaryDark = Color(0xFFE5B558)            // Subtle radiant gold
val BrandOnPrimaryDark = Color(0xFF261900)
val BrandPrimaryContainerDark = Color(0xFF3A2B10)
val BrandOnPrimaryContainerDark = Color(0xFFFDE8BC)

val BrandSecondaryDark = Color(0xFFA0AEC0)          // Muted slate
val BrandOnSecondaryDark = Color(0xFF1A202C)
val BrandSecondaryContainerDark = Color(0xFF2D3748)
val BrandOnSecondaryContainerDark = Color(0xFFE2E8F0)

val BrandTertiaryDark = Color(0xFF34D399)           // Secure / Online emerald
val BrandOnTertiaryDark = Color(0xFF064E3B)
val BrandTertiaryContainerDark = Color(0xFF065F46)
val BrandOnTertiaryContainerDark = Color(0xFFA7F3D0)

val BackgroundDark = Color(0xFF0D0F12)              // Deep near-black charcoal
val OnBackgroundDark = Color(0xFFECEFF4)
val SurfaceDark = Color(0xFF15181E)                 // Layered primary charcoal
val OnSurfaceDark = Color(0xFFECEFF4)
val SurfaceVariantDark = Color(0xFF1D212A)          // Elevated secondary charcoal
val OnSurfaceVariantDark = Color(0xFF9AA0AC)
val OutlineDark = Color(0xFF2A303C)
val OutlineVariantDark = Color(0xFF1F242E)
val ErrorDark = Color(0xFFF87171)
val OnErrorDark = Color(0xFF450A0A)

// --- Legacy aliases to prevent compile errors in any referenced locations ---
val CoralPrimaryLight = BrandPrimaryLight
val CoralOnPrimaryLight = BrandOnPrimaryLight
val CoralPrimaryContainerLight = BrandPrimaryContainerLight
val CoralOnPrimaryContainerLight = BrandOnPrimaryContainerLight
val CoralSecondaryLight = BrandSecondaryLight
val CoralOnSecondaryLight = BrandOnSecondaryLight
val CoralSecondaryContainerLight = BrandSecondaryContainerLight
val CoralOnSecondaryContainerLight = BrandOnSecondaryContainerLight
val CoralTertiaryLight = BrandTertiaryLight
val CoralOnTertiaryLight = BrandOnTertiaryLight
val CoralTertiaryContainerLight = BrandTertiaryContainerLight
val CoralOnTertiaryContainerLight = BrandOnTertiaryContainerLight
val CoralPrimaryDark = BrandPrimaryDark
val CoralOnPrimaryDark = BrandOnPrimaryDark
val CoralPrimaryContainerDark = BrandPrimaryContainerDark
val CoralOnPrimaryContainerDark = BrandOnPrimaryContainerDark
val CoralSecondaryDark = BrandSecondaryDark
val CoralOnSecondaryDark = BrandOnSecondaryDark
val CoralSecondaryContainerDark = BrandSecondaryContainerDark
val CoralOnSecondaryContainerDark = BrandOnSecondaryContainerDark
val CoralTertiaryDark = BrandTertiaryDark
val CoralOnTertiaryDark = BrandOnTertiaryDark
val CoralTertiaryContainerDark = BrandTertiaryContainerDark
val CoralOnTertiaryContainerDark = BrandOnTertiaryContainerDark

// ==========================================
// Specialized Chat Color Tokens
// ==========================================

@Immutable
data class SwarSetuChatColors(
    val outgoingBubble: Color,
    val outgoingBubbleBorder: Color,
    val outgoingText: Color,
    val outgoingMeta: Color,
    val incomingBubble: Color,
    val incomingBubbleBorder: Color,
    val incomingText: Color,
    val incomingMeta: Color,
    val voiceBadgeBackground: Color,
    val voiceBadgeText: Color,
    val voiceWaveformActive: Color,
    val voiceWaveformInactive: Color,
    val dateSeparatorBackground: Color,
    val dateSeparatorText: Color,
    val composerBackground: Color,
    val composerBorder: Color,
    val composerText: Color,
    val micActive: Color,
)

val LocalSwarSetuChatColors = staticCompositionLocalOf {
    SwarSetuChatColors(
        outgoingBubble = Color.Unspecified,
        outgoingBubbleBorder = Color.Unspecified,
        outgoingText = Color.Unspecified,
        outgoingMeta = Color.Unspecified,
        incomingBubble = Color.Unspecified,
        incomingBubbleBorder = Color.Unspecified,
        incomingText = Color.Unspecified,
        incomingMeta = Color.Unspecified,
        voiceBadgeBackground = Color.Unspecified,
        voiceBadgeText = Color.Unspecified,
        voiceWaveformActive = Color.Unspecified,
        voiceWaveformInactive = Color.Unspecified,
        dateSeparatorBackground = Color.Unspecified,
        dateSeparatorText = Color.Unspecified,
        composerBackground = Color.Unspecified,
        composerBorder = Color.Unspecified,
        composerText = Color.Unspecified,
        micActive = Color.Unspecified,
    )
}
