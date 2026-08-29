package app.swarsetu.ui.chat.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke

/**
 * Faint, non-intrusive soundwave & bridge geometry motif.
 * Draws subtle geometric curves in the background that give SwarSetu a distinct, premium identity
 * without interfering with message readability.
 */
@Composable
fun ChatBackground(
    modifier: Modifier = Modifier,
) {
    val isDark = MaterialTheme.colorScheme.background.red < 0.5f
    val strokeColor = if (isDark) {
        Color(0xFFE5B558).copy(alpha = 0.025f)
    } else {
        Color(0xFFC4861C).copy(alpha = 0.035f)
    }

    Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height

        // Top-right subtle brand waveform
        val path1 = Path().apply {
            moveTo(w * 0.4f, 0f)
            cubicTo(
                w * 0.6f, h * 0.08f,
                w * 0.8f, h * 0.02f,
                w, h * 0.12f,
            )
        }
        drawPath(path1, color = strokeColor, style = Stroke(width = 2f))

        // Center-left gentle bridge arc
        val path2 = Path().apply {
            moveTo(0f, h * 0.45f)
            cubicTo(
                w * 0.25f, h * 0.40f,
                w * 0.75f, h * 0.55f,
                w, h * 0.50f,
            )
        }
        drawPath(path2, color = strokeColor, style = Stroke(width = 1.5f))

        // Bottom-right gentle arc
        val path3 = Path().apply {
            moveTo(0f, h * 0.85f)
            cubicTo(
                w * 0.35f, h * 0.90f,
                w * 0.65f, h * 0.80f,
                w, h * 0.88f,
            )
        }
        drawPath(path3, color = strokeColor, style = Stroke(width = 2f))
    }
}
