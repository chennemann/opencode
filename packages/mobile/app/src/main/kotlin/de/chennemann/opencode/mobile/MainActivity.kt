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
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
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
private data class DetailRoute(val id: String) : NavKey

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
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
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
                                        ServerState.Idle -> Text("Waiting for server check...")
                                        ServerState.Loading -> Text("Checking server...")
                                        is ServerState.Connected -> {
                                            Text("Connected to OpenCode")
                                            Text("Server version: ${status.version}")
                                        }

                                        is ServerState.Failed -> Text("Connection failed: ${status.reason}")
                                    }
                                    Button(onClick = dropUnlessResumed {
                                        model.refresh()
                                    }) {
                                        Text("Connect")
                                    }
                                    Button(onClick = dropUnlessResumed {
                                        backStack.add(DetailRoute("123"))
                                    }) {
                                        Text("Open details")
                                    }
                                }
                            }

                            is DetailRoute -> NavEntry(key) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(24.dp),
                                    verticalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                    Text("Detail id: ${key.id}")
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
