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
import androidx.compose.ui.text.style.TextDecoration
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
    link: SpanStyle = SpanStyle(
        color = Color(0xFF1565C0),
        textDecoration = TextDecoration.Underline,
    ),
    blockCode: SpanStyle = SpanStyle(
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
    val text = remember(runs, inlineCode, emphasis, strong, link, blockCode) {
        toAnnotatedString(
            runs = runs,
            inlineCode = inlineCode,
            emphasis = emphasis,
            strong = strong,
            link = link,
            blockCode = blockCode,
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
    link: SpanStyle = SpanStyle(
        color = Color(0xFF1565C0),
        textDecoration = TextDecoration.Underline,
    ),
    blockCode: SpanStyle = SpanStyle(
        fontFamily = FontFamily.Monospace,
        background = Color(0x1A7A7A7A),
    ),
): AnnotatedString = buildAnnotatedString {
    linkify(decorate(runs)).forEach { run ->
        val start = length
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
        if (run.kind == MarkdownKind.LINK) {
            pushStyle(link)
            append(run.value)
            pop()
            val href = run.href
            if (href != null) {
                addStringAnnotation(
                    tag = "URL",
                    annotation = href,
                    start = start,
                    end = length,
                )
            }
            return@forEach
        }
        if (run.kind == MarkdownKind.BLOCK_CODE) {
            pushStyle(blockCode)
            append(run.value)
            pop()
            return@forEach
        }
        pushStyle(link)
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
    href: String? = null,
) {
    if (value.isEmpty()) return
    val run = runs.lastOrNull()
    if (run == null) {
        runs += MarkdownRun(kind, value, href)
        return
    }
    if (run.kind != kind || run.href != href) {
        runs += MarkdownRun(kind, value, href)
        return
    }
    runs[runs.lastIndex] = run.copy(value = run.value + value)
}

private fun linkify(runs: List<MarkdownRun>): List<MarkdownRun> {
    val out = mutableListOf<MarkdownRun>()
    runs.forEach { run ->
        if (run.kind != MarkdownKind.TEXT) {
            append(out, run.kind, run.value, run.href)
            return@forEach
        }
        val plain = StringBuilder()
        var index = 0
        fun flush() {
            if (plain.isEmpty()) return
            append(out, MarkdownKind.TEXT, plain.toString())
            plain.clear()
        }
        while (index < run.value.length) {
            if (run.value[index] == '[') {
                val close = run.value.indexOf(']', index + 1)
                val open = if (close == -1) -1 else close + 1
                val start = if (open >= run.value.length || run.value[open] != '(') -1 else open + 1
                val end = if (start == -1) -1 else run.value.indexOf(')', start)
                if (close != -1 && start != -1 && end != -1) {
                    val label = run.value.substring(index + 1, close)
                    val href = run.value.substring(start, end)
                    if (label.isNotEmpty() && href.startsWith("http")) {
                        flush()
                        append(out, MarkdownKind.LINK, label, href)
                        index = end + 1
                        continue
                    }
                }
            }
            val http = run.value.startsWith("http://", index) || run.value.startsWith("https://", index)
            if (http) {
                var end = index
                while (end < run.value.length && !run.value[end].isWhitespace()) {
                    end += 1
                }
                val href = run.value.substring(index, end)
                flush()
                append(out, MarkdownKind.LINK, href, href)
                index = end
                continue
            }
            plain.append(run.value[index])
            index += 1
        }
        flush()
    }
    return out
}

class StreamingMarkdownState(private val streaming: Boolean) {
    private val parser = StreamingMarkdownParser()
    private var previous = ""
    private var runs = emptyList<MarkdownRun>()

    fun update(content: String): List<MarkdownRun> {
        if (!streaming) {
            runs = parseMarkdownDocument(content)
            previous = content
            return runs
        }
        if (content == previous) return runs
        if (content.contains("```")) {
            runs = parseMarkdownDocument(content)
            previous = content
            return runs
        }
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
