package de.chennemann.opencode.mobile.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import de.chennemann.opencode.mobile.ui.conversation.ConversationScreen
import de.chennemann.opencode.mobile.ui.conversation.ConversationViewModel
import de.chennemann.opencode.mobile.ui.manage.ManageScreen
import de.chennemann.opencode.mobile.ui.manage.ManageViewModel
import org.koin.androidx.compose.koinViewModel

@Composable
fun AppNavHost() {
    val stack = rememberNavBackStack(ConversationRoute)
    NavDisplay(
        backStack = stack,
        onBack = { stack.removeLastOrNull() },
        entryProvider = { key ->
            when (key) {
                is ConversationRoute -> NavEntry(key) {
                    val model: ConversationViewModel = koinViewModel()
                    val state by model.state.collectAsStateWithLifecycle()
                    LaunchedEffect(model) {
                        model.nav.collect {
                            if (it is NavEvent.ToManage) {
                                stack.add(ManageRoute)
                            }
                        }
                    }
                    ConversationScreen(
                        state = state,
                        onEvent = model::onEvent,
                    )
                }

                is ManageRoute -> NavEntry(key) {
                    val model: ManageViewModel = koinViewModel()
                    val state by model.state.collectAsStateWithLifecycle()
                    LaunchedEffect(model) {
                        model.nav.collect {
                            if (it is NavEvent.Back) {
                                stack.removeLastOrNull()
                            }
                        }
                    }
                    ManageScreen(
                        state = state,
                        onEvent = model::onEvent,
                    )
                }

                else -> error("Unknown route: $key")
            }
        },
    )
}
