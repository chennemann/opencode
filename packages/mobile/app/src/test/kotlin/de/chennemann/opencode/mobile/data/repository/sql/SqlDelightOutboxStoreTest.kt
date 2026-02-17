package de.chennemann.opencode.mobile.data.repository.sql

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import de.chennemann.opencode.mobile.db.AppDatabase
import de.chennemann.opencode.mobile.di.DispatcherProvider
import de.chennemann.opencode.mobile.domain.service.outbox.OutboxMutationInput
import de.chennemann.opencode.mobile.domain.service.outbox.OutboxType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SqlDelightOutboxStoreTest {
    @Test
    fun enqueueMessageWritesPendingRowSyncMarkerAndOutbox() = runTest {
        val lane = StandardTestDispatcher(testScheduler)
        val db = db()
        val store = SqlDelightOutboxStore(db, lanes(lane))

        val ref = store.enqueueMessage(
            OutboxMutationInput(
                outboxId = "o-1",
                type = OutboxType.SEND_MESSAGE,
                sessionId = "s-1",
                directory = "/repo/main",
                text = "hello",
                payloadJson = """{"text":"hello","agent":"build"}""",
                createdAt = 100,
            )
        )

        val message = db.appDatabaseQueries.selectMessageById(ref.localMessageId).executeAsOneOrNull()
        val outbox = db.appDatabaseQueries.selectOutboxById("o-1").executeAsOneOrNull()
        val sync = syncMeta(db)

        assertNotNull(message)
        assertEquals(1L, message?.pending)
        assertEquals(ref.localMessageId, message?.local_message_id)
        assertEquals("hello", message?.text)
        assertNotNull(outbox)
        assertEquals(ref.localMessageId, outbox?.local_message_id)
        assertEquals("SEND_MESSAGE", outbox?.type)
        assertNotNull(sync)
        assertEquals("s-1", sync?.sessionId)
        assertEquals("QUEUED", sync?.status)
        assertEquals(100L, sync?.nextSyncAt)
        assertEquals(100L, sync?.updatedAt)
    }

    @Test
    fun enqueueMessageRollsBackMessageAndSyncOnOutboxFailure() = runTest {
        val lane = StandardTestDispatcher(testScheduler)
        val db = db()
        val store = SqlDelightOutboxStore(db, lanes(lane))

        val first = store.enqueueMessage(
            OutboxMutationInput(
                outboxId = "o-dup",
                type = OutboxType.SEND_MESSAGE,
                sessionId = "s-1",
                directory = "/repo/main",
                text = "first",
                payloadJson = """{"text":"first","agent":"build"}""",
                createdAt = 100,
            )
        )
        val beforeMessages = messageTexts(db)
        val beforeSync = syncMeta(db)

        val failed = runCatching {
            store.enqueueMessage(
                OutboxMutationInput(
                    outboxId = "o-dup",
                    type = OutboxType.SEND_MESSAGE,
                    sessionId = "s-1",
                    directory = "/repo/main",
                    text = "second",
                    payloadJson = """{"text":"second","agent":"build"}""",
                    createdAt = 200,
                )
            )
        }

        val afterMessages = messageTexts(db)
        val afterSync = syncMeta(db)
        val outbox = db.appDatabaseQueries.selectOutboxById("o-dup").executeAsOneOrNull()

        assertTrue(failed.isFailure)
        assertEquals(beforeMessages, afterMessages)
        assertFalse(afterMessages.contains("second"))
        assertEquals(beforeSync, afterSync)
        assertNotNull(outbox)
        assertEquals(first.localMessageId, outbox?.local_message_id)
    }
}

private data class SyncMeta(
    val sessionId: String,
    val status: String,
    val nextSyncAt: Long,
    val updatedAt: Long,
)

private fun syncMeta(db: AppDatabase): SyncMeta? {
    return db.appDatabaseQueries
        .listDueSyncState(now = Long.MAX_VALUE, limit = 10) { sessionId, status, _, _, _, nextSyncAt, _, _, updatedAt ->
            SyncMeta(
                sessionId = sessionId,
                status = status,
                nextSyncAt = nextSyncAt,
                updatedAt = updatedAt,
            )
        }
        .executeAsList()
        .firstOrNull()
}

private fun messageTexts(db: AppDatabase): List<String> {
    return db.appDatabaseQueries
        .observeMessagePage(session_id = "s-1", before_message_id = null, limit = 100) { _, _, text, _, _, _ -> text }
        .executeAsList()
}

private fun db(): AppDatabase {
    val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
    AppDatabase.Schema.create(driver)
    return AppDatabase(driver)
}

private fun lanes(lane: TestDispatcher): DispatcherProvider {
    return object : DispatcherProvider {
        override val io = lane
        override val default = lane
        override val mainImmediate = Dispatchers.Unconfined
    }
}
