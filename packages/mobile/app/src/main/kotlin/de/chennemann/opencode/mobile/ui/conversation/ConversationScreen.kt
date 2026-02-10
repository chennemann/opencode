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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.chennemann.opencode.mobile.domain.session.ServerState
import de.chennemann.opencode.mobile.domain.session.ToolCallState
import de.chennemann.opencode.mobile.icons.ChevronDown
import de.chennemann.opencode.mobile.icons.ChevronUp
import de.chennemann.opencode.mobile.icons.Icons
import de.chennemann.opencode.mobile.ui.components.ConversationHeader
import de.chennemann.opencode.mobile.ui.components.MessageComposer
import de.chennemann.opencode.mobile.ui.components.ToolCallCard
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun ConversationScreen(state: ConversationUiState, onEvent: (ConversationEvent) -> Unit) {
    val list = rememberLazyListState()
    val dragging by list.interactionSource.collectIsDraggedAsState()
    val scope = rememberCoroutineScope()
    var follow by remember(state.title) { mutableStateOf(true) }
    val offset = if (state.canLoadMoreMessages || state.loadingMoreMessages) 1 else 0
    val turns = state.turns
    val current = {
        (list.firstVisibleItemIndex - offset)
            .coerceIn(-1, turns.lastIndex)
    }
    val previous = {
        (current() - 1).takeIf { it >= 0 }
    }
    val next = {
        (current() + 1).takeIf { it <= turns.lastIndex }
    }

    LaunchedEffect(dragging) {
        if (dragging) follow = false
    }

    LaunchedEffect(state.scroll) {
        follow = true
    }

    LaunchedEffect(
        state.title,
        turns.size,
        turns.lastOrNull()?.systemTexts?.lastOrNull()?.length,
        turns.lastOrNull()?.toolCalls?.size,
        turns.lastOrNull()?.userText?.length,
        follow,
        offset,
    ) {
        if (!follow) return@LaunchedEffect
        val count = turns.size + offset
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
            onOpenManage = { onEvent(ConversationEvent.OpenManageTapped) },
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
                itemsIndexed(turns, key = { _, it -> it.id }) { index, turn ->
                    ConversationTurnItem(
                        turn = turn,
                        active = index == turns.lastIndex,
                        stepOpen = state.stepOpen[turn.id] == true,
                        callOpen = state.callOpen,
                        onToggleSteps = { onEvent(ConversationEvent.ToggleSteps(turn.id)) },
                        onToggleToolCall = { onEvent(ConversationEvent.ToggleToolCall(it)) },
                    )
                }
            }

            NavigationButtons(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(bottom = 8.dp),
                onPrevious = {
                    follow = false
                    val target = previous() ?: return@NavigationButtons
                    scope.launch {
                        list.scrollToItem(target + offset)
                    }
                },
                onNext = {
                    follow = false
                    scope.launch {
                        val target = next()
                        if (target != null) {
                            list.scrollToItem(target + offset)
                            return@launch
                        }
                        val count = turns.size + offset
                        if (count <= 0) return@launch
                        list.scrollToItem(count - 1)
                    }
                },
            )
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

@Composable
private fun ConversationTurnItem(
    turn: ConversationTurnUiState,
    active: Boolean,
    stepOpen: Boolean,
    callOpen: Map<String, Boolean>,
    onToggleSteps: () -> Unit,
    onToggleToolCall: (String) -> Unit,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        turn.userText?.let {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                SelectionContainer {
                    Text(
                        it,
                        modifier = Modifier.padding(12.dp),
                    )
                }
            }
        }

        if (turn.toolCalls.isNotEmpty()) {
            ToolCallsSection(
                count = turn.toolCalls.size,
                calls = turn.toolCalls,
                startedAt = turn.startedAt,
                completedAt = turn.completedAt,
                active = active,
                open = stepOpen,
                callOpen = callOpen,
                onToggleSteps = onToggleSteps,
                onToggleToolCall = onToggleToolCall,
            )
        }

        turn.systemTexts.forEach {
            SelectionContainer {
                Text(
                    it,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun ToolCallsSection(
    count: Int,
    calls: List<ToolCallState>,
    startedAt: Long?,
    completedAt: Long?,
    active: Boolean,
    open: Boolean,
    callOpen: Map<String, Boolean>,
    onToggleSteps: () -> Unit,
    onToggleToolCall: (String) -> Unit,
) {
    val duration = rememberTurnDuration(startedAt, completedAt, active)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        androidx.compose.foundation.layout.Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggleSteps)
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            androidx.compose.foundation.layout.Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    if (open) Icons.ChevronUp else Icons.ChevronDown,
                    "Toggle steps",
                )
                Text(
                    if (duration == null) {
                        if (open) "Hide steps" else "Show steps"
                    } else {
                        if (open) "Hide steps - $duration" else "Show steps - $duration"
                    }
                )
            }
            Text("$count")
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
                        expanded = callOpen[call.id] == true,
                        onToggle = { onToggleToolCall(call.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun rememberTurnDuration(startedAt: Long?, completedAt: Long?, active: Boolean): String? {
    if (startedAt == null) return null
    if (completedAt != null) {
        return formatTurnDuration(startedAt, completedAt)
    }
    if (!active) return null
    var now by remember(startedAt, completedAt, active) { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(startedAt, completedAt, active) {
        while (active) {
            delay(1000)
            now = System.currentTimeMillis()
        }
    }
    return formatTurnDuration(startedAt, now)
}

private fun formatTurnDuration(startedAt: Long, completedAt: Long): String {
    val totalSeconds = ((completedAt - startedAt).coerceAtLeast(0L) / 1000L)
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    if (hours > 0) {
        return "${hours}h ${minutes.toString().padStart(2, '0')}m"
    }
    if (minutes > 0) {
        return "${minutes}m ${seconds.toString().padStart(2, '0')}s"
    }
    return "${seconds}s"
}

@Composable
private fun NavigationButtons(
    modifier: Modifier = Modifier,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SmallFloatingActionButton(onClick = onPrevious) {
            Icon(Icons.ChevronUp, "Previous message")
        }
        SmallFloatingActionButton(onClick = onNext) {
            Icon(Icons.ChevronDown, "Next message")
        }
    }
}
