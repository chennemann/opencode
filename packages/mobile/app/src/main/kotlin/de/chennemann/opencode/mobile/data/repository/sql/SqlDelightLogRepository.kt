package de.chennemann.opencode.mobile.data.repository.sql

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import de.chennemann.opencode.mobile.data.repository.LogPage
import de.chennemann.opencode.mobile.data.repository.LogRepository
import de.chennemann.opencode.mobile.db.AppDatabase
import de.chennemann.opencode.mobile.db.App_log
import de.chennemann.opencode.mobile.di.DispatcherProvider
import de.chennemann.opencode.mobile.domain.session.LogEntry
import de.chennemann.opencode.mobile.domain.session.LogFacet
import de.chennemann.opencode.mobile.domain.session.LogFilter
import de.chennemann.opencode.mobile.domain.session.LogLevel
import de.chennemann.opencode.mobile.domain.session.LogProjectOption
import de.chennemann.opencode.mobile.domain.session.LogRecord
import de.chennemann.opencode.mobile.domain.session.LogSessionOption
import de.chennemann.opencode.mobile.domain.session.LogUnit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

class SqlDelightLogRepository(
    private val db: AppDatabase,
    private val dispatchers: DispatcherProvider,
    private val json: Json,
) : LogRepository {
    override fun observeLogs(filter: LogFilter, page: LogPage): Flow<List<LogEntry>> {
        val term = filter.query.trim().ifBlank { null }
        return db.appDatabaseQueries
            .listAppLog(
                logical_unit = filter.unit?.key,
                level = filter.level?.key,
                event = filter.event,
                project_id = filter.projectId,
                session_id = filter.sessionId,
                from_at = filter.from,
                until_at = filter.until,
                search = term,
                limit = page.offset + page.size,
            )
            .asFlow()
            .mapToList(dispatchers.io)
            .map { it.map(::entry).drop(page.offset.toInt()).take(page.size.toInt()) }
    }

    override fun observeFacets(): Flow<LogFacet> {
        val projects = db.appDatabaseQueries
            .listAppLogProjectFacet { projectId, projectName ->
                LogProjectOption(
                    id = projectId,
                    name = projectName ?: projectId,
                )
            }
            .asFlow()
            .mapToList(dispatchers.io)
        val sessions = db.appDatabaseQueries
            .listAppLogSessionFacet { sessionId, sessionTitle ->
                LogSessionOption(
                    id = sessionId,
                    title = sessionTitle ?: sessionId,
                )
            }
            .asFlow()
            .mapToList(dispatchers.io)
        val events = db.appDatabaseQueries
            .listAppLogEventFacet()
            .asFlow()
            .mapToList(dispatchers.io)
        return combine(projects, sessions, events) { p, s, e ->
            LogFacet(
                projects = p,
                sessions = s,
                events = e,
            )
        }
    }

    override suspend fun append(entry: LogRecord) {
        withContext(dispatchers.io) {
            db.appDatabaseQueries.insertAppLog(
                created_at = entry.createdAt,
                level = entry.level.key,
                logical_unit = entry.unit.key,
                tag = entry.tag,
                event = entry.event,
                project_id = entry.projectId,
                project_name = entry.projectName,
                session_id = entry.sessionId,
                session_title = entry.sessionTitle,
                message = entry.message,
                context_json = encode(entry.context),
                throwable = entry.throwable,
                redacted = if (entry.redacted) 1 else 0,
            )
        }
    }

    override suspend fun prune(policy: String) {
        val parsed = parsePolicy(policy)
        withContext(dispatchers.io) {
            db.appDatabaseQueries.deleteAppLogBefore(System.currentTimeMillis() - parsed.windowMs)
            val count = db.appDatabaseQueries.countAppLog().executeAsOne()
            if (count > parsed.maxRows) {
                db.appDatabaseQueries.deleteAppLogOverflow(parsed.maxRows)
            }
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
            projectId = value.project_id,
            projectName = value.project_name,
            sessionId = value.session_id,
            sessionTitle = value.session_title,
            message = value.message,
            context = decode(value.context_json),
            throwable = value.throwable,
        )
    }
}

private data class RetentionPolicy(
    val windowMs: Long,
    val maxRows: Long,
)

private fun parsePolicy(value: String): RetentionPolicy {
    val match = Regex("window_(\\d+)d_max_(\\d+)").matchEntire(value)
    if (match == null) {
        return RetentionPolicy(
            windowMs = 7 * 24 * 60 * 60 * 1000L,
            maxRows = 5000,
        )
    }
    return RetentionPolicy(
        windowMs = (match.groupValues[1].toLongOrNull() ?: 7L) * 24 * 60 * 60 * 1000L,
        maxRows = match.groupValues[2].toLongOrNull() ?: 5000L,
    )
}
