package de.chennemann.opencode.mobile.domain.usecase.connection

import de.chennemann.opencode.mobile.domain.service.connection.ConnectionActionService
import de.chennemann.opencode.mobile.domain.service.model.RefreshInput
import java.net.URI

data class RefreshServerResult(
    val accepted: Boolean,
    val endpoint: String?,
    val reason: String?,
)

class RefreshServerUseCase(
    private val action: ConnectionActionService,
) {
    suspend operator fun invoke(url: String): RefreshServerResult {
        val value = url.trim()
        if (value.isBlank()) {
            return RefreshServerResult(
                accepted = false,
                endpoint = null,
                reason = "url_blank",
            )
        }
        val endpoint = endpoint(value)
        if (endpoint == null) {
            return RefreshServerResult(
                accepted = false,
                endpoint = null,
                reason = "url_invalid",
            )
        }
        val result = action.refresh(
            RefreshInput(
                endpoint = endpoint,
                userInitiated = true,
            )
        )
        return RefreshServerResult(
            accepted = result.accepted,
            endpoint = endpoint,
            reason = result.reason,
        )
    }
}

private fun endpoint(value: String): String? {
    val url = if (value.startsWith("http://", true) || value.startsWith("https://", true)) {
        value
    } else {
        "http://$value"
    }
    val normalized = url.replace(TrailingSlashRegex, "")
    val uri = runCatching { URI(normalized) }.getOrNull() ?: return null
    val scheme = uri.scheme?.lowercase()
    if (scheme != "http" && scheme != "https") return null
    if (uri.host.isNullOrBlank()) return null
    return normalized
}

private val TrailingSlashRegex = Regex("/+$")
