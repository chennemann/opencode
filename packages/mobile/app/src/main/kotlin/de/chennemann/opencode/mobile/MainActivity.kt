package de.chennemann.opencode.mobile

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.dropUnlessResumed
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import de.chennemann.opencode.mobile.ui.theme.MobileTheme
import de.chennemann.opencode.mobile.home.HomeViewModel
import de.chennemann.opencode.mobile.home.ServerState
import kotlinx.serialization.Serializable
import org.koin.androidx.compose.koinViewModel

@Serializable
private data object HomeRoute : NavKey

@Serializable
private data class SessionRoute(val id: String, val title: String, val directory: String) : NavKey

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
                val backStack = rememberNavBackStack(HomeRoute)

                NavDisplay(
                    backStack = backStack,
                    onBack = { backStack.removeLastOrNull() },
                    entryProvider = { key ->
                        when (key) {
                            is HomeRoute -> NavEntry(key) {
                                val model: HomeViewModel = koinViewModel()
                                val state by model.state.collectAsStateWithLifecycle()
                                var field by remember { mutableStateOf(TextFieldValue(state.url)) }
                                LaunchedEffect(state.url) {
                                    if (state.url == field.text) return@LaunchedEffect
                                    field = TextFieldValue(state.url)
                                }
                                LaunchedEffect(state.opened?.id) {
                                    val opened = state.opened ?: return@LaunchedEffect
                                    backStack.add(SessionRoute(opened.id, opened.title, opened.directory))
                                    model.consumeOpened()
                                }
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .verticalScroll(rememberScrollState())
                                        .padding(24.dp),
                                    verticalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                    Text("OpenCode Android")
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
                                    when (val status = state.status) {
                                        ServerState.Idle -> SelectionContainer { Text("Waiting for server check...") }
                                        ServerState.Loading -> SelectionContainer { Text("Checking server...") }
                                        is ServerState.Connected -> {
                                            SelectionContainer { Text("Connected to OpenCode") }
                                            SelectionContainer { Text("Server version: ${status.version}") }
                                        }

                                        is ServerState.Failed -> SelectionContainer { Text("Connection failed: ${status.reason}") }
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
                                        }) {
                                            Text(if (state.loadingSessions) "Creating session..." else "New session")
                                        }
                                    }
                                    if (state.sessions.isNotEmpty()) {
                                        Text("Sessions")
                                        state.sessions.forEach { session ->
                                            Button(onClick = dropUnlessResumed {
                                                model.openSession(session.id)
                                                backStack.add(
                                                    SessionRoute(
                                                        id = session.id,
                                                        title = session.title,
                                                        directory = session.directory,
                                                    )
                                                )
                                            }) {
                                                Text("${session.title} (${session.version})")
                                            }
                                        }
                                    } else if (state.selectedProject != null && !state.loadingSessions) {
                                        SelectionContainer { Text("No sessions found for selected project") }
                                    }
                                    state.message?.let {
                                        SelectionContainer {
                                            Text(it)
                                        }
                                    }
                                    Button(onClick = dropUnlessResumed {
                                        model.createSession()
                                    }) {
                                        Text("Quick new session")
                                    }
                                }
                            }

                            is SessionRoute -> NavEntry(key) {
                                val model: HomeViewModel = koinViewModel()
                                val state by model.state.collectAsStateWithLifecycle()
                                LaunchedEffect(key.id, key.directory) {
                                    model.loadMessages(key.id, key.directory)
                                    model.startMessageStream(key.id, key.directory)
                                }
                                DisposableEffect(key.id, key.directory) {
                                    onDispose {
                                        model.stopMessageStream()
                                    }
                                }
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(24.dp),
                                    verticalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                    Text("Session")
                                    SelectionContainer { Text("Title: ${key.title}") }
                                    SelectionContainer { Text("ID: ${key.id}") }
                                    if (state.loadingMessages) {
                                        SelectionContainer { Text("Loading messages...") }
                                    }
                                    LazyColumn(
                                        verticalArrangement = Arrangement.spacedBy(12.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        items(state.messages, key = { it.id }) { message ->
                                            val user = message.role == "user"
                                            val label = if (user) "User" else "Server"
                                            val color = if (user) {
                                                MaterialTheme.colorScheme.primaryContainer
                                            } else {
                                                MaterialTheme.colorScheme.secondaryContainer
                                            }
                                            Card(
                                                colors = CardDefaults.cardColors(
                                                    containerColor = color,
                                                ),
                                            ) {
                                                Column(
                                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                                    modifier = Modifier.padding(12.dp),
                                                ) {
                                                    Text(label)
                                                    SelectionContainer {
                                                        Text(message.text)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    Button(onClick = dropUnlessResumed {
                                        backStack.removeLastOrNull()
                                    }) {
                                        Text("Back")
                                    }
                                }
                            }

                            else -> error("Unknown route: $key")
                        }
                    }
                )
            }
        }
    }
}
