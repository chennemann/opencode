package de.chennemann.opencode.mobile.ui.conversation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.chennemann.opencode.mobile.home.ServerState
import de.chennemann.opencode.mobile.icons.Adb
import de.chennemann.opencode.mobile.icons.ChevronDown
import de.chennemann.opencode.mobile.icons.ChevronUp
import de.chennemann.opencode.mobile.icons.Icons
import de.chennemann.opencode.mobile.icons.Send
import de.chennemann.opencode.mobile.icons.Settings
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

@Composable
fun ConversationScreen(state: ConversationUiState, onEvent: (ConversationEvent) -> Unit) {
    val list = rememberLazyListState()
    val dragging by list.interactionSource.collectIsDraggedAsState()
    val scope = rememberCoroutineScope()
    var follow by remember(state.title) { mutableStateOf(true) }
    val offset = if (state.canLoadMoreMessages || state.loadingMoreMessages) 1 else 0
    val isTool = { text: String, role: String ->
        role != "user" && text.startsWith("[") && text.endsWith("]")
    }
    val current = {
        (list.firstVisibleItemIndex - offset)
            .coerceIn(-1, state.focusedMessages.lastIndex)
    }
    val previous = {
        (current() - 1 downTo 0)
            .firstOrNull { index ->
                !isTool(
                    state.focusedMessages[index].text,
                    state.focusedMessages[index].role,
                )
            }
    }
    val next = {
        (current() + 1..state.focusedMessages.lastIndex)
            .firstOrNull { index ->
                !isTool(
                    state.focusedMessages[index].text,
                    state.focusedMessages[index].role,
                )
            }
    }

    LaunchedEffect(list) {
        snapshotFlow { list.isScrollInProgress }
            .map { !it }
            .filter { it }
            .distinctUntilChanged()
            .collect {
                follow = !list.canScrollForward
            }
    }

    LaunchedEffect(dragging) {
        if (dragging) follow = false
    }

    LaunchedEffect(
        state.title,
        state.focusedMessages.size,
        state.focusedMessages.lastOrNull()?.text?.length,
        follow,
        offset,
    ) {
        if (!follow) return@LaunchedEffect
        val count = state.focusedMessages.size + offset
        if (count <= 0) return@LaunchedEffect
        list.scrollToItem(count - 1)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SelectionContainer(modifier = Modifier.weight(1f)) {
                Text(
                    state.title,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Row(
                modifier = Modifier.width(96.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                IconButton(
                    onClick = { onEvent(ConversationEvent.ToggleDebug) },
                    colors = IconButtonDefaults.iconButtonColors(
                        contentColor = MaterialTheme.colorScheme.primary,
                    ),
                ) {
                    val label = if (state.debugOpen) "Hide debug panel" else "Show debug panel"
                    Icon(Icons.Adb, label)
                }
                IconButton(
                    onClick = { onEvent(ConversationEvent.OpenManageTapped) },
                    colors = IconButtonDefaults.iconButtonColors(
                        contentColor = MaterialTheme.colorScheme.primary,
                    ),
                ) {
                    Icon(Icons.Settings, "Open settings")
                }
            }
        }

        AnimatedVisibility(
            visible = state.debugOpen,
            enter = slideInVertically(initialOffsetY = { -it / 2 }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { -it / 2 }) + fadeOut(),
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                ),
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    SelectionContainer {
                        Text(
                            "SSE raw=${state.debug.sseRaw} seen=${state.debug.sseSeen} applied=${state.debug.sseApplied} dropped=${state.debug.sseDropped} connected=${state.debug.sseConnected} errors=${state.debug.sseErrors} sync=${state.debug.syncRuns}/${state.debug.syncFails}",
                        )
                    }
                    state.debug.lastDrop?.let {
                        SelectionContainer { Text("Last drop: $it") }
                    }
                    state.debug.lastStreamError?.let {
                        SelectionContainer { Text("Last stream error: $it") }
                    }
                }
            }
        }

        Box(modifier = Modifier.weight(1f)) {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize(),
                state = list,
            ) {
                if (state.canLoadMoreMessages || state.loadingMoreMessages) {
                    item("load-more") {
                        Button(
                            onClick = { onEvent(ConversationEvent.LoadMoreMessagesTapped) },
                            enabled = !state.loadingMoreMessages,
                        ) {
                            val label = if (state.loadingMoreMessages) {
                                "Loading older messages..."
                            } else {
                                "Load older messages"
                            }
                            Text(label)
                        }
                    }
                }
                itemsIndexed(state.focusedMessages, key = { _, it -> it.id }) { index, message ->
                    val user = message.role == "user"
                    if (user) {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                            ),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            SelectionContainer {
                                Text(
                                    message.text,
                                    modifier = Modifier.padding(12.dp),
                                )
                            }
                        }
                        return@itemsIndexed
                    }

                    val showText = message.text.isNotBlank() && message.text != "(streaming...)"
                    if (showText) {
                        SelectionContainer {
                            Text(
                                message.text,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            }

            Row(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(bottom = 8.dp),
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    SmallFloatingActionButton(
                        onClick = {
                            val target = previous() ?: return@SmallFloatingActionButton
                            scope.launch {
                                list.scrollToItem(target + offset)
                            }
                        },
                    ) {
                        Icon(Icons.ChevronUp, "Previous message")
                    }
                    SmallFloatingActionButton(
                        onClick = {
                            scope.launch {
                                val target = next()
                                if (target != null) {
                                    follow = false
                                    list.scrollToItem(target + offset)
                                    return@launch
                                }
                                val count = state.focusedMessages.size + offset
                                if (count <= 0) return@launch
                                follow = true
                                list.scrollToItem(count - 1)
                            }
                        },
                    ) {
                        Icon(Icons.ChevronDown, "Next message")
                    }
                }
            }
        }

        val connected = state.status is ServerState.Connected
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            TextField(
                value = state.draft,
                onValueChange = { onEvent(ConversationEvent.DraftChanged(it)) },
                modifier = Modifier.weight(1f),
                label = { Text("Message") },
            )
            if (connected) {
                IconButton(
                    onClick = { onEvent(ConversationEvent.SendTapped) },
                    colors = IconButtonDefaults.iconButtonColors(
                        contentColor = MaterialTheme.colorScheme.primary,
                    ),
                    modifier = Modifier.align(Alignment.Bottom),
                ) {
                    Icon(Icons.Send, "")
                }
            } else {
                Button(
                    onClick = { onEvent(ConversationEvent.ReloadTapped) },
                    modifier = Modifier.align(Alignment.Bottom),
                ) {
                    Text("Reload")
                }
            }
        }
    }
}
