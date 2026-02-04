package de.chennemann.opencode.mobile.data

import de.chennemann.opencode.mobile.api.apis.DefaultApi
import de.chennemann.opencode.mobile.api.models.GlobalHealth200Response
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.json.Json

class ServerService(
    private val json: Json,
    private val engine: HttpClientEngine,
) {
    private var url: String? = null
    private var api: DefaultApi? = null

    private fun client(next: String): DefaultApi {
        if (url == next && api != null) return api!!
        val created = DefaultApi(
            baseUrl = next,
            httpClientEngine = engine,
        )
        api = created
        url = next
        return created
    }

    suspend fun health(baseUrl: String): GlobalHealth200Response {
        val res = client(baseUrl).globalHealth()
        if (!res.success) {
            throw IllegalStateException("Server returned ${res.status}")
        }
        val body = res.response.bodyAsText()
        return json.decodeFromString(GlobalHealth200Response.serializer(), body)
    }
}
