package de.chennemann.opencode.mobile.ui.manage

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.chennemann.opencode.mobile.R
import de.chennemann.opencode.mobile.domain.session.ServerState
import de.chennemann.opencode.mobile.icons.ChevronDown
import de.chennemann.opencode.mobile.icons.ChevronUp
import de.chennemann.opencode.mobile.icons.Icons
import java.net.URI

@Composable
fun ServerCard(
    status: ServerState,
    url: String,
    urlError: String?,
    connecting: Boolean,
    discovered: String?,
    onConnect: (String) -> Unit,
) {
    val connected = status is ServerState.Connected
    var open by remember(connected) { mutableStateOf(!connected) }
    var edited by rememberSaveable { mutableStateOf(false) }
    var field by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(url))
    }
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current

    LaunchedEffect(url, edited) {
        if (edited) return@LaunchedEffect
        if (url == field.text) return@LaunchedEffect
        field = field.copy(text = url)
    }

    val shown = !connected || open
    val target = endpoint(field.text)
    val invalid = inputError(field.text, target)
    val error = when {
        connecting -> null
        edited -> invalid
        else -> urlError
    }
    val enabled = !connecting && target != null
    val connect = {
        val value = endpoint(field.text)
        if (value == null) {
            edited = true
        } else {
            edited = false
            onConnect(value)
            focus.clearFocus()
            keyboard?.hide()
        }
        Unit
    }

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Header(
                status = status,
                shown = shown,
                connected = connected,
                onToggle = { open = !open },
            )
            AnimatedVisibility(
                visible = shown,
                enter = expandVertically(animationSpec = tween(220)) + fadeIn(animationSpec = tween(160)),
                exit = shrinkVertically(animationSpec = tween(180)),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    UrlInput(
                        value = field,
                        error = error,
                        connecting = connecting,
                        discovered = discovered,
                        enabled = enabled,
                        onValueChange = {
                            field = it
                            edited = true
                        },
                        onDiscovered = {
                            field = field.copy(text = it)
                            edited = true
                        },
                        onConnect = connect,
                    )
                    ConnectButton(
                        connecting = connecting,
                        enabled = enabled,
                        onConnect = connect,
                    )
                    val failed = status as? ServerState.Failed
                    if (failed != null) {
                        Text(
                            text = failed.reason,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Header(
    status: ServerState,
    shown: Boolean,
    connected: Boolean,
    onToggle: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = connected, onClick = onToggle)
            .heightIn(min = 48.dp)
            .padding(horizontal = 4.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.manage_server_title),
            style = MaterialTheme.typography.titleMedium,
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (status is ServerState.Loading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(14.dp),
                    strokeWidth = 2.dp,
                )
            }
            Text(
                text = statusLabel(status),
                color = statusColor(status),
                style = MaterialTheme.typography.labelLarge,
            )
            if (connected) {
                Icon(
                    imageVector = if (shown) Icons.ChevronUp else Icons.ChevronDown,
                    contentDescription = if (shown) {
                        stringResource(R.string.manage_server_collapse)
                    } else {
                        stringResource(R.string.manage_server_expand)
                    },
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun UrlInput(
    value: TextFieldValue,
    error: String?,
    connecting: Boolean,
    discovered: String?,
    enabled: Boolean,
    onValueChange: (TextFieldValue) -> Unit,
    onDiscovered: (String) -> Unit,
    onConnect: () -> Unit,
) {
    TextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(stringResource(R.string.manage_server_url)) },
        singleLine = true,
        enabled = !connecting,
        isError = error != null,
        supportingText = {
            if (error != null) {
                Text(errorLabel(error))
            }
        },
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Uri,
            imeAction = ImeAction.Go,
        ),
        keyboardActions = KeyboardActions(
            onGo = {
                if (enabled) onConnect()
            },
        ),
        modifier = Modifier.fillMaxWidth(),
    )
    if (discovered != null) {
        OutlinedButton(
            onClick = { onDiscovered(discovered) },
            enabled = !connecting,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = stringResource(R.string.manage_server_discovered, discovered),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun ConnectButton(
    connecting: Boolean,
    enabled: Boolean,
    onConnect: () -> Unit,
) {
    Button(
        onClick = onConnect,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (connecting) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            }
            Text(
                text = if (connecting) {
                    stringResource(R.string.manage_server_connecting)
                } else {
                    stringResource(R.string.manage_server_connect)
                },
            )
        }
    }
}

@Composable
private fun statusLabel(state: ServerState): String {
    return when (state) {
        is ServerState.Idle -> stringResource(R.string.manage_server_status_idle)
        is ServerState.Loading -> stringResource(R.string.manage_server_status_connecting)
        is ServerState.Connected -> stringResource(R.string.manage_server_status_connected, state.version)
        is ServerState.Failed -> stringResource(R.string.manage_server_status_failed)
    }
}

@Composable
private fun errorLabel(value: String): String {
    return when (value) {
        "url_blank" -> stringResource(R.string.manage_server_error_blank)
        "url_invalid" -> stringResource(R.string.manage_server_error_invalid)
        "connection_loading", "connection_idle" -> stringResource(R.string.manage_server_error_connect)
        else -> value
    }
}

@Composable
private fun statusColor(state: ServerState): Color {
    return when (state) {
        is ServerState.Idle -> MaterialTheme.colorScheme.onSurfaceVariant
        is ServerState.Loading -> MaterialTheme.colorScheme.tertiary
        is ServerState.Connected -> MaterialTheme.colorScheme.primary
        is ServerState.Failed -> MaterialTheme.colorScheme.error
    }
}

private fun inputError(value: String, endpoint: String?): String? {
    if (value.trim().isBlank()) return "url_blank"
    if (endpoint == null) return "url_invalid"
    return null
}

private fun endpoint(value: String): String? {
    val text = value.trim()
    if (text.isBlank()) return null
    val url = if (text.startsWith("http://", true) || text.startsWith("https://", true)) {
        text
    } else {
        "http://$text"
    }
    val normalized = url.replace(TrailingSlashRegex, "")
    val uri = runCatching { URI(normalized) }.getOrNull() ?: return null
    val scheme = uri.scheme?.lowercase()
    if (scheme != "http" && scheme != "https") return null
    if (uri.host.isNullOrBlank() && uri.authority.isNullOrBlank()) return null
    return normalized
}

private val TrailingSlashRegex = Regex("/+$")
