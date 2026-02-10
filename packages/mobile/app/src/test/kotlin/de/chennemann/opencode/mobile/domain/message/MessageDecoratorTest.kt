package de.chennemann.opencode.mobile.domain.message

import de.chennemann.opencode.mobile.ui.state.MessageState
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class MessageDecoratorTest {
    private val decorator = MessageDecorator()

    @Test
    fun decoratesAssistantMessageWithToolCalls() {
        val message = MessageState(
            id = "m1",
            role = "assistant",
            text = "(streaming...)",
            sort = "1",
        )
        val parts = listOf(
            MessagePart(
                id = "text-1",
                type = "text",
                text = "final answer",
            ),
            MessagePart(
                id = "tool-1",
                type = "tool",
                text = "",
                tool = "bash",
                status = "completed",
                title = "Run command",
                output = "ok",
            ),
        )

        val value = decorator.decorate(message, parts)

        assertEquals("final answer", value.text)
        assertEquals(1, value.toolCalls.size)
        assertEquals("bash", value.toolCalls.first().title)
    }

    @Test
    fun keepsUserMessageUntouched() {
        val message = MessageState(
            id = "m1",
            role = "user",
            text = "hello",
            sort = "1",
        )

        val value = decorator.decorate(message, emptyList())

        assertEquals("hello", value.text)
        assertEquals(0, value.toolCalls.size)
    }
}
