package de.chennemann.opencode.mobile.data

import de.chennemann.opencode.mobile.api.apis.DefaultApi
import de.chennemann.opencode.mobile.api.models.SessionCreateRequest
import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.utils.io.readAvailable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.math.BigDecimal
import kotlinx.serialization.json.put

data class Health(
    val healthy: Boolean,
    val version: String,
)

data class ProjectInfo(
    val id: String,
    val worktree: String,
    val name: String,
)

data class SessionInfo(
    val id: String,
    val title: String,
    val version: String,
    val directory: String,
)

data class SessionMessageInfo(
    val id: String,
    val role: String,
    val text: String,
)

data class GlobalStreamEvent(
    val directory: String,
    val type: String,
    val properties: JsonObject,
    val id: String?,
    val retry: Int?,
)

class ServerService(
    private val json: Json,
    private val engine: HttpClientEngine,
) {
    private val http = HttpClient(engine)
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

    suspend fun projects(baseUrl: String): List<ProjectInfo> {
        val res = client(baseUrl).projectList(null)
        if (!res.success) {
            throw IllegalStateException("Server returned ${res.status}")
        }
        return json
            .parseToJsonElement(res.response.bodyAsText())
            .jsonArray
            .mapNotNull {
                val obj = it.jsonObject
                val id = obj["id"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                val worktree = obj["worktree"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                val name = obj["name"]?.jsonPrimitive?.contentOrNull ?: worktree
                ProjectInfo(
                    id = id,
                    worktree = worktree,
                    name = name,
                )
            }
    }

    suspend fun sessions(baseUrl: String, worktree: String): List<SessionInfo> {
        val res = client(baseUrl).sessionList(worktree, true, null, null, null)
        if (!res.success) {
            throw IllegalStateException("Server returned ${res.status}")
        }
        return json
            .parseToJsonElement(res.response.bodyAsText())
            .jsonArray
            .mapNotNull {
                val obj = it.jsonObject
                val id = obj["id"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                val title = obj["title"]?.jsonPrimitive?.contentOrNull ?: "Session"
                val version = obj["version"]?.jsonPrimitive?.contentOrNull ?: "unknown"
                val directory = obj["directory"]?.jsonPrimitive?.contentOrNull ?: ""
                SessionInfo(
                    id = id,
                    title = title,
                    version = version,
                    directory = directory,
                )
            }
    }

    suspend fun createSession(baseUrl: String, worktree: String, title: String): SessionInfo {
        val res = client(baseUrl).sessionCreate(
            worktree,
            SessionCreateRequest(title = title),
        )
        if (!res.success) {
            throw IllegalStateException("Server returned ${res.status}")
        }
        val obj = json.parseToJsonElement(res.response.bodyAsText()).jsonObject
        val id = obj["id"]?.jsonPrimitive?.contentOrNull ?: throw IllegalStateException("Invalid session payload")
        val value = obj["title"]?.jsonPrimitive?.contentOrNull ?: "Session"
        val version = obj["version"]?.jsonPrimitive?.contentOrNull ?: "unknown"
        val directory = obj["directory"]?.jsonPrimitive?.contentOrNull ?: ""
        return SessionInfo(
            id = id,
            title = value,
            version = version,
            directory = directory,
        )
    }

    suspend fun sessionMessages(baseUrl: String, sessionId: String, directory: String, limit: Int?): List<SessionMessageInfo> {
        val res = client(baseUrl).sessionMessages(sessionId, directory, limit?.let(::BigDecimal))
        if (!res.success) {
            throw IllegalStateException("Server returned ${res.status}")
        }
        return json
            .parseToJsonElement(res.response.bodyAsText())
            .jsonArray
            .mapNotNull {
                val row = it.jsonObject
                val info = row["info"]?.jsonObject ?: return@mapNotNull null
                val id = info["id"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                val role = info["role"]?.jsonPrimitive?.contentOrNull ?: "assistant"
                val parts = row["parts"]?.jsonArray ?: return@mapNotNull null
                val text = parts
                    .mapNotNull {
                        val obj = it.jsonObject
                        val type = obj["type"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                        if (type != "text") return@mapNotNull null
                        obj["text"]?.jsonPrimitive?.contentOrNull
                    }
                    .filter { it.isNotBlank() }
                    .joinToString("\n")
                val value = if (text.isBlank()) {
                    val tags = parts
                        .mapNotNull { it.jsonObject["type"]?.jsonPrimitive?.contentOrNull }
                        .distinct()
                        .joinToString(", ")
                    if (tags.isBlank()) "(empty)" else "[$tags]"
                } else {
                    text
                }
                SessionMessageInfo(
                    id = id,
                    role = role,
                    text = value,
                )
            }
    }

    suspend fun streamEvents(
        baseUrl: String,
        lastEventId: String?,
        onRawEvent: suspend () -> Unit,
        onEvent: suspend (GlobalStreamEvent) -> Unit,
    ): String? {
        val res = http.get("$baseUrl/global/event") {
            header(HttpHeaders.Accept, "text/event-stream")
            header(HttpHeaders.CacheControl, "no-cache")
            if (!lastEventId.isNullOrBlank()) {
                header("Last-Event-ID", lastEventId)
            }
        }
        if (res.status.value !in 200..299) {
            throw IllegalStateException("Server returned ${res.status}")
        }

        var cursor = lastEventId
        val bytes = ByteArray(8192)
        var buffer = ""

        suspend fun flush(chunk: String) {
            if (chunk.isBlank()) return
            onRawEvent()

            val lines = chunk.split("\n")
            val data = mutableListOf<String>()
            var id: String? = null
            var retry: Int? = null

            lines.forEach { line ->
                if (line.startsWith(":")) return@forEach
                if (line.startsWith("data:")) {
                    data.add(line.removePrefix("data:").trimStart())
                    return@forEach
                }
                if (line.startsWith("id:")) {
                    id = line.removePrefix("id:").trim()
                    return@forEach
                }
                if (line.startsWith("retry:")) {
                    retry = line.removePrefix("retry:").trim().toIntOrNull()
                }
            }

            if (data.isEmpty()) return

            val parsed = runCatching { json.parseToJsonElement(data.joinToString("\n")).jsonObject }
            val root = parsed.getOrNull() ?: return
            val payload = root["payload"]?.jsonObject ?: return
            val type = payload["type"]?.jsonPrimitive?.contentOrNull ?: return
            val properties = payload["properties"]?.jsonObject ?: JsonObject(emptyMap())

            if (!id.isNullOrBlank()) {
                cursor = id
            }

            onEvent(
                GlobalStreamEvent(
                    directory = root["directory"]?.jsonPrimitive?.contentOrNull ?: "global",
                    type = type,
                    properties = properties,
                    id = id,
                    retry = retry,
                )
            )
        }

        val channel = res.bodyAsChannel()
        while (!channel.isClosedForRead) {
            val read = channel.readAvailable(bytes, 0, bytes.size)
            if (read <= 0) continue

            buffer += bytes.decodeToString(endIndex = read)
            buffer = buffer.replace("\r\n", "\n").replace("\r", "\n")

            while (true) {
                val split = buffer.indexOf("\n\n")
                if (split < 0) break
                val chunk = buffer.substring(0, split)
                buffer = buffer.substring(split + 2)
                flush(chunk)
            }
        }

        if (buffer.isNotBlank()) {
            flush(buffer)
        }

        return cursor
    }

    suspend fun sendMessage(baseUrl: String, sessionId: String, directory: String, text: String) {
        val res = http.post("$baseUrl/session/$sessionId/prompt_async") {
            parameter("directory", directory)
            contentType(ContentType.Application.Json)
            setBody(
                buildJsonObject {
                    put(
                        "parts",
                        buildJsonArray {
                            add(
                                buildJsonObject {
                                    put("type", "text")
                                    put("text", text)
                                }
                            )
                        }
                    )
                }.toString()
            )
        }
        if (res.status.value !in 200..299) {
            throw IllegalStateException("Server returned ${res.status}")
        }
    }
}
