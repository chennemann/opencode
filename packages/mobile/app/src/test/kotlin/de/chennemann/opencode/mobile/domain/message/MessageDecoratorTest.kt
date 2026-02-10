package de.chennemann.opencode.mobile.domain.message

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class MessageDecoratorTest {
    private val decorator = MessageDecorator()

    @Test
    fun decoratesAssistantMessageWithToolCalls() {
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

        val value = decorator.decorate("assistant", "(streaming...)", parts)

        assertEquals("final answer", value.text)
        assertEquals(1, value.toolCalls.size)
        assertEquals("bash", value.toolCalls.first().title)
    }

    @Test
    fun keepsUserMessageUntouched() {
        val value = decorator.decorate("user", "hello", emptyList())

        assertEquals("hello", value.text)
        assertEquals(0, value.toolCalls.size)
    }
}
