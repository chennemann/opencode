package de.chennemann.opencode.mobile.ui.conversation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
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
import androidx.compose.ui.unit.dp
import de.chennemann.opencode.mobile.domain.session.ServerState
import de.chennemann.opencode.mobile.icons.ChevronDown
import de.chennemann.opencode.mobile.icons.ChevronUp
import de.chennemann.opencode.mobile.icons.Icons
import de.chennemann.opencode.mobile.ui.components.ConversationHeader
import de.chennemann.opencode.mobile.ui.components.DebugPanel
import de.chennemann.opencode.mobile.ui.components.MessageComposer
import de.chennemann.opencode.mobile.ui.components.ToolCallCard
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
        ConversationHeader(
            title = state.title,
            debugOpen = state.debugOpen,
            onToggleDebug = { onEvent(ConversationEvent.ToggleDebug) },
            onOpenManage = { onEvent(ConversationEvent.OpenManageTapped) },
        )

        DebugPanel(
            visible = state.debugOpen,
            debug = state.debug,
        )

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

                        val nextUser = state.focusedMessages
                            .subList(index + 1, state.focusedMessages.size)
                            .indexOfFirst { it.role == "user" }
                        val end = if (nextUser < 0) {
                            state.focusedMessages.lastIndex
                        } else {
                            index + nextUser
                        }
                        val calls = state.focusedMessages
                            .subList(index + 1, end + 1)
                            .flatMap { it.toolCalls }
                        if (calls.isNotEmpty()) {
                            val open = state.stepOpen[message.id] == true
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 4.dp)
                                    .animateContentSize(),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onEvent(ConversationEvent.ToggleSteps(message.id)) }
                                        .padding(horizontal = 8.dp, vertical = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Icon(
                                            if (open) Icons.ChevronUp else Icons.ChevronDown,
                                            "Toggle steps",
                                        )
                                        Text(if (open) "Hide steps" else "Show steps")
                                    }
                                    Text("${calls.size}")
                                }
                                AnimatedVisibility(
                                    visible = open,
                                    enter = expandVertically(
                                        expandFrom = Alignment.Top,
                                        animationSpec = tween(240),
                                    ) + fadeIn(animationSpec = tween(180)),
                                    exit = shrinkVertically(
                                        shrinkTowards = Alignment.Top,
                                        animationSpec = tween(240),
                                    ),
                                ) {
                                    Column(
                                        verticalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        calls.forEach { call ->
                                            ToolCallCard(
                                                call = call,
                                                expanded = state.callOpen[call.id] == true,
                                                onToggle = { onEvent(ConversationEvent.ToggleToolCall(call.id)) },
                                            )
                                        }
                                    }
                                }
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

        MessageComposer(
            draft = state.draft,
            connected = state.status is ServerState.Connected,
            onDraftChange = { onEvent(ConversationEvent.DraftChanged(it)) },
            onSend = { onEvent(ConversationEvent.SendTapped) },
            onReload = { onEvent(ConversationEvent.ReloadTapped) },
        )
    }
}
