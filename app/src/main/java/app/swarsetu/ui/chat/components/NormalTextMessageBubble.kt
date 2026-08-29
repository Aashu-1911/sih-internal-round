package app.swarsetu.ui.chat.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.swarsetu.data.message.DeliveryPlane
import app.swarsetu.mesh.protocol.Mention
import app.swarsetu.ui.chat.DeliveryStatus
import app.swarsetu.ui.theme.BrandPrimaryDark
import app.swarsetu.ui.theme.BrandPrimaryLight
import app.swarsetu.ui.theme.SwarSetuTheme

@Composable
fun NormalTextMessageBubble(
    text: String,
    isOutgoing: Boolean,
    timestampText: String,
    deliveryStatus: DeliveryStatus?,
    deliveryPlane: DeliveryPlane?,
    senderName: String? = null,
    mentions: List<Mention> = emptyList(),
    onMentionClick: ((String) -> Unit)? = null,
    replyPreview: @Composable (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val textColor = if (isOutgoing) SwarSetuTheme.chatColors.outgoingText else SwarSetuTheme.chatColors.incomingText
    val metaColor = if (isOutgoing) SwarSetuTheme.chatColors.outgoingMeta else SwarSetuTheme.chatColors.incomingMeta
    val isDark = MaterialTheme.colorScheme.background.red < 0.5f
    val senderColor = if (isDark) BrandPrimaryDark else BrandPrimaryLight

    Column(modifier = modifier) {
        if (!isOutgoing && !senderName.isNullOrBlank()) {
            Text(
                text = senderName,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = senderColor,
                ),
                modifier = Modifier.padding(bottom = 2.dp),
            )
        }

        if (replyPreview != null) {
            replyPreview()
            Spacer(modifier = Modifier.height(4.dp))
        }

        // Message text with inline timestamp flow
        Column {
            Text(
                text = text,
                color = textColor,
                fontSize = 15.sp,
                lineHeight = 20.sp,
            )

            Box(
                modifier = Modifier
                    .align(Alignment.End)
                    .padding(top = 2.dp),
            ) {
                MessageMetaRow(
                    timestampText = timestampText,
                    isOutgoing = isOutgoing,
                    deliveryStatus = deliveryStatus,
                    deliveryPlane = deliveryPlane,
                    metaColor = metaColor,
                )
            }
        }
    }
}
