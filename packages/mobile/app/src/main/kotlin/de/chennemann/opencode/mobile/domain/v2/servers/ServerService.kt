package de.chennemann.opencode.mobile.domain.v2.servers

import de.chennemann.opencode.mobile.domain.v2.OpenCodeServerAdapter

interface ServerService {
    suspend fun connect(url: String): Boolean
}

class DefaultServerService(
    private val adapter: OpenCodeServerAdapter,
) : ServerService {
    override suspend fun connect(url: String): Boolean {
        val baseUrl = url.trim()
        if (baseUrl.isBlank()) return false
        return runCatching {
            adapter.healthCheckWithUrl(baseUrl).healthy
        }.getOrDefault(false)
    }
}
