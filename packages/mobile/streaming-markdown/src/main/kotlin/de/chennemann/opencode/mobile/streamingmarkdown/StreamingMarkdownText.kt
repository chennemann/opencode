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
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
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
    emphasis: SpanStyle = SpanStyle(
        fontStyle = FontStyle.Italic,
    ),
    strong: SpanStyle = SpanStyle(
        fontWeight = FontWeight.Bold,
    ),
) {
    val model = remember(streaming) {
        StreamingMarkdownState(streaming = streaming)
    }
    val runs = remember(content, model) {
        model.update(content)
    }
    val text = remember(runs, inlineCode, emphasis, strong) {
        toAnnotatedString(
            runs = runs,
            inlineCode = inlineCode,
            emphasis = emphasis,
            strong = strong,
        )
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
    emphasis: SpanStyle = SpanStyle(
        fontStyle = FontStyle.Italic,
    ),
    strong: SpanStyle = SpanStyle(
        fontWeight = FontWeight.Bold,
    ),
): AnnotatedString = buildAnnotatedString {
    decorate(runs).forEach { run ->
        if (run.kind == MarkdownKind.TEXT) {
            append(run.value)
            return@forEach
        }
        if (run.kind == MarkdownKind.INLINE_CODE) {
            pushStyle(inlineCode)
            append(run.value)
            pop()
            return@forEach
        }
        if (run.kind == MarkdownKind.EMPHASIS) {
            pushStyle(emphasis)
            append(run.value)
            pop()
            return@forEach
        }
        if (run.kind == MarkdownKind.STRONG) {
            pushStyle(strong)
            append(run.value)
            pop()
            return@forEach
        }
        pushStyle(inlineCode)
        append(run.value)
        pop()
    }
}

private fun decorate(runs: List<MarkdownRun>): List<MarkdownRun> {
    val out = mutableListOf<MarkdownRun>()
    runs.forEach { run ->
        if (run.kind != MarkdownKind.TEXT) {
            append(out, run.kind, run.value)
            return@forEach
        }
        var emphasis = false
        var strong = false
        var slash = false
        var index = 0
        while (index < run.value.length) {
            val char = run.value[index]
            if (slash) {
                append(out, kind(emphasis, strong), char.toString())
                slash = false
                index += 1
                continue
            }
            if (char == '\\') {
                slash = true
                index += 1
                continue
            }
            if (char == '*') {
                val pair = index + 1 < run.value.length && run.value[index + 1] == '*'
                if (pair) {
                    strong = !strong
                    index += 2
                    continue
                }
                emphasis = !emphasis
                index += 1
                continue
            }
            append(out, kind(emphasis, strong), char.toString())
            index += 1
        }
        if (slash) {
            append(out, kind(emphasis, strong), "\\")
        }
    }
    return out
}

private fun kind(emphasis: Boolean, strong: Boolean): MarkdownKind {
    if (strong) return MarkdownKind.STRONG
    if (emphasis) return MarkdownKind.EMPHASIS
    return MarkdownKind.TEXT
}

private fun append(
    runs: MutableList<MarkdownRun>,
    kind: MarkdownKind,
    value: String,
) {
    if (value.isEmpty()) return
    val run = runs.lastOrNull()
    if (run == null) {
        runs += MarkdownRun(kind, value)
        return
    }
    if (run.kind != kind) {
        runs += MarkdownRun(kind, value)
        return
    }
    runs[runs.lastIndex] = run.copy(value = run.value + value)
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
