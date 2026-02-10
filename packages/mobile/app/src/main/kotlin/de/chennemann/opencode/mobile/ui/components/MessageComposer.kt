package de.chennemann.opencode.mobile.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.chennemann.opencode.mobile.icons.Icons
import de.chennemann.opencode.mobile.icons.Send

@Composable
fun MessageComposer(
    draft: String,
    connected: Boolean,
    onDraftChange: (String) -> Unit,
    onSend: () -> Unit,
    onReload: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        TextField(
            value = draft,
            onValueChange = onDraftChange,
            modifier = Modifier.weight(1f),
            label = { Text("Message") },
        )
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
