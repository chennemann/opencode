package de.chennemann.opencode.mobile.ui.components

import android.os.SystemClock
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TextButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.SheetValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupProperties
import de.chennemann.opencode.mobile.domain.session.CommandState
import de.chennemann.opencode.mobile.domain.session.SessionState
import de.chennemann.opencode.mobile.icons.Icons
import de.chennemann.opencode.mobile.icons.Add
import de.chennemann.opencode.mobile.icons.CircleSlash
import de.chennemann.opencode.mobile.icons.Pin
import de.chennemann.opencode.mobile.icons.PinOutline
import de.chennemann.opencode.mobile.icons.Send
import de.chennemann.opencode.mobile.ui.conversation.QuickSwitchMenuState
import de.chennemann.opencode.mobile.ui.conversation.QuickSwitchState
import de.chennemann.opencode.mobile.ui.conversation.ConversationMode
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.launch

private val ModeSwipeThreshold = 28.dp

private fun cycleMode(mode: ConversationMode): ConversationMode {
    return when (mode) {
        ConversationMode.PLAN -> ConversationMode.BUILD
        ConversationMode.BUILD -> ConversationMode.PLAN
    }
}

@Composable
fun MessageComposer(
    draft: String,
    mode: ConversationMode,
    connected: Boolean,
    suggestions: List<CommandState>,
    quickSwitches: List<QuickSwitchState>,
    quickSwitchMenu: QuickSwitchMenuState?,
    onDraftChange: (String) -> Unit,
    onModeChange: (ConversationMode) -> Unit,
    onSend: () -> Unit,
    onReload: () -> Unit,
    onCommandSelect: (CommandState) -> Unit,
    onQuickSwitch: (String) -> Unit,
    onQuickSwitchLongPress: (String) -> Unit,
    onQuickSwitchDismiss: () -> Unit,
    onQuickSwitchSession: (SessionState) -> Unit,
    onQuickSwitchPin: (SessionState, Boolean) -> Unit,
    onQuickSwitchArchive: (SessionState) -> Unit,
    onQuickSwitchLoadMore: () -> Unit,
    onQuickSwitchCreate: () -> Unit,
) {
    var commandOpen by remember { mutableStateOf(false) }
    var dismissedAt by remember { mutableLongStateOf(0L) }
    var field by remember { mutableStateOf(TextFieldValue(draft)) }
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current

    LaunchedEffect(draft) {
        if (draft == field.text) return@LaunchedEffect
        field = TextFieldValue(
            text = draft,
            selection = TextRange(draft.length),
        )
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        Text(
            text = if (mode == ConversationMode.PLAN) "Plan" else "Build",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(0.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            Box(modifier = Modifier.weight(1f)) {
                TextField(
                    value = field,
                    onValueChange = {
                        field = it
                        onDraftChange(it.text)
                        commandOpen = commandOpen && (it.text.isBlank() || it.text.startsWith("/"))
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .pointerInput(mode) {
                            val threshold = ModeSwipeThreshold.toPx()
                            var delta = 0f
                            var changed = false
                            detectHorizontalDragGestures(
                                onDragStart = {
                                    delta = 0f
                                    changed = false
                                },
                                onHorizontalDrag = { _, dragAmount ->
                                    if (changed) return@detectHorizontalDragGestures
                                    delta += dragAmount
                                    if (delta > -threshold && delta < threshold) return@detectHorizontalDragGestures
                                    changed = true
                                    onModeChange(cycleMode(mode))
                                },
                            )
                        },
                    placeholder = { Text("Message") },
                    maxLines = 12,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        disabledContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        disabledIndicatorColor = Color.Transparent,
                    ),
                )

                if (draft.isEmpty()) {
                    DropdownMenu(
                        expanded = commandOpen && suggestions.isNotEmpty(),
                        onDismissRequest = {
                            commandOpen = false
                            dismissedAt = SystemClock.elapsedRealtime()
                        },
                        properties = PopupProperties(focusable = false),
                        modifier = Modifier
                            .fillMaxWidth(0.95f)
                            .heightIn(max = 280.dp),
                    ) {
                        suggestions.forEach { command ->
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text(
                                            text = "/${command.name}",
                                            style = MaterialTheme.typography.labelLarge,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        if (!command.description.isNullOrBlank()) {
                                            Text(
                                                text = command.description,
                                                style = MaterialTheme.typography.bodySmall,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                        }
                                    }
                                },
                                onClick = {
                                    onCommandSelect(command)
                                    commandOpen = false
                                },
                            )
                        }
                    }

                    Box(
                        Modifier
                            .align(Alignment.BottomEnd)
                            .padding(bottom = 4.dp)
                    ) {
                        IconButton(
                            onClick = {
                                val now = SystemClock.elapsedRealtime()
                                if (commandOpen) {
                                    commandOpen = false
                                    dismissedAt = now
                                    return@IconButton
                                }
                                if (now - dismissedAt < CommandReopenDelayMs) {
                                    return@IconButton
                                }
                                commandOpen = true
                            },
                            modifier = Modifier
                                .focusProperties { canFocus = false }
                                .size(48.dp),
                            colors = IconButtonDefaults.iconButtonColors(
                                contentColor = MaterialTheme.colorScheme.primary,
                            ),
                        ) {
                            Icon(
                                imageVector = Icons.CircleSlash,
                                contentDescription = "Toggle command suggestions",
                            )
                        }
                    }
                }
            }

            if (connected) {
                Box(
                    Modifier
                        .align(Alignment.Bottom)
                        .padding(bottom = 4.dp)
                ) {
                    IconButton(
                        onClick = {
                            if (draft != field.text) {
                                onDraftChange(field.text)
                            }
                            onSend()
                            if (field.text.isNotBlank()) {
                                commandOpen = false
                                focus.clearFocus()
                                keyboard?.hide()
                            }
                        },
                        colors = IconButtonDefaults.iconButtonColors(
                            contentColor = MaterialTheme.colorScheme.primary,
                        ),
                        modifier = Modifier.size(48.dp),
                    ) {
                        Icon(Icons.Send, "")
                    }
                }
            } else {
                Button(
                    onClick = onReload,
                    modifier = Modifier.align(Alignment.Bottom),
                ) {
                    Text("Reload")
                }
            }
        }


        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (quickSwitches.isNotEmpty()) {
                LazyRow(
                    modifier = Modifier.padding(top = 8.dp, bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    items(quickSwitches, key = { it.key }) { item ->
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            QuickSwitchButton(
                                item = item,
                                onClick = { onQuickSwitch(item.key) },
                                onLongPress = { onQuickSwitchLongPress(item.key) },
                            )
                            Box(
                                modifier = Modifier.height(8.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (item.unread > 0) {
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        repeat(minOf(item.unread, 6)) {
                                            Box(
                                                modifier = Modifier
                                                    .size(5.dp)
                                                    .background(
                                                        color = MaterialTheme.colorScheme.primary,
                                                        shape = CircleShape,
                                                    )
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    quickSwitchMenu?.let { menu ->
        QuickSwitchPanel(
            menu = menu,
            onDismiss = onQuickSwitchDismiss,
            onQuickSwitchSession = onQuickSwitchSession,
            onQuickSwitchPin = onQuickSwitchPin,
            onQuickSwitchArchive = onQuickSwitchArchive,
            onQuickSwitchLoadMore = onQuickSwitchLoadMore,
            onQuickSwitchCreate = onQuickSwitchCreate,
        )
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun QuickSwitchPanel(
    menu: QuickSwitchMenuState,
    onDismiss: () -> Unit,
    onQuickSwitchSession: (SessionState) -> Unit,
    onQuickSwitchPin: (SessionState, Boolean) -> Unit,
    onQuickSwitchArchive: (SessionState) -> Unit,
    onQuickSwitchLoadMore: () -> Unit,
    onQuickSwitchCreate: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    val rows = menu.sessions
        .sortedWith(
            compareByDescending<SessionState> { menu.pinned.contains(it.id) }
                .thenByDescending { it.updatedAt ?: 0L }
                .thenByDescending { it.id }
        )
    val flingGuard = remember {
        object : NestedScrollConnection {
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (source == NestedScrollSource.SideEffect && available.y < 0f) return available
                return Offset.Zero
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                return Velocity.Zero
            }
        }
    }
    LaunchedEffect(sheet.currentValue, sheet.targetValue) {
        if (sheet.currentValue == SheetValue.Expanded && sheet.targetValue == SheetValue.PartiallyExpanded) {
            scope.launch {
                sheet.hide()
            }
        }
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheet,
        sheetMaxWidth = Dp.Unspecified,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.9f)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = folderName(menu.project),
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                IconButton(
                    onClick = onQuickSwitchCreate,
                    colors = IconButtonDefaults.iconButtonColors(
                        contentColor = MaterialTheme.colorScheme.primary,
                    ),
                ) {
                    Icon(
                        imageVector = Icons.Add,
                        contentDescription = "New session",
                    )
                }
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .nestedScroll(flingGuard),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (menu.loading && rows.isEmpty()) {
                    item {
                        Text(
                            text = "Loading sessions...",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                if (menu.sessions.isEmpty() && !menu.loading) {
                    item {
                        Text(
                            text = "No sessions found",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                items(rows, key = { it.id }) { session ->
                    val pinned = menu.pinned.contains(session.id)
                    val systemPinned = menu.systemPinned.contains(session.id)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onQuickSwitchSession(session) }
                            .padding(vertical = 2.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = session.title,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = quickSwitchSessionSubtitle(session, folderName(menu.project), menu.worktree),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        TextButton(onClick = { onQuickSwitchArchive(session) }) {
                            Text("Archive")
                        }
                        IconButton(onClick = { onQuickSwitchPin(session, systemPinned) }) {
                            Icon(
                                imageVector = if (pinned) Icons.Pin else Icons.PinOutline,
                                contentDescription = if (pinned) {
                                    "Remove from quick switch"
                                } else {
                                    "Pin in quick switch"
                                },
                                tint = if (pinned) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            )
                        }
                    }
                }

                if (menu.canLoadMore) {
                    item {
                        TextButton(
                            onClick = onQuickSwitchLoadMore,
                            enabled = !menu.loading,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(if (menu.loading) "Loading..." else "Load more")
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun QuickSwitchButton(
    item: QuickSwitchState,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
) {
    val container = if (item.active) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    val content = if (item.active) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Box(
        modifier = Modifier.size(44.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (item.processing) {
            CircularProgressIndicator(
                modifier = Modifier.size(44.dp),
                strokeWidth = 2.dp,
            )
        }
        Surface(
            shape = CircleShape,
            color = container,
            contentColor = content,
            modifier = Modifier
                .size(36.dp)
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = onLongPress,
                ),
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text(item.label)
            }
        }
    }
}

private fun quickSwitchSessionSubtitle(session: SessionState, project: String, worktree: String): String {
    val updated = session.updatedAt?.let {
        val value = Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault())
        "Updated ${QuickSwitchSessionFormatter.format(value)}"
    } ?: "Updated unknown"
    if (workspaceId(session.directory) == workspaceId(worktree)) return updated
    return "$updated | $project"
}

private fun folderName(path: String): String {
    val value = path.trim().trimEnd('/', '\\')
    if (value.isBlank()) return path
    val index = maxOf(value.lastIndexOf('/'), value.lastIndexOf('\\'))
    if (index < 0) return value
    val name = value.substring(index + 1)
    if (name.isBlank()) return value
    return name
}

private fun workspaceId(path: String): String {
    return path.trimEnd('/', '\\')
}

private const val CommandReopenDelayMs = 500L
private val QuickSwitchSessionFormatter = DateTimeFormatter.ofPattern("MMM d HH:mm")
