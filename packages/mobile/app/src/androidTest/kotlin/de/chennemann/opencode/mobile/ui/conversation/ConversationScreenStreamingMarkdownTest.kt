package de.chennemann.opencode.mobile.ui.conversation

import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.assertExists
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
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
        compose.setContent {
            ConversationScreen(
                state = ConversationUiState(
                    title = "Session",
                    status = ServerState.Connected("v1"),
                    turns = listOf(
                        ConversationTurnUiState(
                            id = "turn-1",
                            userText = null,
                            toolCalls = emptyList<ToolCallState>(),
                            systemTexts = listOf("hello `code` world"),
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
                ),
                onEvent = {},
            )
        }

        compose.onNodeWithText("hello code world").assertExists()
        compose.onNodeWithText("hello `code` world").assertDoesNotExist()
    }
}
