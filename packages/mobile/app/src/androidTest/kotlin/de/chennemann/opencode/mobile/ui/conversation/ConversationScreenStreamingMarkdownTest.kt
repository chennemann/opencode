package de.chennemann.opencode.mobile.ui.conversation

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.longClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.chennemann.opencode.mobile.domain.session.ServerState
import de.chennemann.opencode.mobile.domain.session.ToolCallState
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

        compose.onAllNodesWithText("hello code world").onFirst().performTouchInput {
            longClick()
        }
        compose.onAllNodesWithText("hello code world").assertCountEquals(1)
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

    private fun ui(texts: List<String>) = ConversationUiState(
        title = "Session",
        status = ServerState.Connected("v1"),
        turns = listOf(
            ConversationTurnUiState(
                id = "turn-1",
                userText = null,
                toolCalls = emptyList<ToolCallState>(),
                systemTexts = texts,
            ),
        ),
        canLoadMoreMessages = false,
        loadingMoreMessages = false,
        scroll = 0,
        draft = "",
        slashSuggestions = emptyList(),
        quickSwitches = emptyList(),
        stepOpen = emptyMap(),
        callOpen = emptyMap(),
    )
}
