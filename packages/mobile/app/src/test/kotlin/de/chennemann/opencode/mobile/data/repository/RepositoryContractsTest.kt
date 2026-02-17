package de.chennemann.opencode.mobile.data.repository

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import de.chennemann.opencode.mobile.data.repository.sql.SqlDelightCommandRepository
import de.chennemann.opencode.mobile.data.repository.sql.SqlDelightConnectionRepository
import de.chennemann.opencode.mobile.data.repository.sql.SqlDelightLogRepository
import de.chennemann.opencode.mobile.data.repository.sql.SqlDelightPreferencesRepository
import de.chennemann.opencode.mobile.data.repository.sql.SqlDelightProjectRepository
import de.chennemann.opencode.mobile.data.repository.sql.SqlDelightSessionRepository
import de.chennemann.opencode.mobile.db.AppDatabase
import de.chennemann.opencode.mobile.di.DispatcherProvider
import de.chennemann.opencode.mobile.domain.session.CommandState
import de.chennemann.opencode.mobile.domain.session.LogFilter
import de.chennemann.opencode.mobile.domain.session.LogLevel
import de.chennemann.opencode.mobile.domain.session.LogRecord
import de.chennemann.opencode.mobile.domain.session.LogUnit
import de.chennemann.opencode.mobile.domain.session.ProjectState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files

@OptIn(ExperimentalCoroutinesApi::class)
class RepositoryContractsTest {
    @Test
    fun t1_observeSessionList_returnsOnlyRequestedProjectWhenNotIncludingArchived() = runTest {
        val lane = StandardTestDispatcher(testScheduler)
        val c = ctx(lane)
        upsertSession(c.db, id = "s1", project = "p1", title = "Alpha", updatedAt = 300)
        upsertSession(c.db, id = "s2", project = "p2", title = "Other", updatedAt = 200)
        upsertSession(c.db, id = "s3", project = "p1", title = "Archived", updatedAt = 100, archivedAt = 10)

        val rows = c.session.observeSessionList("p1", SessionListFilter(includeArchived = false, limit = 20)).first()

        assertEquals(listOf("s1"), rows.map { it.id })
    }

    @Test
    fun t2_observeSessionList_includesArchivedWhenRequested() = runTest {
        val lane = StandardTestDispatcher(testScheduler)
        val c = ctx(lane)
        upsertSession(c.db, id = "s1", project = "p1", title = "Live", updatedAt = 300)
        upsertSession(c.db, id = "s2", project = "p1", title = "Archived", updatedAt = 200, archivedAt = 55)

        val rows = c.session.observeSessionList("p1", SessionListFilter(includeArchived = true, limit = 20)).first()

        assertEquals(listOf("s1", "s2"), rows.map { it.id })
    }

    @Test
    fun t3_observeSessionList_appliesQueryAndDeterministicSort() = runTest {
        val lane = StandardTestDispatcher(testScheduler)
        val c = ctx(lane)
        upsertSession(c.db, id = "s3", project = "p1", title = "alpha log", updatedAt = 100)
        upsertSession(c.db, id = "s2", project = "p1", title = "zeta", directory = "/tmp/alpha-dir", updatedAt = 100)
        upsertSession(c.db, id = "s1", project = "p1", title = "beta", updatedAt = 200)

        val rows = c.session.observeSessionList("p1", SessionListFilter(query = "alpha", includeArchived = true, limit = 20)).first()

        assertEquals(listOf("s3", "s2"), rows.map { it.id })
    }

    @Test
    fun t4_focus_updatesFocusedPointerAndEmitsAfterCommit() = runTest {
        val lane = StandardTestDispatcher(testScheduler)
        val c = ctx(lane)
        upsertSession(c.db, id = "s1", project = "p1", title = "One", updatedAt = 100)
        upsertSession(c.db, id = "s2", project = "p1", title = "Two", updatedAt = 200)

        c.session.focus("s2")
        val row = c.session.observeFocusedSession().first()

        assertNotNull(row)
        assertEquals("s2", row?.id)
    }

    @Test
    fun t5_appendLocalMessage_insertsPendingRowAndReturnsLocalId() = runTest {
        val lane = StandardTestDispatcher(testScheduler)
        val c = ctx(lane)

        val ref = c.session.appendLocalMessage(
            AppendLocalMessageInput(
                sessionId = "s1",
                directory = "/tmp",
                text = "hello",
                agent = "gpt",
                createdAt = 1000,
            )
        )

        val row = c.db.appDatabaseQueries.selectMessageById(ref.localMessageId).executeAsOneOrNull()
        assertTrue(ref.localMessageId.isNotBlank())
        assertNotNull(row)
        assertEquals(1L, row?.pending)
        assertEquals(ref.localMessageId, row?.local_message_id)
    }

    @Test
    fun t6_confirmSentMessage_patchesPendingRowToServerIdsInSingleMutation() = runTest {
        val lane = StandardTestDispatcher(testScheduler)
        val c = ctx(lane)
        val ref = c.session.appendLocalMessage(
            AppendLocalMessageInput("s1", "/tmp", "hello", "gpt", 1000)
        )

        val result = c.session.confirmSentMessage(
            ConfirmSentMessageInput(
                sessionId = "s1",
                localMessageId = ref.localMessageId,
                serverUserMessageId = "u-1",
                serverAssistantMessageId = "a-1",
                confirmedAt = 2000,
            )
        )

        assertTrue(result.ok)
        assertNull(c.db.appDatabaseQueries.selectMessageById(ref.localMessageId).executeAsOneOrNull())
        val user = c.db.appDatabaseQueries.selectMessageById("u-1").executeAsOneOrNull()
        val assistant = c.db.appDatabaseQueries.selectMessageById("a-1").executeAsOneOrNull()
        assertNotNull(user)
        assertEquals(0L, user?.pending)
        assertNull(user?.local_message_id)
        assertNotNull(assistant)
        assertEquals("assistant", assistant?.role)
    }

    @Test
    fun t7_applyRemoteBatch_upsertsSessionAndMessage() = runTest {
        val lane = StandardTestDispatcher(testScheduler)
        val c = ctx(lane)

        val result = c.session.applyRemoteBatch(
            SessionRemoteBatch(
                sessionId = "s1",
                receivedAt = 5000,
                source = RemoteBatchSource.SNAPSHOT_SYNC,
                payloadJson = """
                {
                  "sessions": [{"id":"s1","projectId":"p1","title":"Remote","version":"v1","directory":"/r","updatedAt":4000}],
                  "messages": [{"id":"m1","sessionId":"s1","role":"assistant","text":"hi","sort":"00000000000000004000:m1","createdAt":4000,"updatedAt":4000}]
                }
                """.trimIndent(),
            )
        )

        assertTrue(result.ok)
        assertEquals("Remote", c.db.appDatabaseQueries.observeSessionList("p1", 1, "", 10) { _, title, _, _, _, _, _ -> title }.executeAsOne())
        assertEquals("hi", c.db.appDatabaseQueries.selectMessageById("m1").executeAsOneOrNull()?.text)
    }

    @Test
    fun t8_applyRemoteBatch_patchesCanonicalMessageWithoutLocalDuplication() = runTest {
        val lane = StandardTestDispatcher(testScheduler)
        val c = ctx(lane)
        val ref = c.session.appendLocalMessage(AppendLocalMessageInput("s1", "/tmp", "hello", "gpt", 1000))
        c.session.confirmSentMessage(
            ConfirmSentMessageInput(
                sessionId = "s1",
                localMessageId = ref.localMessageId,
                serverUserMessageId = "u-1",
                serverAssistantMessageId = "a-1",
                confirmedAt = 2000,
            )
        )

        val result = c.session.applyRemoteBatch(
            SessionRemoteBatch(
                sessionId = "s1",
                receivedAt = 2100,
                source = RemoteBatchSource.STREAM_HINT,
                payloadJson = """
                {
                  "sessions": [],
                  "messages": [{"id":"u-1","sessionId":"s1","role":"user","text":"hello patched","sort":"00000000000000001000:u-1","createdAt":1000,"updatedAt":2100}]
                }
                """.trimIndent(),
            )
        )

        assertTrue(result.ok)
        val page = c.session.observeMessagePage("s1", MessagePageRequest(limit = 20)).first()
        assertEquals(2, page.items.size)
        assertEquals(1, page.items.count { it.id == "u-1" })
        assertEquals("hello patched", c.db.appDatabaseQueries.selectMessageById("u-1").executeAsOneOrNull()?.text)
        assertNull(c.db.appDatabaseQueries.selectMessageByLocalId(ref.localMessageId).executeAsOneOrNull())
    }

    @Test
    fun t9_archive_hidesFromActiveListAndKeepsArchivedQueryable() = runTest {
        val lane = StandardTestDispatcher(testScheduler)
        val c = ctx(lane)
        upsertSession(c.db, id = "s1", project = "p1", title = "Archive me", updatedAt = 100)

        val result = c.session.archive("s1")

        assertTrue(result.ok)
        assertTrue(c.session.observeSessionList("p1", SessionListFilter(includeArchived = false)).first().isEmpty())
        assertEquals(listOf("s1"), c.session.observeSessionList("p1", SessionListFilter(includeArchived = true)).first().map { it.id })
    }

    @Test
    fun t10_rename_updatesSessionListAndFocusedSession() = runTest {
        val lane = StandardTestDispatcher(testScheduler)
        val c = ctx(lane)
        upsertSession(c.db, id = "s1", project = "p1", title = "Old", updatedAt = 100)
        c.session.focus("s1")

        val result = c.session.rename("s1", "New")

        assertTrue(result.ok)
        assertEquals("New", c.session.observeSessionList("p1", SessionListFilter(includeArchived = true)).first().first().title)
        assertEquals("New", c.session.observeFocusedSession().first()?.title)
    }

    @Test
    fun t11_requestSync_userSendSetsSyncStateQueued() = runTest {
        val lane = StandardTestDispatcher(testScheduler)
        val c = ctx(lane)

        c.session.requestSync("s1", SyncReason.USER_SEND)
        val row = c.session.observeSyncState("s1").first()

        assertEquals("s1", row.sessionId)
        assertEquals(SessionSyncStatus.QUEUED, row.status)
    }

    @Test
    fun t12_requestSync_outboxDrainUpdatesSameRowWithoutDuplicates() = runTest {
        val lane = StandardTestDispatcher(testScheduler)
        val c = ctx(lane)

        c.session.requestSync("s1", SyncReason.USER_SEND)
        c.session.requestSync("s1", SyncReason.OUTBOX_DRAIN)

        val rows = c.db.appDatabaseQueries.observeSyncState("s1") { sessionId, status, _, _, _ ->
            SessionSyncState(
                sessionId = sessionId,
                status = SessionSyncStatus.valueOf(status),
                lastSnapshotAt = null,
                lastStreamSeenAt = null,
                lastErrorAt = null,
            )
        }.executeAsList()
        assertEquals(1, rows.size)
        assertEquals(SessionSyncStatus.QUEUED, rows.first().status)
    }

    @Test
    fun t13_observeMessagePage_latestPageRespectsLimitAndHasMore() = runTest {
        val lane = StandardTestDispatcher(testScheduler)
        val c = ctx(lane)
        seedMessages(c.db, "s1", 1, 5)

        val page = c.session.observeMessagePage("s1", MessagePageRequest(limit = 2)).first()

        assertEquals(listOf("m4", "m5"), page.items.map { it.id })
        assertTrue(page.hasMore)
        assertEquals("m4", page.nextBeforeMessageId)
    }

    @Test
    fun t14_observeMessagePage_beforeCursorReturnsOlderPageWithoutOverlap() = runTest {
        val lane = StandardTestDispatcher(testScheduler)
        val c = ctx(lane)
        seedMessages(c.db, "s1", 1, 5)
        val newest = c.session.observeMessagePage("s1", MessagePageRequest(limit = 2)).first()

        val older = c.session.observeMessagePage(
            "s1",
            MessagePageRequest(beforeMessageId = newest.nextBeforeMessageId, limit = 2),
        ).first()

        assertEquals(listOf("m2", "m3"), older.items.map { it.id })
        assertTrue(older.items.none { it.id in newest.items.map { m -> m.id } })
    }

    @Test
    fun t15_observeMessagePage_finalPageHasNoMoreAndNoCursor() = runTest {
        val lane = StandardTestDispatcher(testScheduler)
        val c = ctx(lane)
        seedMessages(c.db, "s1", 1, 3)

        val page = c.session.observeMessagePage("s1", MessagePageRequest(beforeMessageId = "m2", limit = 5)).first()

        assertEquals(listOf("m1"), page.items.map { it.id })
        assertFalse(page.hasMore)
        assertNull(page.nextBeforeMessageId)
    }

    @Test
    fun t16_observeMessagePage_concurrentInsertKeepsStableOlderPageOrdering() = runTest {
        val lane = StandardTestDispatcher(testScheduler)
        val c = ctx(lane)
        seedMessages(c.db, "s1", 1, 6)
        val top = c.session.observeMessagePage("s1", MessagePageRequest(limit = 2)).first()
        val req = MessagePageRequest(beforeMessageId = top.nextBeforeMessageId, limit = 2)
        val before = c.session.observeMessagePage("s1", req).first().items.map { it.id }
        insertMessage(c.db, "m7", "s1", 7)

        val after = c.session.observeMessagePage("s1", req).first().items.map { it.id }

        assertEquals(before, after)
        assertEquals(listOf("m3", "m4"), after)
    }

    @Test
    fun t17_toggleFavorite_persistsAndReemitsProjects() = runTest {
        val lane = StandardTestDispatcher(testScheduler)
        val c = ctx(lane)
        c.project.upsertProjects(listOf(project("p1", "Alpha")))

        val value = c.project.toggleFavorite("p1")
        val rows = c.project.observeProjects().first()

        assertTrue(value)
        assertTrue(rows.first().favorite)
    }

    @Test
    fun t18_toggleHidden_persistsAndReemitsProjects() = runTest {
        val lane = StandardTestDispatcher(testScheduler)
        val c = ctx(lane)
        c.project.upsertProjects(listOf(project("p1", "Alpha")))

        val value = c.project.toggleHidden("p1")
        val rows = c.project.observeProjects().first()

        assertTrue(value)
        assertTrue(rows.isEmpty())
    }

    @Test
    fun t19_select_updatesSelectedProjectAndPersistsAcrossReopen() = runTest {
        val lane = StandardTestDispatcher(testScheduler)
        val file = Files.createTempFile("repo-contract", ".db")
        val url = "jdbc:sqlite:${file.toAbsolutePath()}"

        val d1 = JdbcSqliteDriver(url)
        AppDatabase.Schema.create(d1)
        val db1 = AppDatabase(d1)
        val repo1 = SqlDelightProjectRepository(db1, lanes(lane), json())
        repo1.upsertProjects(listOf(project("p1", "Alpha"), project("p2", "Beta")))
        repo1.select("p2")
        d1.close()

        val d2 = JdbcSqliteDriver(url)
        val db2 = AppDatabase(d2)
        val repo2 = SqlDelightProjectRepository(db2, lanes(lane), json())

        assertEquals("p2", repo2.observeSelectedProject().first()?.id)
        d2.close()
    }

    @Test
    fun t20_replaceCommands_atomicallyReplacesProjectCatalog() = runTest {
        val lane = StandardTestDispatcher(testScheduler)
        val c = ctx(lane)
        c.command.replaceCommands(
            "p1",
            listOf(
                CommandState("build", "Build", "local"),
                CommandState("test", "Test", "local"),
            ),
        )

        c.command.replaceCommands("p1", listOf(CommandState("deploy", "Deploy", "remote")))
        val rows = c.command.observeCommands("p1").first()

        assertEquals(listOf("deploy"), rows.map { it.name })
    }

    @Test
    fun t21_find_returnsExactCommandAndNullWhenMissing() = runTest {
        val lane = StandardTestDispatcher(testScheduler)
        val c = ctx(lane)
        c.command.replaceCommands("p1", listOf(CommandState("build", "Build", "local")))

        val found = c.command.find("p1", "build")
        val miss = c.command.find("p1", "unknown")

        assertEquals("build", found?.name)
        assertNull(miss)
    }

    @Test
    fun t22_connectionMutations_emitInCommitOrder() = runTest {
        val lane = StandardTestDispatcher(testScheduler)
        val c = ctx(lane)
        val seen = mutableListOf<ConnectionSnapshot>()
        val job = backgroundScope.launch(lane) {
            c.connection.observeConnection().take(4).toList(seen)
        }
        advanceUntilIdle()

        c.connection.setEndpoint("https://api")
        c.connection.setStatus("CONNECTING")
        c.connection.recordDiscovery(listOf("https://found", "https://other"))
        advanceUntilIdle()
        job.join()

        assertEquals(ConnectionSnapshot(endpoint = "", discovered = null, status = "IDLE"), seen[0])
        assertEquals(ConnectionSnapshot(endpoint = "https://api", discovered = null, status = "IDLE"), seen[1])
        assertEquals(ConnectionSnapshot(endpoint = "https://api", discovered = null, status = "CONNECTING"), seen[2])
        assertEquals(ConnectionSnapshot(endpoint = "https://api", discovered = "https://found", status = "CONNECTING"), seen[3])
    }

    @Test
    fun t23_preferenceWrites_persistAndReemitPrefs() = runTest {
        val lane = StandardTestDispatcher(testScheduler)
        val c = ctx(lane)
        val seen = mutableListOf<PrefsState>()
        val job = backgroundScope.launch(lane) {
            c.prefs.observePrefs().take(5).toList(seen)
        }
        advanceUntilIdle()

        c.prefs.setQuickSwitchScope("workspace")
        c.prefs.setSortMode("name_asc")
        c.prefs.setLogsFilter("{\"level\":\"error\"}")
        c.prefs.setLogsRetentionPolicy("window_1d_max_10")
        advanceUntilIdle()
        job.join()

        val last = seen.last()
        assertEquals("workspace", last.quickSwitchScope)
        assertEquals("name_asc", last.sortMode)
        assertEquals("{\"level\":\"error\"}", last.logsFilterJson)
        assertEquals("window_1d_max_10", last.logsRetentionPolicy)
    }

    @Test
    fun t24_appendLog_writesRowAndObserveLogsReflectsFilter() = runTest {
        val lane = StandardTestDispatcher(testScheduler)
        val c = ctx(lane)
        c.log.append(log(1000, "match", unit = LogUnit.sync))
        c.log.append(log(1001, "other", unit = LogUnit.ui))

        val rows = c.log.observeLogs(LogFilter(event = "match"), LogPage(size = 20)).first()

        assertEquals(1, rows.size)
        assertEquals("match", rows.first().event)
    }

    @Test
    fun t25_observeFacets_updatesAfterMultipleAppends() = runTest {
        val lane = StandardTestDispatcher(testScheduler)
        val c = ctx(lane)
        c.log.append(log(1000, "sync_ok", projectId = "p1", projectName = "Alpha", sessionId = "s1", sessionTitle = "One"))
        c.log.append(log(1001, "sync_fail", projectId = "p2", projectName = "Beta", sessionId = "s2", sessionTitle = "Two"))
        c.log.append(log(1002, "sync_ok", projectId = "p1", projectName = "Alpha", sessionId = "s1", sessionTitle = "One"))

        val facet = c.log.observeFacets().first()

        assertEquals(2, facet.projects.size)
        assertEquals(2, facet.sessions.size)
        assertEquals(listOf("sync_fail", "sync_ok"), facet.events)
    }

    @Test
    fun t26_prune_removesRowsInsidePolicyAndKeepsRowsOutsideWindow() = runTest {
        val lane = StandardTestDispatcher(testScheduler)
        val c = ctx(lane)
        c.log.append(log(1, "old_event"))
        c.log.append(log(System.currentTimeMillis(), "new_event"))

        c.log.prune("window_1d_max_10")
        val rows = c.log.observeLogs(LogFilter(), LogPage(size = 20)).first()

        assertEquals(1, rows.size)
        assertEquals("new_event", rows.first().event)
    }

    @Test
    fun t27_observers_emitPostCommitStateOnly() = runTest {
        val lane = StandardTestDispatcher(testScheduler)
        val c = ctx(lane)
        upsertSession(c.db, id = "s1", project = "p1", title = "First", updatedAt = 100, focused = 1)
        upsertSession(c.db, id = "s2", project = "p1", title = "Second", updatedAt = 200, focused = 0)
        val seen = mutableListOf<String?>()
        val job = backgroundScope.launch(lane) {
            c.session.observeFocusedSession().map { it?.id }.take(2).toList(seen)
        }
        advanceUntilIdle()

        c.session.focus("s2")
        advanceUntilIdle()
        job.join()

        assertEquals(listOf("s1", "s2"), seen)
    }

    @Test
    fun t28_migrationPurge_clearsLegacyAndCanonicalRows() = runTest {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        createV6(driver)
        seedV6(driver)

        AppDatabase.Schema.migrate(driver, 6, AppDatabase.Schema.version)
        val db = AppDatabase(driver)

        assertNull(db.appDatabaseQueries.selectSetting("endpoint").executeAsOneOrNull())
        assertFalse(tableExists(driver, "session_cache"))
        assertFalse(tableExists(driver, "message_cache"))
        assertEquals(0L, db.appDatabaseQueries.countAppLog().executeAsOne())
        assertNull(db.appDatabaseQueries.selectProjectById("p1").executeAsOneOrNull())
        assertNull(db.appDatabaseQueries.observeConnection { endpoint, _, _ -> endpoint }.executeAsOneOrNull())
        assertNull(
            db.appDatabaseQueries.observePrefs { quickSwitchScope, _, _, _ -> quickSwitchScope }
                .executeAsOneOrNull()
        )
        driver.close()
    }

    private fun ctx(lane: TestDispatcher): Ctx {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        AppDatabase.Schema.create(driver)
        val db = AppDatabase(driver)
        val dispatchers = lanes(lane)
        val json = json()
        return Ctx(
            db = db,
            session = SqlDelightSessionRepository(db, dispatchers, json),
            project = SqlDelightProjectRepository(db, dispatchers, json),
            command = SqlDelightCommandRepository(db, dispatchers),
            connection = SqlDelightConnectionRepository(db, dispatchers),
            log = SqlDelightLogRepository(db, dispatchers, json),
            prefs = SqlDelightPreferencesRepository(db, dispatchers),
        )
    }

    private fun lanes(lane: TestDispatcher): DispatcherProvider {
        return object : DispatcherProvider {
            override val io = lane
            override val default = lane
            override val mainImmediate = Dispatchers.Unconfined
        }
    }

    private fun json(): Json {
        return Json { ignoreUnknownKeys = true }
    }

    private fun project(id: String, name: String): ProjectState {
        return ProjectState(
            id = id,
            worktree = "/$id",
            name = name,
            sandboxes = emptyList(),
            favorite = false,
        )
    }

    private fun upsertSession(
        db: AppDatabase,
        id: String,
        project: String,
        title: String,
        directory: String = "/$id",
        updatedAt: Long,
        archivedAt: Long? = null,
        focused: Long = 0,
    ) {
        db.appDatabaseQueries.upsertSession(
            id = id,
            project_id = project,
            title = title,
            version = "v1",
            directory = directory,
            parent_id = null,
            updated_at = updatedAt,
            archived_at = archivedAt,
            focused = focused,
        )
    }

    private fun seedMessages(db: AppDatabase, sessionId: String, from: Int, to: Int) {
        (from..to).forEach {
            insertMessage(db, "m$it", sessionId, it.toLong())
        }
    }

    private fun insertMessage(db: AppDatabase, id: String, sessionId: String, createdAt: Long) {
        db.appDatabaseQueries.insertMessage(
            id = id,
            session_id = sessionId,
            role = "assistant",
            text = id,
            sort_key = "%020d:%s".format(createdAt, id),
            created_at = createdAt,
            completed_at = null,
            local_message_id = null,
            pending = 0,
            updated_at = createdAt,
        )
    }

    private fun log(
        at: Long,
        event: String,
        unit: LogUnit = LogUnit.sync,
        projectId: String? = "p1",
        projectName: String? = "Project",
        sessionId: String? = "s1",
        sessionTitle: String? = "Session",
    ): LogRecord {
        return LogRecord(
            createdAt = at,
            level = LogLevel.info,
            unit = unit,
            tag = "Test",
            event = event,
            projectId = projectId,
            projectName = projectName,
            sessionId = sessionId,
            sessionTitle = sessionTitle,
            message = event,
            context = mapOf("k" to "v"),
            throwable = null,
            redacted = false,
        )
    }

    private fun createV6(driver: SqlDriver) {
        runSql(
            driver,
            "CREATE TABLE meta (id INTEGER NOT NULL PRIMARY KEY, value TEXT NOT NULL)",
        )
        runSql(
            driver,
            "CREATE TABLE settings (key TEXT NOT NULL PRIMARY KEY, value TEXT NOT NULL)",
        )
        runSql(
            driver,
            "CREATE TABLE session_cache (server_url TEXT NOT NULL, session_id TEXT NOT NULL, project_id TEXT, directory TEXT NOT NULL, title TEXT NOT NULL, version TEXT NOT NULL, last_opened_at INTEGER NOT NULL, updated_at INTEGER NOT NULL, PRIMARY KEY (server_url, session_id))",
        )
        runSql(
            driver,
            "CREATE TABLE message_cache (server_url TEXT NOT NULL, session_id TEXT NOT NULL, message_id TEXT NOT NULL, role TEXT NOT NULL, text TEXT NOT NULL, sort_key TEXT NOT NULL, created_at INTEGER, completed_at INTEGER, updated_at INTEGER NOT NULL, PRIMARY KEY (server_url, session_id, message_id))",
        )
        runSql(
            driver,
            "CREATE TABLE app_log (id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, created_at INTEGER NOT NULL, level TEXT NOT NULL, logical_unit TEXT NOT NULL, tag TEXT NOT NULL, event TEXT NOT NULL, project_id TEXT, project_name TEXT, session_id TEXT, session_title TEXT, message TEXT NOT NULL, context_json TEXT NOT NULL, throwable TEXT, redacted INTEGER NOT NULL)",
        )
    }

    private fun seedV6(driver: SqlDriver) {
        runSql(driver, "INSERT INTO meta(id, value) VALUES (1, 'm')")
        runSql(driver, "INSERT INTO settings(key, value) VALUES ('endpoint', 'https://legacy')")
        runSql(
            driver,
            "INSERT INTO session_cache(server_url, session_id, project_id, directory, title, version, last_opened_at, updated_at) VALUES ('srv', 's1', 'p1', '/tmp', 'Legacy', 'v1', 1, 2)",
        )
        runSql(
            driver,
            "INSERT INTO message_cache(server_url, session_id, message_id, role, text, sort_key, created_at, completed_at, updated_at) VALUES ('srv', 's1', 'm1', 'user', 'legacy', '1:m1', 1, NULL, 2)",
        )
        runSql(
            driver,
            "INSERT INTO app_log(created_at, level, logical_unit, tag, event, project_id, project_name, session_id, session_title, message, context_json, throwable, redacted) VALUES (1, 'info', 'sync', 'Test', 'legacy', 'p1', 'Legacy', 's1', 'Legacy', 'old', '{}', NULL, 0)",
        )
    }

    private fun runSql(driver: SqlDriver, sql: String) {
        driver.execute(null, sql, 0, null).value
    }

    private fun tableExists(driver: SqlDriver, name: String): Boolean {
        return driver.executeQuery(
            identifier = null,
            sql = "SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = ? LIMIT 1",
            mapper = {
                it.next()
            },
            parameters = 1,
            binders = {
                bindString(0, name)
            }
        ).value
    }

    private data class Ctx(
        val db: AppDatabase,
        val session: SqlDelightSessionRepository,
        val project: SqlDelightProjectRepository,
        val command: SqlDelightCommandRepository,
        val connection: SqlDelightConnectionRepository,
        val log: SqlDelightLogRepository,
        val prefs: SqlDelightPreferencesRepository,
    )
}
