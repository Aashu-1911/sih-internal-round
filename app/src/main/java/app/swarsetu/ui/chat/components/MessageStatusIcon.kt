package app.swarsetu.ui.chat.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.swarsetu.data.message.DeliveryPlane
import app.swarsetu.ui.chat.DeliveryStatus
import app.swarsetu.ui.theme.BrandPrimaryDark
import app.swarsetu.ui.theme.BrandPrimaryLight

@Composable
fun MessageMetaRow(
    timestampText: String,
    isOutgoing: Boolean,
    deliveryStatus: DeliveryStatus?,
    deliveryPlane: DeliveryPlane?,
    metaColor: Color,
    modifier: Modifier = Modifier,
) {
    val isDark = MaterialTheme.colorScheme.background.red < 0.5f
    val readColor = if (isDark) BrandPrimaryDark else BrandPrimaryLight

    Row(
        modifier = modifier.padding(start = 6.dp, top = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = timestampText,
            fontSize = 11.sp,
            color = metaColor,
            lineHeight = 12.sp,
        )

        if (isOutgoing && deliveryStatus != null) {
            when (deliveryStatus) {
                DeliveryStatus.Pending -> {
                    Icon(
                        imageVector = Icons.Default.AccessTime,
                        contentDescription = "Pending",
                        tint = metaColor.copy(alpha = 0.8f),
                        modifier = Modifier
                            .padding(start = 3.dp)
                            .size(11.dp),
                    )
                }
                DeliveryStatus.Sent -> {
                    Icon(
                        imageVector = Icons.Default.Done,
                        contentDescription = "Sent",
                        tint = metaColor,
                        modifier = Modifier
                            .padding(start = 3.dp)
                            .size(13.dp),
                    )
                }
                DeliveryStatus.Delivered -> {
                    Icon(
                        imageVector = Icons.Default.DoneAll,
                        contentDescription = "Delivered",
                        tint = readColor,
                        modifier = Modifier
                            .padding(start = 3.dp)
                            .size(14.dp),
                    )
                }
            }
        }
    }
}
