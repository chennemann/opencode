package de.chennemann.opencode.mobile.data

import de.chennemann.opencode.mobile.api.apis.DefaultApi
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

data class Health(
    val healthy: Boolean,
    val version: String,
)

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

    suspend fun health(baseUrl: String): Health {
        val res = client(baseUrl).globalHealth()
        if (!res.success) {
            throw IllegalStateException("Server returned ${res.status}")
        }
        val body = res.response.bodyAsText()
        val obj = json.parseToJsonElement(body).jsonObject
        val healthy = obj["healthy"]?.jsonPrimitive?.booleanOrNull ?: false
        val version = obj["version"]?.jsonPrimitive?.content ?: "unknown"
        return Health(
            healthy = healthy,
            version = version,
        )
    }
}
