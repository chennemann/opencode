package de.chennemann.opencode.mobile.ui.conversation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.chennemann.opencode.mobile.domain.session.ServerState
import de.chennemann.opencode.mobile.domain.session.ToolCallState
import de.chennemann.opencode.mobile.icons.ChevronDown
import de.chennemann.opencode.mobile.icons.ChevronUp
import de.chennemann.opencode.mobile.icons.DoubleChevronDown
import de.chennemann.opencode.mobile.icons.Icons
import de.chennemann.opencode.mobile.ui.components.ConversationHeader
import de.chennemann.opencode.mobile.ui.components.MessageComposer
import de.chennemann.opencode.mobile.ui.components.ToolCallCard
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun ConversationScreen(state: ConversationUiState, onEvent: (ConversationEvent) -> Unit) {
    val list = rememberLazyListState()
    val dragging by list.interactionSource.collectIsDraggedAsState()
    val scope = rememberCoroutineScope()
    var follow by remember(state.title) { mutableStateOf(true) }
    var viewportTop by remember { mutableIntStateOf(0) }
    var viewportBottom by remember { mutableIntStateOf(0) }
    val tools = remember { mutableStateMapOf<String, ToolPosition>() }
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
    val nextUser = {
        val start = (current() + 1).coerceAtLeast(0)
        turns.indices
            .drop(start)
            .firstOrNull { turns[it].userText != null }
    }

    LaunchedEffect(list, turns.size, offset) {
        snapshotFlow { dragging to isAtEnd(list, turns.size + offset) }
            .distinctUntilChanged()
            .collect {
                if (it.first && !it.second) {
                    follow = false
                }
            }
    }

    LaunchedEffect(state.scroll) {
        follow = true
    }

    LaunchedEffect(list, turns.size, offset) {
        snapshotFlow { isAtEnd(list, turns.size + offset) }
            .distinctUntilChanged()
            .collect {
                if (it) {
                    follow = true
                }
            }
    }

    LaunchedEffect(
        state.title,
        turns.size,
        turns.lastOrNull()?.systemTexts?.lastOrNull()?.length,
        turns.lastOrNull()?.toolCalls?.size,
        turns.lastOrNull()?.toolCalls?.sumOf { it.details.size },
        turns.lastOrNull()?.activeTool?.id,
        turns.lastOrNull()?.activeTool?.status,
        turns.lastOrNull()?.activeTool?.details?.size,
        turns.lastOrNull()?.userText?.length,
        state.stepOpen[turns.lastOrNull()?.id],
        state.callOpen,
        follow,
        offset,
    ) {
        if (!follow) return@LaunchedEffect
        val count = turns.size + offset
        if (count <= 0) return@LaunchedEffect
        ensureEndVisible(list, count - 1)
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

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .imePadding(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Box(modifier = Modifier.weight(1f)) {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier
                        .fillMaxSize()
                        .onGloballyPositioned {
                            viewportTop = it.positionInRoot().y.roundToInt()
                            viewportBottom = (it.positionInRoot().y + it.size.height).roundToInt()
                        },
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
                            onEnsureToolVisible = { toolId ->
                                follow = false
                                scope.launch {
                                    ensureToolVisible(
                                        list = list,
                                        toolId = toolId,
                                        tools = tools,
                                        viewportTop = viewportTop,
                                        viewportBottom = viewportBottom,
                                    )
                                }
                            },
                            onToolLayout = { toolId, top, bottom ->
                                tools[toolId] = ToolPosition(top = top, bottom = bottom)
                            },
                        )
                    }
                }

                NavigationButtons(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(bottom = 8.dp),
                    following = follow,
                    onPrevious = {
                        follow = false
                        val target = previous() ?: return@NavigationButtons
                        scope.launch {
                            list.scrollToItem(target + offset)
                        }
                    },
                    onNext = {
                        scope.launch {
                            if (!follow) {
                                val target = nextUser()
                                if (target != null) {
                                    list.animateScrollToItem(target + offset)
                                    if (isAtEnd(list, turns.size + offset)) {
                                        follow = true
                                    }
                                    return@launch
                                }
                            }
                            follow = true
                            val count = turns.size + offset
                            if (count <= 0) return@launch
                            ensureEndVisible(list, count - 1)
                        }
                    },
                )
            }

            MessageComposer(
                draft = state.draft,
                connected = state.status is ServerState.Connected,
                suggestions = state.slashSuggestions,
                commandOpen = state.commandOpen,
                onDraftChange = { onEvent(ConversationEvent.DraftChanged(it)) },
                onSend = { onEvent(ConversationEvent.SendTapped) },
                onReload = { onEvent(ConversationEvent.ReloadTapped) },
                onCommandSelect = { onEvent(ConversationEvent.SlashCommandSelected(it.name)) },
                onCommandToggle = { onEvent(ConversationEvent.CommandListToggled) },
                onCommandDismiss = { onEvent(ConversationEvent.CommandListDismissed) },
            )
        }
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
    onEnsureToolVisible: (String) -> Unit,
    onToolLayout: (String, Int, Int) -> Unit,
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

        if (turn.userText != null || turn.toolCalls.isNotEmpty() || turn.activeTool != null) {
            ToolCallsSection(
                calls = turn.toolCalls,
                activeTool = turn.activeTool,
                startedAt = turn.startedAt,
                completedAt = turn.completedAt,
                active = active,
                open = stepOpen,
                callOpen = callOpen,
                onToggleSteps = onToggleSteps,
                onToggleToolCall = onToggleToolCall,
                onEnsureToolVisible = onEnsureToolVisible,
                onToolLayout = onToolLayout,
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
    calls: List<ToolCallState>,
    activeTool: ToolCallState?,
    startedAt: Long?,
    completedAt: Long?,
    active: Boolean,
    open: Boolean,
    callOpen: Map<String, Boolean>,
    onToggleSteps: () -> Unit,
    onToggleToolCall: (String) -> Unit,
    onEnsureToolVisible: (String) -> Unit,
    onToolLayout: (String, Int, Int) -> Unit,
) {
    val duration = rememberTurnDuration(startedAt, completedAt, active)
    val count = calls.size + if (activeTool == null) 0 else 1
    val shown = if (open) {
        calls + listOfNotNull(activeTool)
    } else {
        listOfNotNull(activeTool)
    }
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
                Text("${if (open) "Hide steps" else "Show steps"} • $count")
            }
            if (duration != null) {
                Text(duration)
            }
        }
        AnimatedVisibility(
            visible = shown.isNotEmpty(),
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
                shown.forEach { call ->
                    Box(
                        modifier = Modifier.onGloballyPositioned {
                            val top = it.positionInRoot().y.roundToInt()
                            val bottom = (it.positionInRoot().y + it.size.height).roundToInt()
                            onToolLayout(call.id, top, bottom)
                        },
                    ) {
                        val expanded = callOpen[call.id] == true
                        ToolCallCard(
                            call = call,
                            expanded = expanded,
                            onToggle = {
                                onToggleToolCall(call.id)
                                if (!expanded) {
                                    onEnsureToolVisible(call.id)
                                }
                            },
                        )
                    }
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

private fun isAtEnd(list: androidx.compose.foundation.lazy.LazyListState, totalCount: Int): Boolean {
    if (totalCount <= 0) return true
    val info = list.layoutInfo
    val end = totalCount - 1
    val item = info.visibleItemsInfo.lastOrNull { it.index == end } ?: return false
    return item.offset + item.size <= info.viewportEndOffset + 8
}

private suspend fun ensureEndVisible(list: androidx.compose.foundation.lazy.LazyListState, index: Int) {
    repeat(10) {
        val info = list.layoutInfo
        val item = info.visibleItemsInfo.lastOrNull { it.index == index }
        if (item == null) {
            list.scrollToItem(index)
            delay(16)
            return@repeat
        }
        val overflow = item.offset + item.size - info.viewportEndOffset
        if (overflow <= 0) {
            return
        }
        list.animateScrollBy(overflow.toFloat())
        delay(16)
    }
}

private suspend fun ensureToolVisible(
    list: androidx.compose.foundation.lazy.LazyListState,
    toolId: String,
    tools: Map<String, ToolPosition>,
    viewportTop: Int,
    viewportBottom: Int,
) {
    if (viewportBottom <= viewportTop) return
    repeat(8) {
        delay(16)
        val tool = tools[toolId] ?: return
        val height = tool.bottom - tool.top
        val viewportHeight = viewportBottom - viewportTop
        val delta = when {
            height >= viewportHeight -> (tool.top - viewportTop).toFloat()
            tool.bottom > viewportBottom -> (tool.bottom - viewportBottom).toFloat()
            tool.top < viewportTop -> (tool.top - viewportTop).toFloat()
            else -> return
        }
        if (delta == 0f) return
        list.animateScrollBy(delta)
    }
}

private data class ToolPosition(
    val top: Int,
    val bottom: Int,
)

@Composable
private fun NavigationButtons(
    modifier: Modifier = Modifier,
    following: Boolean,
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
            Icon(if (following) Icons.DoubleChevronDown else Icons.ChevronDown, "Follow latest")
        }
    }
}
