package app.swarsetu.ui.theme

import androidx.compose.ui.graphics.Color

// Swarsetu Brand Palette — Teal/Emerald inspired for voice-first communication
// Represents: Clarity (teal), Connection (emerald), Trust (deep blue-green)
// Seed: #0D9488 (Teal-600) — a warm, approachable green-blue that evokes voice waves and connectivity

// Light Theme
val SwarsetuPrimaryLight = Color(0xFF0D9488)           // Teal-600 — primary actions, active states
val SwarsetuOnPrimaryLight = Color(0xFFFFFFFF)          // White on primary
val SwarsetuPrimaryContainerLight = Color(0xFFCCFBF1)   // Teal-100 — subtle primary containers
val SwarsetuOnPrimaryContainerLight = Color(0xFF134E4A) // Teal-900 — text on primary containers

val SwarsetuSecondaryLight = Color(0xFF475569)          // Slate-600 — secondary elements
val SwarsetuOnSecondaryLight = Color(0xFFFFFFFF)        // White on secondary
val SwarsetuSecondaryContainerLight = Color(0xFFF1F5F9) // Slate-100 — secondary containers
val SwarsetuOnSecondaryContainerLight = Color(0xFF1E293B) // Slate-800 — text on secondary containers

val SwarsetuTertiaryLight = Color(0xFF7C3AED)           // Violet-600 — accent, voice indicators
val SwarsetuOnTertiaryLight = Color(0xFFFFFFFF)         // White on tertiary
val SwarsetuTertiaryContainerLight = Color(0xFFEDE9FE)  // Violet-100 — tertiary containers
val SwarsetuOnTertiaryContainerLight = Color(0xFF4C1D95) // Violet-900 — text on tertiary containers

val SwarsetuErrorLight = Color(0xFFDC2626)              // Red-600 — errors, destructive actions
val SwarsetuOnErrorLight = Color(0xFFFFFFFF)            // White on error
val SwarsetuErrorContainerLight = Color(0xFFFEE2E2)     // Red-100 — error containers
val SwarsetuOnErrorContainerLight = Color(0xFF991B1B)   // Red-800 — text on error containers

val SwarsetuBackgroundLight = Color(0xFFF8FAFC)         // Slate-50 — app background
val SwarsetuOnBackgroundLight = Color(0xFF0F172A)       // Slate-900 — text on background
val SwarsetuSurfaceLight = Color(0xFFFFFFFF)            // White — card surfaces
val SwarsetuOnSurfaceLight = Color(0xFF0F172A)          // Slate-900 — text on surface
val SwarsetuSurfaceVariantLight = Color(0xFFF1F5F9)     // Slate-100 — surface variants
val SwarsetuOnSurfaceVariantLight = Color(0xFF475569)   // Slate-600 — secondary text
val SwarsetuOutlineLight = Color(0xFFCBD5E1)            // Slate-200 — borders
val SwarsetuOutlineVariantLight = Color(0xFFE2E8F0)     // Slate-200 — subtle borders

// Dark Theme
val SwarsetuPrimaryDark = Color(0xFF5EEAD4)             // Teal-300 — primary in dark mode
val SwarsetuOnPrimaryDark = Color(0xFF134E4A)           // Teal-900 — text on primary in dark
val SwarsetuPrimaryContainerDark = Color(0xFF115E59)    // Teal-800 — primary containers in dark
val SwarsetuOnPrimaryContainerDark = Color(0xFFCCFBF1)  // Teal-100 — text on primary containers in dark

val SwarsetuSecondaryDark = Color(0xFF94A3B8)           // Slate-400 — secondary in dark
val SwarsetuOnSecondaryDark = Color(0xFF1E293B)         // Slate-800 — text on secondary in dark
val SwarsetuSecondaryContainerDark = Color(0xFF334155)  // Slate-700 — secondary containers in dark
val SwarsetuOnSecondaryContainerDark = Color(0xFFF1F5F9) // Slate-100 — text on secondary containers in dark

val SwarsetuTertiaryDark = Color(0xFFA78BFA)            // Violet-400 — accent in dark
val SwarsetuOnTertiaryDark = Color(0xFF4C1D95)          // Violet-900 — text on tertiary in dark
val SwarsetuTertiaryContainerDark = Color(0xFF5B21B6)   // Violet-700 — tertiary containers in dark
val SwarsetuOnTertiaryContainerDark = Color(0xFFEDE9FE) // Violet-100 — text on tertiary containers in dark

val SwarsetuErrorDark = Color(0xFFFCA5A5)               // Red-300 — errors in dark
val SwarsetuOnErrorDark = Color(0xFF991B1B)              // Red-800 — text on error in dark
val SwarsetuErrorContainerDark = Color(0xFF7F1D1D)       // Red-900 — error containers in dark
val SwarsetuOnErrorContainerDark = Color(0xFFFEE2E2)     // Red-100 — text on error containers in dark

val SwarsetuBackgroundDark = Color(0xFF0F172A)           // Slate-900 — dark background
val SwarsetuOnBackgroundDark = Color(0xFFF8FAFC)         // Slate-50 — text on dark background
val SwarsetuSurfaceDark = Color(0xFF1E293B)              // Slate-800 — dark card surfaces
val SwarsetuOnSurfaceDark = Color(0xFFF8FAFC)            // Slate-50 — text on dark surface
val SwarsetuSurfaceVariantDark = Color(0xFF334155)       // Slate-700 — surface variants in dark
val SwarsetuOnSurfaceVariantDark = Color(0xFF94A3B8)     // Slate-400 — secondary text in dark
val SwarsetuOutlineDark = Color(0xFF475569)               // Slate-600 — borders in dark
val SwarsetuOutlineVariantDark = Color(0xFF334155)        // Slate-700 — subtle borders in dark

// Voice-specific accent colors (used across the app for voice/STT/TTS indicators)
val VoiceRecordingRed = Color(0xFFEF4444)                 // Red-500 — active recording
val VoiceWaveTeal = Color(0xFF14B8A6)                     // Teal-400 — voice wave animations
val VoicePlaybackViolet = Color(0xFF8B5CF6)               // Violet-500 — playback state
val OnlineGreen = Color(0xFF22C55E)                       // Green-500 — online indicator
val MeshConnectedTeal = Color(0xFF0D9488)                 // Teal-600 — mesh connected
