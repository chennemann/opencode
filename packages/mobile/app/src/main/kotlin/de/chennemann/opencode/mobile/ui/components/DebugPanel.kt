package de.chennemann.opencode.mobile.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.chennemann.opencode.mobile.home.DebugState

@Composable
fun DebugPanel(visible: Boolean, debug: DebugState) {
    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(initialOffsetY = { -it / 2 }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { -it / 2 }) + fadeOut(),
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            ),
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                SelectionContainer {
                    Text(
                        "SSE raw=${debug.sseRaw} seen=${debug.sseSeen} applied=${debug.sseApplied} dropped=${debug.sseDropped} connected=${debug.sseConnected} errors=${debug.sseErrors} sync=${debug.syncRuns}/${debug.syncFails}",
                    )
                }
                debug.lastDrop?.let {
                    SelectionContainer { Text("Last drop: $it") }
                }
                debug.lastStreamError?.let {
                    SelectionContainer { Text("Last stream error: $it") }
                }
            }
        }
    }
}
