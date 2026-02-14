package de.chennemann.opencode.mobile.data

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import de.chennemann.opencode.mobile.db.AppDatabase
import de.chennemann.opencode.mobile.db.App_log
import de.chennemann.opencode.mobile.di.DispatcherProvider
import de.chennemann.opencode.mobile.domain.session.LogEntry
import de.chennemann.opencode.mobile.domain.session.LogFilter
import de.chennemann.opencode.mobile.domain.session.LogLevel
import de.chennemann.opencode.mobile.domain.session.LogRecord
import de.chennemann.opencode.mobile.domain.session.LogStoreGateway
import de.chennemann.opencode.mobile.domain.session.LogUnit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

class LocalLogRepository(
    private val db: AppDatabase,
    private val dispatchers: DispatcherProvider,
    private val json: Json,
) : LogStoreGateway {
    private var writes = 0L

    override suspend fun append(record: LogRecord) {
        withContext(dispatchers.io) {
            db.appDatabaseQueries.insertAppLog(
                created_at = record.createdAt,
                level = record.level.key,
                logical_unit = record.unit.key,
                tag = record.tag,
                event = record.event,
                message = record.message,
                context_json = encode(record.context),
                throwable = record.throwable,
                redacted = if (record.redacted) 1 else 0,
            )
            writes += 1
            if (writes % PruneStride == 0L) {
                prune(record.createdAt)
            }
        }
    }

    override fun observe(filter: LogFilter): Flow<List<LogEntry>> {
        val term = filter.query.trim().ifBlank { null }
        return db.appDatabaseQueries
            .listAppLog(
                logical_unit = filter.unit?.key,
                level = filter.level?.key,
                value_ = term,
                value__ = filter.limit,
            )
            .asFlow()
            .mapToList(dispatchers.io)
            .map { rows -> rows.map(::entry) }
    }

    override suspend fun prune(now: Long) {
        withContext(dispatchers.io) {
            db.appDatabaseQueries.deleteAppLogBefore(now - RetainMs)
            val count = db.appDatabaseQueries.countAppLog().executeAsOne()
            val overflow = count - MaxRows
            if (overflow > 0) {
                db.appDatabaseQueries.deleteAppLogOverflow(MaxRows)
            }
        }
    }

    override suspend fun clear() {
        withContext(dispatchers.io) {
            db.appDatabaseQueries.deleteAppLogAll()
        }
    }

    private fun encode(context: Map<String, String>): String {
        return buildJsonObject {
            context.forEach { put(it.key, it.value) }
        }.toString()
    }

    private fun decode(value: String): Map<String, String> {
        return runCatching {
            json.parseToJsonElement(value).jsonObject.mapValues { it.value.jsonPrimitive.content }
        }.getOrDefault(emptyMap())
    }

    private fun entry(value: App_log): LogEntry {
        return LogEntry(
            id = value.id,
            createdAt = value.created_at,
            level = LogLevel.from(value.level),
            unit = LogUnit.from(value.logical_unit),
            tag = value.tag,
            event = value.event,
            message = value.message,
            context = decode(value.context_json),
            throwable = value.throwable,
        )
    }
}

private const val RetainMs = 7 * 24 * 60 * 60 * 1000L
private const val MaxRows = 5000L
private const val PruneStride = 200L
