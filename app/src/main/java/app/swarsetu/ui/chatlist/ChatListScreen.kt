package app.swarsetu.ui.chatlist

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.text.format.DateUtils
import android.widget.Toast
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DownloadForOffline
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.MarkChatUnread
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.swarsetu.R
import app.swarsetu.data.message.DeliveryPlane
import app.swarsetu.data.relay.RelayPlane
import app.swarsetu.mesh.TransportHealth
import app.swarsetu.ui.chat.DeliveryStatus
import app.swarsetu.ui.chat.deliveryIcon
import app.swarsetu.ui.chat.deliveryLabel
import app.swarsetu.ui.chat.resolve
import app.swarsetu.ui.components.Avatar
import app.swarsetu.ui.components.GradientAvatar
import app.swarsetu.ui.components.ConnectionStatusRow
import app.swarsetu.ui.components.RoomAvatar
import app.swarsetu.ui.image.BlobImage
import app.swarsetu.ui.invite.ShareKnitDialog
import app.swarsetu.ui.invite.ShareStorageException
import app.swarsetu.ui.invite.launchApkShareChooser
import app.swarsetu.ui.invite.prepareKnitApk
import app.swarsetu.ui.preview.PREVIEW_NOW
import app.swarsetu.ui.preview.SwarSetuPreview
import app.swarsetu.ui.util.compactTimeAgo
import app.swarsetu.ui.util.rememberCurrentTimeMillis
import coil3.compose.AsyncImage
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

@Composable
fun ChatListScreen(
    onOpenConversation: (conversationId: String) -> Unit,
    onNewMessage: () -> Unit,
    onOpenProfile: () -> Unit,
    onOpenDiagnostics: () -> Unit,
    onOpenBlockedUsers: () -> Unit,
    onOpenMessageRequests: () -> Unit,
    onOpenDonate: () -> Unit,
    onOpenVerify: () -> Unit,
    viewModel: ChatListViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showShareApp by remember { mutableStateOf(false) }
    var preparingShare by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val now by rememberCurrentTimeMillis()

    ChatListScreenContent(
        state = state,
        now = now,
        onOpenConversation = onOpenConversation,
        onNewMessage = onNewMessage,
        onOpenProfile = onOpenProfile,
        onOpenDiagnostics = onOpenDiagnostics,
        onOpenBlockedUsers = onOpenBlockedUsers,
        onOpenMessageRequests = onOpenMessageRequests,
        onOpenDonate = onOpenDonate,
        onOpenVerify = onOpenVerify,
        onShareApp = { showShareApp = true },
        onOpenRadioSettings = { warning -> openRadioSettings(context, warning) },
        onDismissRadioWarning = viewModel::dismissRadioWarning,
        onDeleteConversation = viewModel::deleteConversation,
    )

    if (showShareApp) {
        ShareKnitDialog(
            onConfirm = {
                showShareApp = false
                preparingShare = true
                scope.launch {
                    try {
                        runCatching {
                            launchApkShareChooser(context, prepareKnitApk(context))
                        }.onFailure { e ->
                            val msg = if (e is ShareStorageException) {
                                R.string.share_app_error_storage
                            } else {
                                R.string.share_app_error
                            }
                            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                        }
                    } finally {
                        preparingShare = false
                    }
                }
            },
            onDismiss = { showShareApp = false },
        )
    }

    if (preparingShare) {
        AlertDialog(
            onDismissRequest = {},
            confirmButton = {},
            text = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator()
                    Spacer(Modifier.width(20.dp))
                    Text(stringResource(R.string.share_app_preparing))
                }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ChatListScreenContent(
    state: ChatListUiState,
    now: Long,
    onOpenConversation: (conversationId: String) -> Unit,
    onNewMessage: () -> Unit,
    onOpenProfile: () -> Unit,
    onOpenDiagnostics: () -> Unit,
    onOpenBlockedUsers: () -> Unit,
    onOpenMessageRequests: () -> Unit,
    onOpenDonate: () -> Unit,
    onOpenVerify: () -> Unit,
    onShareApp: () -> Unit,
    onOpenRadioSettings: (RadioWarning) -> Unit,
    onDismissRadioWarning: () -> Unit,
    onDeleteConversation: (conversationId: String) -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = stringResource(R.string.app_name),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.semantics { heading() },
                        )
                        ConnectionStatusRow(state.neighborCount, state.transportHealth, state.relayPlane)
                    }
                },
                actions = {
                    if (state.requestCount > 0) {
                        IconButton(
                            onClick = onOpenMessageRequests,
                            modifier = Modifier.size(48.dp).semantics { testTag = "chatlist_requests" },
                        ) {
                            BadgedBox(
                                badge = { Badge { Text(state.requestCount.toString()) } },
                            ) {
                                Icon(
                                    Icons.Filled.MarkChatUnread,
                                    contentDescription = stringResource(R.string.message_requests_title),
                                )
                            }
                        }
                    }
                    Box {
                        IconButton(onClick = { menuOpen = true }, modifier = Modifier.size(48.dp)) {
                            Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.chat_more_options))
                        }
                        DropdownMenu(
                            expanded = menuOpen,
                            onDismissRequest = { menuOpen = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.settings_title)) },
                                leadingIcon = { Icon(Icons.Filled.Settings, contentDescription = null) },
                                onClick = {
                                    menuOpen = false
                                    onOpenProfile()
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.verify_contact_title)) },
                                leadingIcon = { Icon(Icons.Filled.QrCodeScanner, contentDescription = null) },
                                onClick = {
                                    menuOpen = false
                                    onOpenVerify()
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.diagnostics_title)) },
                                leadingIcon = { Icon(Icons.Filled.NetworkCheck, contentDescription = null) },
                                onClick = {
                                    menuOpen = false
                                    onOpenDiagnostics()
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.blocked_users_title)) },
                                leadingIcon = { Icon(Icons.Filled.Block, contentDescription = null) },
                                onClick = {
                                    menuOpen = false
                                    onOpenBlockedUsers()
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.donate_title)) },
                                leadingIcon = { Icon(Icons.Filled.Favorite, contentDescription = null) },
                                onClick = {
                                    menuOpen = false
                                    onOpenDonate()
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.share_app_menu)) },
                                leadingIcon = { Icon(Icons.Filled.DownloadForOffline, contentDescription = null) },
                                onClick = {
                                    menuOpen = false
                                    onShareApp()
                                },
                            )
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onNewMessage,
                modifier = Modifier.semantics { testTag = "chatlist_fab" },
                icon = {
                    Icon(
                        Icons.AutoMirrored.Filled.Message,
                        contentDescription = null,
                    )
                },
                text = {
                    Text(
                        text = stringResource(R.string.contacts_new_message),
                        style = MaterialTheme.typography.labelLarge,
                    )
                },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            state.radioWarning?.let { warning ->
                RadioWarningBanner(
                    warning = warning,
                    onOpenSettings = { onOpenRadioSettings(warning) },
                    onDismiss = if (warning == RadioWarning.AllRadiosOff) null else onDismissRadioWarning,
                )
            }
            if (state.isLoading) {
                ChatListSkeleton(modifier = Modifier.fillMaxSize())
            } else if (state.conversations.isEmpty()) {
                // Empty state
                EmptyChatListState(
                    modifier = Modifier.fillMaxSize(),
                    onNewMessage = onNewMessage,
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = 8.dp, horizontal = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    items(state.conversations, key = { it.id }) { row ->
                        ConversationListItem(
                            row = row,
                            now = now,
                            onClick = { onOpenConversation(row.id) },
                            onDelete = onDeleteConversation,
                        )
                    }
                }
            }
        }
    }
}

/**
 * Empty state shown when there are no conversations yet.
 */
@Composable
private fun EmptyChatListState(
    modifier: Modifier = Modifier,
    onNewMessage: () -> Unit,
) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        // Icon with gradient background
        Box(
            modifier = Modifier
                .size(120.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.05f),
                        ),
                    )
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.Message,
                contentDescription = null,
                modifier = Modifier.size(56.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "No conversations yet",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground,
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Start a new conversation or join the Nearby room to connect with people around you.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(24.dp))

        androidx.compose.material3.FilledTonalButton(
            onClick = onNewMessage,
            modifier = Modifier.fillMaxWidth(0.7f),
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.Message,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Start a conversation",
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}

/**
 * Placeholder rows shown while the conversation list is still loading.
 */
@Composable
private fun ChatListSkeleton(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "chatListSkeleton")
    val alpha by transition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 700, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "chatListSkeletonAlpha",
    )
    Column(modifier = modifier.padding(vertical = 8.dp, horizontal = 12.dp)) {
        repeat(SKELETON_ROW_COUNT) { ConversationSkeletonRow(pulseAlpha = alpha) }
    }
}

private const val SKELETON_ROW_COUNT = 6

@Composable
private fun ConversationSkeletonRow(pulseAlpha: Float) {
    val blockColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f * pulseAlpha + 0.04f)
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(blockColor),
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Box(
                    Modifier
                        .fillMaxWidth(0.45f)
                        .height(14.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(blockColor),
                )
                Spacer(Modifier.height(8.dp))
                Box(
                    Modifier
                        .fillMaxWidth(0.75f)
                        .height(12.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(blockColor),
                )
            }
        }
    }
}

private fun openRadioSettings(
    context: Context,
    warning: RadioWarning,
) {
    val action = when (warning) {
        RadioWarning.BluetoothOff -> Settings.ACTION_BLUETOOTH_SETTINGS
        RadioWarning.WifiOff -> Settings.ACTION_WIFI_SETTINGS
        RadioWarning.AllRadiosOff -> {
            if (isAirplaneModeOn(context)) {
                Settings.ACTION_AIRPLANE_MODE_SETTINGS
            } else {
                Settings.ACTION_WIRELESS_SETTINGS
            }
        }
    }
    runCatching { context.startActivity(Intent(action)) }
}

private fun isAirplaneModeOn(context: Context): Boolean =
    Settings.Global.getInt(context.contentResolver, Settings.Global.AIRPLANE_MODE_ON, 0) != 0

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ConversationListItem(
    row: ConversationRow,
    now: Long,
    onClick: () -> Unit,
    onDelete: (conversationId: String) -> Unit = {},
) {
    val deletable = !row.isRoom
    var menuOpen by remember { mutableStateOf(false) }
    var showConfirm by remember { mutableStateOf(false) }
    val clickModifier = if (deletable) {
        Modifier.combinedClickable(onClick = onClick, onLongClick = { menuOpen = true })
    } else {
        Modifier.clickable(onClick = onClick)
    }

    val preview = row.lastPreview ?: stringResource(R.string.chat_list_empty_preview)
    val spokenTime = row.lastMessageAt?.let {
        DateUtils.getRelativeTimeSpanString(it, now, DateUtils.MINUTE_IN_MILLIS).toString()
    }
    val spokenUnread = if (row.unreadCount > 0) {
        pluralStringResource(R.plurals.chat_list_unread_count, row.unreadCount, row.unreadCount)
    } else {
        null
    }
    val spokenStatus = row.lastStatus?.let { deliveryLabel(it, row.lastDeliveredVia, mine = true).resolve() }
    val rowDescription = listOfNotNull(row.title, preview, spokenTime, spokenStatus, spokenUnread).joinToString(", ")
    val deleteLabel = stringResource(R.string.chat_list_delete_action)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(clickModifier)
            .semantics {
                testTag = "chat_row_${row.id}"
                contentDescription = rowDescription
                role = Role.Button
                onClick {
                    onClick()
                    true
                }
                if (deletable) {
                    customActions = listOf(
                        CustomAccessibilityAction(deleteLabel) {
                            showConfirm = true
                            true
                        },
                    )
                }
            },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (row.unreadCount > 0) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            } else {
                MaterialTheme.colorScheme.surface
            },
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (row.unreadCount > 0) 2.dp else 0.dp,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LeadingVisual(row)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = row.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = if (row.unreadCount > 0) FontWeight.SemiBold else FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = preview,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = if (row.unreadCount > 0) FontWeight.Medium else FontWeight.Normal,
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(horizontalAlignment = Alignment.End) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    row.lastStatus?.let { status ->
                        Icon(
                            imageVector = deliveryIcon(status),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(14.dp),
                        )
                    }
                    row.lastMessageAt?.let { sentAt ->
                        Text(
                            text = compactTimeAgo(sentAt, now),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (row.unreadCount > 0) {
                    Spacer(Modifier.height(4.dp))
                    Badge(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ) {
                        Text(row.unreadCount.toString())
                    }
                }
            }
        }
    }

    if (deletable) {
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            DropdownMenuItem(
                text = {
                    Text(
                        text = stringResource(R.string.chat_list_delete_action),
                        color = MaterialTheme.colorScheme.error,
                    )
                },
                leadingIcon = {
                    Icon(
                        Icons.Filled.Delete,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                    )
                },
                onClick = {
                    menuOpen = false
                    showConfirm = true
                },
            )
        }
    }

    if (showConfirm) {
        AlertDialog(
            onDismissRequest = { showConfirm = false },
            title = { Text(stringResource(R.string.chat_list_delete_confirm_title)) },
            text = { Text(stringResource(R.string.chat_list_delete_confirm_body)) },
            confirmButton = {
                TextButton(onClick = {
                    onDelete(row.id)
                    showConfirm = false
                }) {
                    Text(
                        text = stringResource(R.string.chat_list_delete_action),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirm = false }) {
                    Text(stringResource(android.R.string.cancel))
                }
            },
        )
    }
}

/**
 * The circular leading glyph with gradient fallback.
 */
@Composable
private fun LeadingVisual(row: ConversationRow) {
    val size = 52.dp
    val groupPhoto = row.avatarHash
    when {
        row.isRoom -> {
            RoomAvatar(size = size)
        }
        row.isGroup && groupPhoto != null -> {
            AsyncImage(
                model = BlobImage(groupPhoto),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(size)
                    .clip(CircleShape),
            )
        }
        row.isGroup -> {
            GradientAvatar(
                name = row.title,
                size = size,
            )
        }
        else -> {
            Avatar(
                avatarHash = row.avatarHash,
                name = row.title,
                size = size,
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun ConversationListItemDmPreview() = SwarSetuPreview {
    ConversationListItem(
        row = ConversationRow(
            id = "dm-1",
            title = "Ada Lovelace",
            avatarHash = null,
            isRoom = false,
            isGroup = false,
            lastPreview = "See you at the meetup tonight!",
            lastMessageAt = PREVIEW_NOW - 5 * 60_000L,
            unreadCount = 2,
        ),
        now = PREVIEW_NOW,
        onClick = {},
    )
}

@Preview(showBackground = true)
@Composable
fun ConversationListItemDeliveredPreview() = SwarSetuPreview {
    ConversationListItem(
        row = ConversationRow(
            id = "dm-2",
            title = "Grace Hopper",
            avatarHash = null,
            isRoom = false,
            isGroup = false,
            lastPreview = "You: on my way",
            lastMessageAt = PREVIEW_NOW - 2 * 60_000L,
            unreadCount = 0,
            lastStatus = DeliveryStatus.Delivered,
            lastDeliveredVia = DeliveryPlane.Nearby,
        ),
        now = PREVIEW_NOW,
        onClick = {},
    )
}

@Preview(showBackground = true)
@Composable
fun ConversationListItemRoomPreview() = SwarSetuPreview {
    ConversationListItem(
        row = ConversationRow(
            id = "room",
            title = "Nearby",
            avatarHash = null,
            isRoom = true,
            isGroup = false,
            lastPreview = null,
            lastMessageAt = null,
            unreadCount = 0,
        ),
        now = PREVIEW_NOW,
        onClick = {},
    )
}

// Shared fixture rows for the full-screen previews.
private fun previewConversations(): List<ConversationRow> = listOf(
    ConversationRow(
        id = "room",
        title = "Nearby",
        avatarHash = null,
        isRoom = true,
        isGroup = false,
        lastPreview = "Anyone at the north gate?",
        lastMessageAt = PREVIEW_NOW - 3 * 60_000L,
        unreadCount = 0,
    ),
    ConversationRow(
        id = "group-1",
        title = "Hiking Crew",
        avatarHash = null,
        isRoom = false,
        isGroup = true,
        lastPreview = "Lena: bringing the trail map",
        lastMessageAt = PREVIEW_NOW - 60 * 60_000L,
        unreadCount = 0,
    ),
    ConversationRow(
        id = "dm-1",
        title = "Ada Lovelace",
        avatarHash = null,
        isRoom = false,
        isGroup = false,
        lastPreview = "See you at the meetup tonight!",
        lastMessageAt = PREVIEW_NOW - 5 * 60_000L,
        unreadCount = 2,
    ),
    ConversationRow(
        id = "dm-2",
        title = "Grace Hopper",
        avatarHash = null,
        isRoom = false,
        isGroup = false,
        lastPreview = "You: on my way",
        lastMessageAt = PREVIEW_NOW - 9 * 60_000L,
        unreadCount = 0,
        lastStatus = DeliveryStatus.Delivered,
        lastDeliveredVia = DeliveryPlane.Nearby,
    ),
)

@Preview(showBackground = true)
@Composable
fun ChatListScreenPopulatedPreview() = SwarSetuPreview {
    ChatListScreenContent(
        state = ChatListUiState(
            conversations = previewConversations(),
            neighborCount = 3,
            transportHealth = TransportHealth.Healthy,
            relayPlane = RelayPlane.Live,
        ),
        now = PREVIEW_NOW,
        onOpenConversation = {},
        onNewMessage = {},
        onOpenProfile = {},
        onOpenDiagnostics = {},
        onOpenBlockedUsers = {},
        onOpenMessageRequests = {},
        onOpenDonate = {},
        onOpenVerify = {},
        onShareApp = {},
        onOpenRadioSettings = {},
        onDismissRadioWarning = {},
        onDeleteConversation = {},
    )
}

@Preview(showBackground = true)
@Composable
fun ChatListScreenRadioWarningPreview() = SwarSetuPreview {
    ChatListScreenContent(
        state = ChatListUiState(
            conversations = previewConversations(),
            neighborCount = 1,
            transportHealth = TransportHealth.Degraded,
            radioWarning = RadioWarning.BluetoothOff,
        ),
        now = PREVIEW_NOW,
        onOpenConversation = {},
        onNewMessage = {},
        onOpenProfile = {},
        onOpenDiagnostics = {},
        onOpenBlockedUsers = {},
        onOpenMessageRequests = {},
        onOpenDonate = {},
        onOpenVerify = {},
        onShareApp = {},
        onOpenRadioSettings = {},
        onDismissRadioWarning = {},
        onDeleteConversation = {},
    )
}

@Preview(showBackground = true)
@Composable
fun ChatListScreenLoadingPreview() = SwarSetuPreview {
    ChatListScreenContent(
        state = ChatListUiState(isLoading = true),
        now = PREVIEW_NOW,
        onOpenConversation = {},
        onNewMessage = {},
        onOpenProfile = {},
        onOpenDiagnostics = {},
        onOpenBlockedUsers = {},
        onOpenMessageRequests = {},
        onOpenDonate = {},
        onOpenVerify = {},
        onShareApp = {},
        onOpenRadioSettings = {},
        onDismissRadioWarning = {},
        onDeleteConversation = {},
    )
}

@Preview(showBackground = true)
@Composable
fun ChatListScreenEmptyPreview() = SwarSetuPreview {
    ChatListScreenContent(
        state = ChatListUiState(conversations = emptyList()),
        now = PREVIEW_NOW,
        onOpenConversation = {},
        onNewMessage = {},
        onOpenProfile = {},
        onOpenDiagnostics = {},
        onOpenBlockedUsers = {},
        onOpenMessageRequests = {},
        onOpenDonate = {},
        onOpenVerify = {},
        onShareApp = {},
        onOpenRadioSettings = {},
        onDismissRadioWarning = {},
        onDeleteConversation = {},
    )
}
