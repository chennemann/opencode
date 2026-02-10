package de.chennemann.opencode.mobile

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Alignment
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
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextOverflow
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.dropUnlessResumed
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import de.chennemann.opencode.mobile.home.ServerState
import de.chennemann.opencode.mobile.home.HomeViewModel
import de.chennemann.opencode.mobile.home.ToolCallState
import de.chennemann.opencode.mobile.icons.Adb
import de.chennemann.opencode.mobile.icons.ChevronDown
import de.chennemann.opencode.mobile.icons.ChevronUp
import de.chennemann.opencode.mobile.icons.Icons
import de.chennemann.opencode.mobile.icons.Send
import de.chennemann.opencode.mobile.icons.Settings
import de.chennemann.opencode.mobile.ui.theme.MobileTheme
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import org.koin.androidx.compose.koinViewModel

@Serializable
private data object ConversationRoute : NavKey

@Serializable
private data object ManageRoute : NavKey

class MainActivity : ComponentActivity() {
    private val requestInternet = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (checkSelfPermission(Manifest.permission.INTERNET) != PackageManager.PERMISSION_GRANTED) {
            requestInternet.launch(Manifest.permission.INTERNET)
        }
        enableEdgeToEdge()
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).run {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        setContent {
            MobileTheme {
                val backStack = rememberNavBackStack(ConversationRoute)

                NavDisplay(
                    backStack = backStack,
                    onBack = { backStack.removeLastOrNull() },
                    entryProvider = { key ->
                        when (key) {
                            is ConversationRoute -> NavEntry(key) {
                                val model: HomeViewModel = koinViewModel()
                                val state by model.state.collectAsStateWithLifecycle()
                                val list = rememberLazyListState()
                                val dragging by list.interactionSource.collectIsDraggedAsState()
                                val scope = rememberCoroutineScope()
                                var follow by remember(state.focusedSession?.id) { mutableStateOf(true) }
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
                                    state.focusedSession?.id,
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
                                    var debugOpen by remember { mutableStateOf(false) }
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        SelectionContainer(modifier = Modifier.weight(1f)) {
                                            Text(
                                                state.focusedSession?.title ?: "No session selected",
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                        }
                                        Row(
                                            modifier = Modifier.width(96.dp),
                                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                                        ) {
                                            IconButton(
                                                onClick = { debugOpen = !debugOpen },
                                                colors = IconButtonDefaults.iconButtonColors(
                                                    contentColor = MaterialTheme.colorScheme.primary,
                                                ),
                                            ) {
                                                val label = if (debugOpen) "Hide debug panel" else "Show debug panel"
                                                Icon(Icons.Adb, label)
                                            }
                                            IconButton(
                                                onClick = dropUnlessResumed {
                                                    model.openManagement()
                                                    backStack.add(ManageRoute)
                                                },
                                                colors = IconButtonDefaults.iconButtonColors(
                                                    contentColor = MaterialTheme.colorScheme.primary,
                                                ),
                                            ) {
                                                Icon(Icons.Settings, "Open settings")
                                            }
                                        }
                                    }
                                    AnimatedVisibility(
                                        visible = debugOpen,
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
                                                        onClick = dropUnlessResumed {
                                                            model.loadMoreMessages()
                                                        },
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

                                                    val next = state.focusedMessages
                                                        .subList(index + 1, state.focusedMessages.size)
                                                        .indexOfFirst { it.role == "user" }
                                                    val end = if (next < 0) {
                                                        state.focusedMessages.lastIndex
                                                    } else {
                                                        index + next
                                                    }
                                                    val calls = state.focusedMessages
                                                        .subList(index + 1, end + 1)
                                                        .flatMap { it.toolCalls }
                                                    if (calls.isNotEmpty()) {
                                                        var open by remember(message.id) { mutableStateOf(false) }
                                                        val expanded = remember(message.id) { mutableStateMapOf<String, Boolean>() }
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
                                                                    .clickable { open = !open }
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
                                                                            expanded = expanded[call.id] == true,
                                                                            onToggle = {
                                                                                expanded[call.id] = expanded[call.id] != true
                                                                            },
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
                                    var draft by remember { mutableStateOf(TextFieldValue("")) }
                                    val connected = state.status is ServerState.Connected
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.Bottom,
                                    ) {
                                        TextField(
                                            value = draft,
                                            onValueChange = { draft = it },
                                            modifier = Modifier.weight(1f),
                                            label = { Text("Message") },
                                        )
                                        if (connected) {
                                            IconButton(
                                                onClick = dropUnlessResumed {
                                                    model.send(draft.text)
                                                    draft = TextFieldValue("")
                                                },
                                                colors = IconButtonDefaults.iconButtonColors(
                                                    contentColor = MaterialTheme.colorScheme.primary,
                                                ),
                                                modifier = Modifier.align(Alignment.Bottom),
                                            ) {
                                                Icon(Icons.Send, "")
                                            }
                                        } else {
                                            Button(
                                                onClick = dropUnlessResumed {
                                                    model.refresh()
                                                },
                                                modifier = Modifier.align(Alignment.Bottom),
                                            ) {
                                                Text("Reload")
                                            }
                                        }
                                    }
                                }
                            }

                            is ManageRoute -> NavEntry(key) {
                                val model: HomeViewModel = koinViewModel()
                                val state by model.state.collectAsStateWithLifecycle()
                                var field by remember { mutableStateOf(TextFieldValue(state.url)) }
                                LaunchedEffect(state.url) {
                                    if (state.url == field.text) return@LaunchedEffect
                                    field = TextFieldValue(state.url)
                                }
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .verticalScroll(rememberScrollState())
                                        .padding(24.dp),
                                    verticalArrangement = Arrangement.spacedBy(16.dp),
                                ) {
                                    Text("Manage Sessions")
                                    TextField(
                                        value = field,
                                        onValueChange = {
                                            field = it
                                            model.updateUrl(it.text)
                                        },
                                        label = { Text("Server URL") },
                                        singleLine = true,
                                    )
                                    if (state.discovered != null) {
                                        Button(onClick = dropUnlessResumed {
                                            model.useDiscovered()
                                        }) {
                                            Text("Use discovered: ${state.discovered}")
                                        }
                                    }
                                    Button(onClick = dropUnlessResumed {
                                        model.refresh()
                                    }) {
                                        Text("Connect")
                                    }
                                    Button(onClick = dropUnlessResumed {
                                        model.loadProjects()
                                    }) {
                                        Text(if (state.loadingProjects) "Loading projects..." else "Load projects")
                                    }
                                    if (state.projects.isNotEmpty()) {
                                        Text("Projects")
                                        state.projects.forEach { project ->
                                            Button(onClick = dropUnlessResumed {
                                                model.selectProject(project.worktree)
                                            }) {
                                                val marker = if (state.selectedProject == project.worktree) "* " else ""
                                                Text("$marker${project.name}")
                                            }
                                        }
                                    }
                                    if (state.selectedProject != null) {
                                        if (state.loadingSessions) {
                                            SelectionContainer { Text("Loading sessions...") }
                                        }
                                        Button(onClick = dropUnlessResumed {
                                            model.createSession()
                                            backStack.removeLastOrNull()
                                        }) {
                                            Text(if (state.loadingSessions) "Creating session..." else "New session")
                                        }
                                    }
                                    if (state.sessions.isNotEmpty()) {
                                        Text("Sessions")
                                        state.sessions.forEach { session ->
                                            Button(onClick = dropUnlessResumed {
                                                model.openSession(session)
                                                backStack.removeLastOrNull()
                                            }) {
                                                Text("${session.title} (${session.version})")
                                            }
                                        }
                                    } else if (state.selectedProject != null && !state.loadingSessions) {
                                        SelectionContainer { Text("No sessions found for selected project") }
                                    }
                                    Button(onClick = dropUnlessResumed {
                                        model.closeManagement()
                                        backStack.removeLastOrNull()
                                    }) {
                                        Text("Back")
                                    }
                                }
                            }

                            else -> error("Unknown route: $key")
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun ToolCallCard(call: ToolCallState, expanded: Boolean, onToggle: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggle),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(call.title)
                Text(call.status ?: "done")
            }
            if (expanded) {
                call.details.forEach { line ->
                    SelectionContainer { Text(line) }
                }
            }
        }
    }
}
