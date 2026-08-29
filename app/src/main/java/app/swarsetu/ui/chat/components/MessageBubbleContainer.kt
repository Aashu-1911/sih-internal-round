package app.swarsetu.ui.chat.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import app.swarsetu.ui.theme.SwarSetuTheme

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MessageBubbleContainer(
    isOutgoing: Boolean,
    modifier: Modifier = Modifier,
    isConsecutive: Boolean = false,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val configuration = LocalConfiguration.current
    val maxWidth = (configuration.screenWidthDp * 0.82f).dp

    val bubbleShape = if (isOutgoing) {
        if (isConsecutive) {
            RoundedCornerShape(16.dp, 6.dp, 6.dp, 16.dp)
        } else {
            RoundedCornerShape(16.dp, 16.dp, 4.dp, 16.dp)
        }
    } else {
        if (isConsecutive) {
            RoundedCornerShape(6.dp, 16.dp, 16.dp, 6.dp)
        } else {
            RoundedCornerShape(16.dp, 16.dp, 16.dp, 4.dp)
        }
    }

    val bubbleBg = if (isOutgoing) SwarSetuTheme.chatColors.outgoingBubble else SwarSetuTheme.chatColors.incomingBubble
    val bubbleBorder = if (isOutgoing) SwarSetuTheme.chatColors.outgoingBubbleBorder else SwarSetuTheme.chatColors.incomingBubbleBorder
    val verticalPadding = if (isConsecutive) 1.5.dp else 4.dp

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                start = if (isOutgoing) 48.dp else 12.dp,
                end = if (isOutgoing) 12.dp else 48.dp,
                top = verticalPadding,
                bottom = verticalPadding,
            ),
        horizontalArrangement = if (isOutgoing) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Bottom,
    ) {
        Surface(
            shape = bubbleShape,
            color = bubbleBg,
            border = BorderStroke(0.8.dp, bubbleBorder),
            shadowElevation = 0.5.dp,
            modifier = Modifier
                .widthIn(min = 40.dp, max = maxWidth)
                .clip(bubbleShape)
                .then(
                    if (onClick != null || onLongClick != null) {
                        Modifier.combinedClickable(
                            onClick = { onClick?.invoke() },
                            onLongClick = { onLongClick?.invoke() },
                        )
                    } else {
                        Modifier
                    },
                ),
        ) {
            Box(modifier = Modifier.padding(horizontal = 11.dp, vertical = 7.dp)) {
                content()
            }
        }
    }
}
