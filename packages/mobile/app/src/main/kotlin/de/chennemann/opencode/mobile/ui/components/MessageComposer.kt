package de.chennemann.opencode.mobile.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
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
    commandOpen: Boolean,
    quickSwitches: List<QuickSwitchState>,
    onDraftChange: (String) -> Unit,
    onSend: () -> Unit,
    onReload: () -> Unit,
    onCommandSelect: (CommandState) -> Unit,
    onCommandToggle: () -> Unit,
    onCommandDismiss: () -> Unit,
    onQuickSwitch: (String) -> Unit,
) {
    val slashInteraction = remember { MutableInteractionSource() }
    val slashPressed by slashInteraction.collectIsPressedAsState()
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        FilledTonalIconButton(
            onClick = {
                if (commandOpen) {
                    onCommandDismiss()
                    return@FilledTonalIconButton
                }
                onCommandToggle()
            },
            interactionSource = slashInteraction,
            modifier = Modifier
                .align(Alignment.Bottom)
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
        Box(modifier = Modifier.weight(1f)) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TextField(
                    value = draft,
                    onValueChange = onDraftChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Message") },
                )
                if (quickSwitches.isNotEmpty()) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(quickSwitches, key = { it.key }) { item ->
                            FilledTonalIconButton(
                                onClick = { onQuickSwitch(item.session.id) },
                                shape = CircleShape,
                                modifier = Modifier.size(36.dp),
                            ) {
                                Text(item.label)
                            }
                        }
                    }
                }
            }
            DropdownMenu(
                expanded = commandOpen && suggestions.isNotEmpty(),
                onDismissRequest = {
                    if (!slashPressed) {
                        onCommandDismiss()
                    }
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
