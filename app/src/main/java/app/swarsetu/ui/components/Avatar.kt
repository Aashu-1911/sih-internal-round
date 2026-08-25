package app.swarsetu.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import app.swarsetu.ui.image.BlobImage
import app.swarsetu.ui.preview.SwarSetuPreview
import coil3.compose.AsyncImage
import java.text.BreakIterator

/**
 * Predefined gradient color pairs for avatar fallbacks.
 * Cycles through these when no avatar image is available.
 */
private val avatarGradientPairs = listOf(
    Color(0xFF0D9488) to Color(0xFF14B8A6),  // Teal
    Color(0xFF7C3AED) to Color(0xFF8B5CF6),  // Violet
    Color(0xFF2563EB) to Color(0xFF3B82F6),  // Blue
    Color(0xFFDC2626) to Color(0xFFEF4444),  // Red
    Color(0xFFD97706) to Color(0xFFF59E0B),  // Amber
    Color(0xFF059669) to Color(0xFF10B981),  // Emerald
    Color(0xFF9333EA) to Color(0xFFA855F7),  // Purple
    Color(0xFFEA580C) to Color(0xFFF97316),  // Orange
)

/**
 * A circular avatar shared by the chat rows and the profile screen. Renders the avatar blob with
 * content hash [avatarHash] (loaded from the encrypted store via Coil) when present, otherwise a
 * gradient circle with the first letter of [name].
 *
 * @param showOnlineIndicator When true, displays a green dot in the bottom-right corner
 *   indicating the user is currently online on the mesh.
 */
@Composable
fun Avatar(
    avatarHash: String?,
    name: String,
    size: Dp,
    modifier: Modifier = Modifier,
    background: Color = MaterialTheme.colorScheme.secondaryContainer,
    contentColor: Color = MaterialTheme.colorScheme.onSecondaryContainer,
    textStyle: TextStyle = MaterialTheme.typography.labelLarge,
    contentDescription: String? = null,
    onClick: (() -> Unit)? = null,
    onClickLabel: String? = null,
    showOnlineIndicator: Boolean = false,
) {
    Box(
        modifier = modifier
            .then(if (onClick != null) Modifier.minimumInteractiveComponentSize() else Modifier)
            .size(size)
            .clip(CircleShape)
            .background(background)
            .then(
                if (onClick != null) {
                    Modifier.clickable(onClickLabel = onClickLabel, role = Role.Button, onClick = onClick)
                } else {
                    Modifier
                },
            )
            .then(
                if (contentDescription != null) {
                    Modifier.semantics { this.contentDescription = contentDescription }
                } else {
                    Modifier
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        // Show the avatar image when its blob is present; fall back to gradient + initial otherwise.
        var imageFailed by remember(avatarHash) { mutableStateOf(false) }
        if (avatarHash != null && !imageFailed) {
            AsyncImage(
                model = BlobImage(avatarHash),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                onError = { imageFailed = true },
            )
        } else {
            AvatarInitial(
                name = name,
                size = size,
                textStyle = textStyle,
                contentColor = contentColor,
            )
        }

        // Online indicator dot
        if (showOnlineIndicator) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(size * 0.25f)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.tertiary)
                    .semantics { this.contentDescription = "Online" },
            )
        }
    }
}

/**
 * Avatar with gradient background fallback based on name hash.
 * Provides more visual variety than a solid color.
 */
@Composable
fun GradientAvatar(
    name: String,
    size: Dp,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    onClick: (() -> Unit)? = null,
    onClickLabel: String? = null,
    showOnlineIndicator: Boolean = false,
) {
    val gradientIndex = remember(name) {
        name.hashCode().let { hash ->
            if (hash == Int.MIN_VALUE) 0 else kotlin.math.abs(hash) % avatarGradientPairs.size
        }
    }
    val (colorStart, colorEnd) = avatarGradientPairs[gradientIndex]

    Box(
        modifier = modifier
            .then(if (onClick != null) Modifier.minimumInteractiveComponentSize() else Modifier)
            .size(size)
            .clip(CircleShape)
            .background(
                Brush.linearGradient(
                    colors = listOf(colorStart, colorEnd),
                )
            )
            .then(
                if (onClick != null) {
                    Modifier.clickable(onClickLabel = onClickLabel, role = Role.Button, onClick = onClick)
                } else {
                    Modifier
                },
            )
            .then(
                if (contentDescription != null) {
                    Modifier.semantics { this.contentDescription = contentDescription }
                } else {
                    Modifier
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        AvatarInitial(
            name = name,
            size = size,
            textStyle = MaterialTheme.typography.labelLarge,
            contentColor = Color.White,
        )

        if (showOnlineIndicator) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(size * 0.25f)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.tertiary),
            )
        }
    }
}

/**
 * The fallback shown when there's no usable avatar image: a single uppercased initial of [name],
 * centered in the circle.
 */
@Composable
private fun AvatarInitial(
    name: String,
    size: Dp,
    textStyle: TextStyle,
    contentColor: Color,
) {
    val initialSize = with(LocalDensity.current) { (size * 0.5f).toSp() }
    Text(
        text = avatarInitial(name),
        style = textStyle.copy(fontSize = initialSize, lineHeight = TextUnit.Unspecified),
        color = contentColor,
        textAlign = TextAlign.Center,
        modifier = Modifier.clearAndSetSemantics {},
    )
}

/**
 * The leading user-perceived character of [name] for the fallback avatar, uppercased — or "?" when
 * [name] is blank.
 */
private fun avatarInitial(name: String): String {
    val trimmed = name.trimStart()
    if (trimmed.isEmpty()) return "?"
    val boundary = BreakIterator.getCharacterInstance().apply { setText(trimmed) }
    val end = boundary.next()
    val grapheme = if (end == BreakIterator.DONE) trimmed else trimmed.substring(0, end)
    return grapheme.uppercase()
}

@Preview(showBackground = true)
@Composable
fun AvatarInitialPreview() = SwarSetuPreview {
    Avatar(avatarHash = null, name = "Ada Lovelace", size = 40.dp)
}

@Preview(showBackground = true)
@Composable
fun AvatarLargeEmojiPreview() = SwarSetuPreview {
    Avatar(avatarHash = null, name = "🦊 Fox", size = 96.dp)
}

@Preview(showBackground = true)
@Composable
fun GradientAvatarPreview() = SwarSetuPreview {
    GradientAvatar(name = "Test User", size = 64.dp, showOnlineIndicator = true)
}
