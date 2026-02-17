package de.chennemann.opencode.mobile.data.repository

import de.chennemann.opencode.mobile.domain.session.CommandState
import de.chennemann.opencode.mobile.domain.session.LogEntry
import de.chennemann.opencode.mobile.domain.session.LogFacet
import de.chennemann.opencode.mobile.domain.session.LogFilter
import de.chennemann.opencode.mobile.domain.session.LogRecord
import de.chennemann.opencode.mobile.domain.session.MessageState
import de.chennemann.opencode.mobile.domain.session.ProjectState
import de.chennemann.opencode.mobile.domain.session.SessionState
import kotlinx.coroutines.flow.Flow

data class SessionListFilter(val query: String = "", val includeArchived: Boolean = false, val limit: Long = 50)
data class MessagePageRequest(val beforeMessageId: String? = null, val limit: Long = 100)
data class MessagePage(
    val items: List<MessageState>,
    val hasMore: Boolean,
    val nextBeforeMessageId: String?,
)
data class PendingMessageRef(val localMessageId: String)
enum class RemoteBatchSource { STREAM_HINT, SNAPSHOT_SYNC, OUTBOX_ACK }
enum class SessionSyncStatus { IDLE, QUEUED, RUNNING, BACKOFF, FAILED }
data class SessionSyncState(
    val sessionId: String,
    val status: SessionSyncStatus,
    val lastSnapshotAt: Long?,
    val lastStreamSeenAt: Long?,
    val lastErrorAt: Long?,
)
data class AppendLocalMessageInput(
    val sessionId: String,
    val directory: String,
    val text: String,
    val agent: String,
    val createdAt: Long,
)
data class ConfirmSentMessageInput(
    val sessionId: String,
    val localMessageId: String,
    val serverUserMessageId: String,
    val serverAssistantMessageId: String,
    val confirmedAt: Long,
)
data class SessionRemoteBatch(
    val sessionId: String,
    val payloadJson: String,
    val receivedAt: Long,
    val source: RemoteBatchSource,
)
data class RepoResult(val ok: Boolean, val reason: String? = null)
enum class SyncReason { FOCUS, USER_SEND, REFRESH, RECONNECT, SCHEDULED, OUTBOX_DRAIN }

data class ConnectionSnapshot(val endpoint: String, val discovered: String?, val status: String)
data class LogPage(val offset: Long = 0, val size: Long = 100)
data class PrefsState(
    val quickSwitchScope: String,
    val sortMode: String,
    val logsFilterJson: String,
    val logsRetentionPolicy: String,
)

interface SessionRepository {
    fun observeSessionList(projectId: String, filter: SessionListFilter): Flow<List<SessionState>>
    fun observeFocusedSession(): Flow<SessionState?>
    fun observeMessagePage(sessionId: String, request: MessagePageRequest): Flow<MessagePage>
    fun observeSyncState(sessionId: String): Flow<SessionSyncState>
    suspend fun focus(sessionId: String)
    suspend fun appendLocalMessage(input: AppendLocalMessageInput): PendingMessageRef
    suspend fun confirmSentMessage(input: ConfirmSentMessageInput): RepoResult
    suspend fun applyRemoteBatch(batch: SessionRemoteBatch): RepoResult
    suspend fun requestMessagePage(sessionId: String, beforeMessageId: String?, limit: Long): RepoResult
    suspend fun archive(sessionId: String): RepoResult
    suspend fun rename(sessionId: String, title: String): RepoResult
    suspend fun requestSync(sessionId: String, reason: SyncReason)
}

interface ProjectRepository {
    fun observeProjects(): Flow<List<ProjectState>>
    fun observeSelectedProject(): Flow<ProjectState?>
    suspend fun select(projectId: String)
    suspend fun toggleFavorite(projectId: String): Boolean
    suspend fun toggleHidden(projectId: String): Boolean
    suspend fun upsertProjects(items: List<ProjectState>)
    suspend fun requestRefresh(projectId: String)
}

interface CommandRepository {
    fun observeCommands(projectId: String): Flow<List<CommandState>>
    suspend fun replaceCommands(projectId: String, commands: List<CommandState>)
    suspend fun find(projectId: String, commandName: String): CommandState?
}

interface ConnectionRepository {
    fun observeConnection(): Flow<ConnectionSnapshot>
    suspend fun setEndpoint(url: String)
    suspend fun setStatus(status: String)
    suspend fun recordDiscovery(items: List<String>)
}

interface LogRepository {
    fun observeLogs(filter: LogFilter, page: LogPage): Flow<List<LogEntry>>
    fun observeFacets(): Flow<LogFacet>
    suspend fun append(entry: LogRecord)
    suspend fun prune(policy: String)
}

interface PreferencesRepository {
    fun observePrefs(): Flow<PrefsState>
    suspend fun setQuickSwitchScope(scope: String)
    suspend fun setSortMode(mode: String)
    suspend fun setLogsFilter(filterJson: String)
    suspend fun setLogsRetentionPolicy(policy: String)
}
