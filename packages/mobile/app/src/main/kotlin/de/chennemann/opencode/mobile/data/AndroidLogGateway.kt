package de.chennemann.opencode.mobile.data

import android.util.Log
import de.chennemann.opencode.mobile.domain.session.LogGateway
import de.chennemann.opencode.mobile.domain.session.LogLevel
import de.chennemann.opencode.mobile.domain.session.LogRecord
import de.chennemann.opencode.mobile.domain.session.LogRedactor
import de.chennemann.opencode.mobile.domain.session.LogStoreGateway
import de.chennemann.opencode.mobile.domain.session.LogUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

class AndroidLogGateway(
    private val store: LogStoreGateway,
    private val scope: CoroutineScope,
    private val redactor: LogRedactor,
) : LogGateway {
    init {
        scope.launch {
            store.prune()
        }
    }

    override fun log(
        level: LogLevel,
        unit: LogUnit,
        tag: String,
        event: String,
        message: String,
        context: Map<String, String>,
        error: Throwable?,
    ) {
        val safeMessage = redactor.redact(message)
        val safeContext = redactor.redact(context)
        val safeThrowable = redactor.throwable(error)
        val line = format(event, safeMessage, safeContext)
        when (level) {
            LogLevel.debug -> Log.d(tag, line)
            LogLevel.info -> Log.i(tag, line)
            LogLevel.warn -> Log.w(tag, line)
            LogLevel.error -> Log.e(tag, line)
        }
        scope.launch {
            store.append(
                LogRecord(
                    createdAt = System.currentTimeMillis(),
                    level = level,
                    unit = unit,
                    tag = tag,
                    event = event,
                    message = safeMessage,
                    context = safeContext,
                    throwable = safeThrowable,
                    redacted = true,
                )
            )
        }
    }
}

private fun format(event: String, message: String, context: Map<String, String>): String {
    if (context.isEmpty()) return "$event: $message"
    val meta = context.entries.joinToString(" ") { "${it.key}=${it.value}" }
    return "$event: $message $meta"
}
