package de.chennemann.opencode.mobile.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import de.chennemann.opencode.mobile.domain.session.CommandState
import de.chennemann.opencode.mobile.icons.Icons
import de.chennemann.opencode.mobile.icons.Send

@Composable
fun MessageComposer(
    draft: String,
    connected: Boolean,
    suggestions: List<CommandState>,
    commandOpen: Boolean,
    onDraftChange: (String) -> Unit,
    onSend: () -> Unit,
    onReload: () -> Unit,
    onCommandSelect: (CommandState) -> Unit,
    onCommandToggle: () -> Unit,
    onCommandDismiss: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        FilledTonalIconButton(
            onClick = onCommandToggle,
            modifier = Modifier.align(Alignment.Bottom),
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
        Box(modifier = Modifier.weight(1f)) {
            TextField(
                value = draft,
                onValueChange = onDraftChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Message") },
            )
            DropdownMenu(
                expanded = suggestions.isNotEmpty(),
                onDismissRequest = onCommandDismiss,
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
                        onClick = { onCommandSelect(command) },
                    )
                }
            }
        }
        if (connected) {
            IconButton(
                onClick = onSend,
                colors = IconButtonDefaults.iconButtonColors(
                    contentColor = MaterialTheme.colorScheme.primary,
                ),
                modifier = Modifier.align(Alignment.Bottom),
            ) {
                Icon(Icons.Send, "")
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
}
