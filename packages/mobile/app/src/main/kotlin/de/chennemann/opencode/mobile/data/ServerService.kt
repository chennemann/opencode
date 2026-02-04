package de.chennemann.opencode.mobile.data

import de.chennemann.opencode.mobile.api.apis.DefaultApi
import de.chennemann.opencode.mobile.api.models.GlobalHealth200Response
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.json.Json

class ServerService(
    private val api: DefaultApi,
    private val json: Json,
) {
    suspend fun health(): GlobalHealth200Response {
        val res = api.globalHealth()
        if (!res.success) {
            throw IllegalStateException("Server returned ${res.status}")
        }
        val body = res.response.bodyAsText()
        return json.decodeFromString(GlobalHealth200Response.serializer(), body)
    }
}
