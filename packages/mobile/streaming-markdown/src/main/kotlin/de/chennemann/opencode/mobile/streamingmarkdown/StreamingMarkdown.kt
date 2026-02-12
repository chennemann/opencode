package de.chennemann.opencode.mobile.streamingmarkdown

enum class MarkdownKind {
    TEXT,
    INLINE_CODE,
}

data class MarkdownRun(
    val kind: MarkdownKind,
    val value: String,
)

class StreamingMarkdownParser {
    private val runs = mutableListOf<MarkdownRun>()
    private var code = false

    fun start(): List<MarkdownRun> {
        reset()
        return snapshot()
    }

    fun write(chunk: String): List<MarkdownRun> {
        val normalized = chunk.replace("\r\n", "\n").replace('\r', '\n')
        normalized.forEach { char ->
            if (char == '`') {
                code = !code
                return@forEach
            }
            if (char == '\n') {
                code = false
                add(MarkdownKind.TEXT, "\n")
                return@forEach
            }
            add(if (code) MarkdownKind.INLINE_CODE else MarkdownKind.TEXT, char.toString())
        }
        return snapshot()
    }

    fun end(): List<MarkdownRun> = snapshot()

    fun reset() {
        runs.clear()
        code = false
    }

    fun snapshot(): List<MarkdownRun> = runs.toList()

    private fun add(kind: MarkdownKind, value: String) {
        if (value.isEmpty()) return
        val run = runs.lastOrNull()
        if (run == null) {
            runs += MarkdownRun(kind = kind, value = value)
            return
        }
        if (run.kind != kind) {
            runs += MarkdownRun(kind = kind, value = value)
            return
        }
        runs[runs.lastIndex] = run.copy(value = run.value + value)
    }
}

fun parseMarkdown(content: String): List<MarkdownRun> {
    val parser = StreamingMarkdownParser()
    parser.start()
    parser.write(content)
    return parser.end()
}
