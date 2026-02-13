package de.chennemann.opencode.mobile.ui.conversation

import android.util.Log
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
import org.junit.Assert.assertTrue
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

    @Test
    fun tracks_render_throughput_for_streamed_markdown_updates() {
        val state = mutableStateOf(ui(listOf("chunk-0 `code-0`")))

        compose.setContent {
            ConversationScreen(
                state = state.value,
                onEvent = {},
            )
        }

        val samples = mutableListOf<Long>()
        repeat(160) { index ->
            val text = "chunk-$index `code-$index`"
            val dt = kotlin.system.measureNanoTime {
                compose.runOnIdle {
                    state.value = ui(listOf(text))
                }
                compose.waitForIdle()
            }
            samples += dt
        }

        val avgMs = samples.average() / 1_000_000.0
        val sorted = samples.sorted()
        val p95Ms = sorted[(sorted.size * 95) / 100] / 1_000_000.0
        Log.i(
            "ConversationPerf",
            "phase5_render updates=${samples.size} avg_ms=$avgMs p95_ms=$p95Ms",
        )

        assertTrue("Expected avg render update under 200ms, got $avgMs", avgMs < 200.0)
        assertTrue("Expected p95 render update under 400ms, got $p95Ms", p95Ms < 400.0)
        compose.onAllNodesWithText("chunk-159 code-159").assertCountEquals(1)
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
