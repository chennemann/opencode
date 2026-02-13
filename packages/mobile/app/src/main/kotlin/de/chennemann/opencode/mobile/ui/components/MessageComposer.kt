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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalIconButton
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
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupProperties
import de.chennemann.opencode.mobile.domain.session.CommandState
import de.chennemann.opencode.mobile.domain.session.SessionState
import de.chennemann.opencode.mobile.icons.Icons
import de.chennemann.opencode.mobile.icons.Add
import de.chennemann.opencode.mobile.icons.Pin
import de.chennemann.opencode.mobile.icons.PinOutline
import de.chennemann.opencode.mobile.icons.Send
import de.chennemann.opencode.mobile.ui.conversation.QuickSwitchMenuState
import de.chennemann.opencode.mobile.ui.conversation.QuickSwitchState
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.launch

@Composable
fun MessageComposer(
    draft: String,
    connected: Boolean,
    suggestions: List<CommandState>,
    quickSwitches: List<QuickSwitchState>,
    quickSwitchMenu: QuickSwitchMenuState?,
    onDraftChange: (String) -> Unit,
    onSend: () -> Unit,
    onReload: () -> Unit,
    onCommandSelect: (CommandState) -> Unit,
    onQuickSwitch: (String) -> Unit,
    onQuickSwitchLongPress: (String) -> Unit,
    onQuickSwitchDismiss: () -> Unit,
    onQuickSwitchSession: (SessionState) -> Unit,
    onQuickSwitchPin: (SessionState, Boolean) -> Unit,
    onQuickSwitchCreate: () -> Unit,
) {
    var commandOpen by remember { mutableStateOf(false) }
    var dismissedAt by remember { mutableLongStateOf(0L) }
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(0.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            Box(modifier = Modifier.weight(1f)) {
                TextField(
                    value = draft,
                    onValueChange = {
                        onDraftChange(it)
                        commandOpen = commandOpen && (it.isBlank() || it.startsWith("/"))
                    },
                    modifier = Modifier.fillMaxWidth(),
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
                        FilledTonalIconButton(
                            onClick = {
                                val now = SystemClock.elapsedRealtime()
                                if (commandOpen) {
                                    commandOpen = false
                                    dismissedAt = now
                                    return@FilledTonalIconButton
                                }
                                if (now - dismissedAt < CommandReopenDelayMs) {
                                    return@FilledTonalIconButton
                                }
                                commandOpen = true
                            },
                            modifier = Modifier
                                .focusProperties { canFocus = false },
                            colors = if (commandOpen) {
                                IconButtonDefaults.filledTonalIconButtonColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                )
                            } else {
                                IconButtonDefaults.filledTonalIconButtonColors()
                            },
                        ) {
                            Text("/")
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
                            onSend()
                            if (draft.isNotBlank()) {
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
                    modifier = Modifier.padding(top = 8.dp, bottom = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    items(quickSwitches, key = { it.key }) { item ->
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            QuickSwitchButton(
                                item = item,
                                onClick = { onQuickSwitch(item.key) },
                                onLongPress = { onQuickSwitchLongPress(item.key) },
                            )
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
    quickSwitchMenu?.let { menu ->
        QuickSwitchPanel(
            menu = menu,
            onDismiss = onQuickSwitchDismiss,
            onQuickSwitchSession = onQuickSwitchSession,
            onQuickSwitchPin = onQuickSwitchPin,
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
    onQuickSwitchCreate: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    val state = rememberLazyListState()
    val rows = menu.sessions.asReversed()
    val target = (if (menu.loading) 1 else 0) +
        (if (menu.sessions.isEmpty() && !menu.loading) 1 else 0) +
        rows.size
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
    LaunchedEffect(menu.key, rows.map { it.id }, menu.loading) {
        state.scrollToItem(target)
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
                    text = menu.project,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                TextButton(onClick = onDismiss) {
                    Text("Close")
                }
            }

            LazyColumn(
                state = state,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .nestedScroll(flingGuard),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (menu.loading) {
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
                                text = quickSwitchSessionSubtitle(session, menu.worktree),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
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

                item {
                    Button(
                        onClick = onQuickSwitchCreate,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(
                            imageVector = Icons.Add,
                            contentDescription = null,
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("New session")
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

private fun quickSwitchSessionSubtitle(session: SessionState, worktree: String): String {
    val updated = session.updatedAt?.let {
        val value = Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault())
        "Updated ${QuickSwitchSessionFormatter.format(value)}"
    } ?: "Updated unknown"
    if (workspaceId(session.directory) == workspaceId(worktree)) return updated
    return "$updated | Workspace ${folderName(session.directory)}"
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
