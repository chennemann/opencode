package de.chennemann.opencode.mobile.streamingmarkdown

import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow

@Composable
fun StreamingMarkdownText(
    content: String,
    modifier: Modifier = Modifier,
    style: TextStyle = TextStyle.Default,
    color: Color = Color.Unspecified,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
    streaming: Boolean = true,
    inlineCode: SpanStyle = SpanStyle(
        fontFamily = FontFamily.Monospace,
        background = Color(0x1A7A7A7A),
    ),
) {
    val model = remember(streaming) {
        StreamingMarkdownState(streaming = streaming)
    }
    val runs = remember(content, model) {
        model.update(content)
    }
    val text = remember(runs, inlineCode) {
        toAnnotatedString(runs = runs, inlineCode = inlineCode)
    }
    BasicText(
        text = text,
        modifier = modifier,
        style = if (color == Color.Unspecified) style else style.copy(color = color),
        maxLines = maxLines,
        overflow = overflow,
    )
}

fun toAnnotatedString(
    runs: List<MarkdownRun>,
    inlineCode: SpanStyle = SpanStyle(
        fontFamily = FontFamily.Monospace,
        background = Color(0x1A7A7A7A),
    ),
): AnnotatedString = buildAnnotatedString {
    runs.forEach { run ->
        if (run.kind == MarkdownKind.TEXT) {
            append(run.value)
            return@forEach
        }
        pushStyle(inlineCode)
        append(run.value)
        pop()
    }
}

class StreamingMarkdownState(private val streaming: Boolean) {
    private val parser = StreamingMarkdownParser()
    private var previous = ""
    private var runs = emptyList<MarkdownRun>()

    fun update(content: String): List<MarkdownRun> {
        if (!streaming) {
            runs = parseMarkdown(content)
            previous = content
            return runs
        }
        if (content == previous) return runs
        if (content.startsWith(previous)) {
            parser.write(content.substring(previous.length))
            previous = content
            runs = parser.snapshot()
            return runs
        }
        parser.start()
        parser.write(content)
        previous = content
        runs = parser.snapshot()
        return runs
    }
}
