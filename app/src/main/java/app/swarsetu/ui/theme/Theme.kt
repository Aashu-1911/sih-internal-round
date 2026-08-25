package app.swarsetu.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

private val LightColorScheme = lightColorScheme(
    primary = SwarsetuPrimaryLight,
    onPrimary = SwarsetuOnPrimaryLight,
    primaryContainer = SwarsetuPrimaryContainerLight,
    onPrimaryContainer = SwarsetuOnPrimaryContainerLight,
    secondary = SwarsetuSecondaryLight,
    onSecondary = SwarsetuOnSecondaryLight,
    secondaryContainer = SwarsetuSecondaryContainerLight,
    onSecondaryContainer = SwarsetuOnSecondaryContainerLight,
    tertiary = SwarsetuTertiaryLight,
    onTertiary = SwarsetuOnTertiaryLight,
    tertiaryContainer = SwarsetuTertiaryContainerLight,
    onTertiaryContainer = SwarsetuOnTertiaryContainerLight,
    error = SwarsetuErrorLight,
    onError = SwarsetuOnErrorLight,
    errorContainer = SwarsetuErrorContainerLight,
    onErrorContainer = SwarsetuOnErrorContainerLight,
    background = SwarsetuBackgroundLight,
    onBackground = SwarsetuOnBackgroundLight,
    surface = SwarsetuSurfaceLight,
    onSurface = SwarsetuOnSurfaceLight,
    surfaceVariant = SwarsetuSurfaceVariantLight,
    onSurfaceVariant = SwarsetuOnSurfaceVariantLight,
    outline = SwarsetuOutlineLight,
    outlineVariant = SwarsetuOutlineVariantLight,
)

private val DarkColorScheme = darkColorScheme(
    primary = SwarsetuPrimaryDark,
    onPrimary = SwarsetuOnPrimaryDark,
    primaryContainer = SwarsetuPrimaryContainerDark,
    onPrimaryContainer = SwarsetuOnPrimaryContainerDark,
    secondary = SwarsetuSecondaryDark,
    onSecondary = SwarsetuOnSecondaryDark,
    secondaryContainer = SwarsetuSecondaryContainerDark,
    onSecondaryContainer = SwarsetuOnSecondaryContainerDark,
    tertiary = SwarsetuTertiaryDark,
    onTertiary = SwarsetuOnTertiaryDark,
    tertiaryContainer = SwarsetuTertiaryContainerDark,
    onTertiaryContainer = SwarsetuOnTertiaryContainerDark,
    error = SwarsetuErrorDark,
    onError = SwarsetuOnErrorDark,
    errorContainer = SwarsetuErrorContainerDark,
    onErrorContainer = SwarsetuOnErrorContainerDark,
    background = SwarsetuBackgroundDark,
    onBackground = SwarsetuOnBackgroundDark,
    surface = SwarsetuSurfaceDark,
    onSurface = SwarsetuOnSurfaceDark,
    surfaceVariant = SwarsetuSurfaceVariantDark,
    onSurfaceVariant = SwarsetuOnSurfaceVariantDark,
    outline = SwarsetuOutlineDark,
    outlineVariant = SwarsetuOutlineVariantDark,
)

/**
 * Material 3 shape system for Swarsetu.
 * Rounded corners convey friendliness and approachability.
 */
val SwarsetuShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(24.dp),
)

@Composable
fun SwarSetuTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Enable dynamic color on Android 12+ for personalized theming
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = SwarsetuShapes,
        content = content,
    )
}
