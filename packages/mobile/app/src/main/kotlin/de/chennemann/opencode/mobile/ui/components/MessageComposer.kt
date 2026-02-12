package de.chennemann.opencode.mobile.ui.components

import android.os.SystemClock
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupProperties
import de.chennemann.opencode.mobile.domain.session.CommandState
import de.chennemann.opencode.mobile.icons.Icons
import de.chennemann.opencode.mobile.icons.Send
import de.chennemann.opencode.mobile.ui.conversation.QuickSwitchState

@Composable
fun MessageComposer(
    draft: String,
    connected: Boolean,
    suggestions: List<CommandState>,
    quickSwitches: List<QuickSwitchState>,
    onDraftChange: (String) -> Unit,
    onSend: () -> Unit,
    onReload: () -> Unit,
    onCommandSelect: (CommandState) -> Unit,
    onQuickSwitch: (String) -> Unit,
) {
    var commandOpen by remember { mutableStateOf(false) }
    var dismissedAt by remember { mutableLongStateOf(0L) }
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(0.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            Box(modifier = Modifier.weight(1f)) {
                TextField(
                    value = draft,
                    onValueChange = {
                        onDraftChange(it)
                        commandOpen = commandOpen && (it.isBlank() || it.startsWith("/"))
                    },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Message") },
                    maxLines = 12,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        disabledContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        disabledIndicatorColor = Color.Transparent,
                    ),
                )

                if (draft.isEmpty()) {
                    DropdownMenu(
                        expanded = commandOpen && suggestions.isNotEmpty(),
                        onDismissRequest = {
                            commandOpen = false
                            dismissedAt = SystemClock.elapsedRealtime()
                        },
                        properties = PopupProperties(focusable = false),
                        modifier = Modifier
                            .fillMaxWidth(0.95f)
                            .heightIn(max = 280.dp),
                    ) {
                        suggestions.forEach { command ->
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text(
                                            text = "/${command.name}",
                                            style = MaterialTheme.typography.labelLarge,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        if (!command.description.isNullOrBlank()) {
                                            Text(
                                                text = command.description,
                                                style = MaterialTheme.typography.bodySmall,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                        }
                                    }
                                },
                                onClick = {
                                    onCommandSelect(command)
                                    commandOpen = false
                                },
                            )
                        }
                    }

                    Box(
                        Modifier
                            .align(Alignment.BottomEnd)
                            .padding(bottom = 4.dp)
                    ) {
                        FilledTonalIconButton(
                            onClick = {
                                val now = SystemClock.elapsedRealtime()
                                if (commandOpen) {
                                    commandOpen = false
                                    dismissedAt = now
                                    return@FilledTonalIconButton
                                }
                                if (now - dismissedAt < CommandReopenDelayMs) {
                                    return@FilledTonalIconButton
                                }
                                commandOpen = true
                            },
                            modifier = Modifier
                                .focusProperties { canFocus = false },
                            colors = if (commandOpen) {
                                IconButtonDefaults.filledTonalIconButtonColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                )
                            } else {
                                IconButtonDefaults.filledTonalIconButtonColors()
                            },
                        ) {
                            Text("/")
                        }
                    }
                }
            }

            if (connected) {
                Box(
                    Modifier
                        .align(Alignment.Bottom)
                        .padding(bottom = 4.dp)
                ) {
                    IconButton(
                        onClick = {
                            onSend()
                            if (draft.isNotBlank()) {
                                commandOpen = false
                                focus.clearFocus()
                                keyboard?.hide()
                            }
                        },
                        colors = IconButtonDefaults.iconButtonColors(
                            contentColor = MaterialTheme.colorScheme.primary,
                        ),
                        modifier = Modifier.size(48.dp),
                    ) {
                        Icon(Icons.Send, "")
                    }
                }
            } else {
                Button(
                    onClick = onReload,
                    modifier = Modifier.align(Alignment.Bottom),
                ) {
                    Text("Reload")
                }
            }
        }


        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (quickSwitches.isNotEmpty()) {
                LazyRow(
                    modifier = Modifier.padding(top = 8.dp, bottom = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    items(quickSwitches, key = { it.key }) { item ->
                        FilledTonalIconButton(
                            onClick = { onQuickSwitch(item.session.id) },
                            shape = CircleShape,
                            modifier = Modifier.size(36.dp),
                            colors = if (item.active) {
                                IconButtonDefaults.filledTonalIconButtonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary,
                                )
                            } else {
                                IconButtonDefaults.filledTonalIconButtonColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            },
                        ) {
                            Text(item.label)
                        }
                    }
                }
            }
        }
    }
}

private const val CommandReopenDelayMs = 500L
