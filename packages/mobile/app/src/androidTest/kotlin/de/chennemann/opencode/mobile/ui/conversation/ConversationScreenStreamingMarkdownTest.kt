package de.chennemann.opencode.mobile.ui.conversation

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.chennemann.opencode.mobile.domain.session.ServerState
import de.chennemann.opencode.mobile.domain.session.ToolCallState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ConversationScreenStreamingMarkdownTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun renders_assistant_inline_code_while_hiding_backticks() {
        val state = mutableStateOf(ui(listOf("hello `code` world")))

        compose.setContent {
            ConversationScreen(
                state = state.value,
                onEvent = {},
            )
        }

        compose.onAllNodesWithText("hello code world").assertCountEquals(1)
        compose.onAllNodesWithText("hello `code` world").assertCountEquals(0)
    }

    @Test
    fun keeps_latest_streamed_text_visible_after_state_updates() {
        val state = mutableStateOf(ui(listOf("line 1")))

        compose.setContent {
            ConversationScreen(
                state = state.value,
                onEvent = {},
            )
        }

        compose.runOnIdle {
            state.value = ui(listOf("line 1", "line 2 `code`"))
        }

        compose.onAllNodesWithText("line 2 code").assertCountEquals(1)
        compose.onAllNodesWithText("line 2 `code`").assertCountEquals(0)
    }

    @Test
    fun renders_latest_streamed_markdown_chunk_after_many_updates() {
        val state = mutableStateOf(ui(listOf("chunk-0 `code-0`")))

        compose.setContent {
            ConversationScreen(
                state = state.value,
                onEvent = {},
            )
        }

        repeat(64) { index ->
            val text = "chunk-$index `code-$index`"
            compose.runOnIdle {
                state.value = ui(listOf(text))
            }
        }
        compose.waitForIdle()
        compose.onAllNodesWithText("chunk-63 code-63").assertCountEquals(1)
        compose.onAllNodesWithText("chunk-63 `code-63`").assertCountEquals(0)
    }

    @Test
    fun renders_long_turn_text_without_showing_markdown_ticks() {
        val long = (1..220).joinToString(" ") { "segment-$it" } + " `tail-code`"
        val state = mutableStateOf(ui(listOf(long)))

        compose.setContent {
            ConversationScreen(
                state = state.value,
                onEvent = {},
            )
        }

        val expected = (1..220).joinToString(" ") { "segment-$it" } + " tail-code"
        compose.onAllNodesWithText(expected).assertCountEquals(1)
        compose.onAllNodesWithText(long).assertCountEquals(0)
    }

    @Test
    fun keeps_tool_call_expansion_state_across_ui_updates() {
        val state = mutableStateOf(
            ui(
                turns = listOf(
                    ConversationTurnUiState(
                        id = "turn-1",
                        userText = "Run tools",
                        toolCalls = listOf(
                            ToolCallState(
                                id = "call-1",
                                title = "Read",
                                details = listOf("detail one"),
                            ),
                            ToolCallState(
                                id = "call-2",
                                title = "Write",
                                details = listOf("detail two"),
                            ),
                        ),
                        systemTexts = emptyList(),
                    ),
                ),
                stepOpen = mapOf("turn-1" to true),
                callOpen = mapOf("call-1" to true),
            ),
        )

        compose.setContent {
            ConversationScreen(
                state = state.value,
                onEvent = {},
            )
        }

        compose.onAllNodesWithText("detail one").assertCountEquals(1)
        compose.onAllNodesWithText("detail two").assertCountEquals(0)

        compose.runOnIdle {
            state.value = state.value.copy(callOpen = mapOf("call-2" to true))
        }

        compose.onAllNodesWithText("detail one").assertCountEquals(0)
        compose.onAllNodesWithText("detail two").assertCountEquals(1)
    }

    @Test
    fun emits_load_more_interaction_signals_and_loading_state() {
        val events = mutableListOf<ConversationEvent>()
        val state = mutableStateOf(
            ui(
                texts = listOf("ready"),
                canLoadMoreMessages = true,
            ),
        )

        compose.setContent {
            ConversationScreen(
                state = state.value,
                onEvent = { events += it },
            )
        }

        compose.onNodeWithText("Load older messages").performClick()
        assertEquals(listOf(ConversationEvent.LoadMoreMessagesTapped), events)

        compose.runOnIdle {
            state.value = state.value.copy(
                loadingMoreMessages = true,
                canLoadMoreMessages = false,
            )
        }

        compose.onNodeWithText("Loading older messages...").assertIsNotEnabled()
        compose.onAllNodesWithText("Load older messages").assertCountEquals(0)
        assertEquals(1, events.size)
    }

    private fun ui(
        texts: List<String> = emptyList(),
        turns: List<ConversationTurnUiState> = listOf(
            ConversationTurnUiState(
                id = "turn-1",
                userText = null,
                toolCalls = emptyList<ToolCallState>(),
                systemTexts = texts,
            ),
        ),
        canLoadMoreMessages: Boolean = false,
        loadingMoreMessages: Boolean = false,
        stepOpen: Map<String, Boolean> = emptyMap(),
        callOpen: Map<String, Boolean> = emptyMap(),
    ) = ConversationUiState(
        title = "Session",
        status = ServerState.Connected("http://demo.local:4096", "v1"),
        message = null,
        turns = turns,
        canLoadMoreMessages = canLoadMoreMessages,
        loadingMoreMessages = loadingMoreMessages,
        scroll = 0,
        draft = "",
        mode = ConversationMode.BUILD,
        slashSuggestions = emptyList(),
        quickSwitches = emptyList(),
        stepOpen = stepOpen,
        callOpen = callOpen,
    )
}
