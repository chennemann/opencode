package de.chennemann.opencode.mobile.domain.v2.servers

import de.chennemann.opencode.mobile.domain.v2.OpenCodeHealthCheck
import de.chennemann.opencode.mobile.domain.v2.OpenCodeProject
import de.chennemann.opencode.mobile.domain.v2.OpenCodeServerAdapter
import de.chennemann.opencode.mobile.domain.v2.OpenCodeSession
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ServerServiceTest {
    @Test
    fun connectReturnsTrueWhenServerIsHealthy() = runTest {
        val adapter = FakeOpenCodeServerAdapter(
            healthCheck = { OpenCodeHealthCheck(healthy = true, version = "1.0.0") },
        )
        val service = DefaultServerService(adapter)

        val connected = service.connect("  https://example.test  ")

        assertTrue(connected)
        assertEquals("https://example.test", adapter.lastHealthCheckBaseUrl)
    }

    @Test
    fun connectReturnsFalseWhenServerIsUnhealthy() = runTest {
        val adapter = FakeOpenCodeServerAdapter(
            healthCheck = { OpenCodeHealthCheck(healthy = false, version = "1.0.0") },
        )
        val service = DefaultServerService(adapter)

        val connected = service.connect("https://example.test")

        assertFalse(connected)
    }

    @Test
    fun connectReturnsFalseWhenHealthCheckFails() = runTest {
        val adapter = FakeOpenCodeServerAdapter(
            healthCheck = { throw IllegalStateException("network error") },
        )
        val service = DefaultServerService(adapter)

        val connected = service.connect("https://example.test")

        assertFalse(connected)
    }

    @Test
    fun connectReturnsFalseForBlankUrl() = runTest {
        val adapter = FakeOpenCodeServerAdapter(
            healthCheck = { OpenCodeHealthCheck(healthy = true, version = "1.0.0") },
        )
        val service = DefaultServerService(adapter)

        val connected = service.connect("   ")

        assertFalse(connected)
    }
}

private class FakeOpenCodeServerAdapter(
    private val healthCheck: suspend (String) -> OpenCodeHealthCheck,
) : OpenCodeServerAdapter {
    var lastHealthCheckBaseUrl: String? = null

    override suspend fun healthCheckWithUrl(baseUrl: String): OpenCodeHealthCheck {
        lastHealthCheckBaseUrl = baseUrl
        return healthCheck(baseUrl)
    }

    override suspend fun allProjects(baseUrl: String): List<OpenCodeProject> = emptyList()

    override suspend fun allSessionsOfAGivenProject(baseUrl: String, path: String): List<OpenCodeSession> = emptyList()
}
