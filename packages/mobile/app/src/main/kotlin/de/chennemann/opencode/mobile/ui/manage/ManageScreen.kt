package de.chennemann.opencode.mobile.ui.manage

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun ManageScreen(state: ManageUiState, onEvent: (ManageEvent) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Manage Sessions")
        TextField(
            value = state.url,
            onValueChange = {
                onEvent(ManageEvent.UrlChanged(it))
            },
            label = { Text("Server URL") },
            singleLine = true,
        )
        if (state.discovered != null) {
            Button(onClick = { onEvent(ManageEvent.UseDiscoveredTapped) }) {
                Text("Use discovered: ${state.discovered}")
            }
        }
        Button(onClick = { onEvent(ManageEvent.ConnectTapped) }) {
            Text("Connect")
        }
        Button(onClick = { onEvent(ManageEvent.LoadProjectsTapped) }) {
            Text(if (state.loadingProjects) "Loading projects..." else "Load projects")
        }
        if (state.projects.isNotEmpty()) {
            Text("Projects")
            state.projects.forEach { project ->
                Button(onClick = { onEvent(ManageEvent.ProjectSelected(project.worktree)) }) {
                    val marker = if (state.selectedProject == project.worktree) "* " else ""
                    Text("$marker${project.name}")
                }
            }
        }
        if (state.selectedProject != null) {
            if (state.loadingSessions) {
                SelectionContainer { Text("Loading sessions...") }
            }
            Button(onClick = { onEvent(ManageEvent.CreateSessionTapped) }) {
                Text(if (state.loadingSessions) "Creating session..." else "New session")
            }
        }
        if (state.sessions.isNotEmpty()) {
            Text("Sessions")
            state.sessions.forEach { session ->
                Button(onClick = { onEvent(ManageEvent.OpenSessionTapped(session)) }) {
                    Text("${session.title} (${session.version})")
                }
            }
        } else if (state.selectedProject != null && !state.loadingSessions) {
            SelectionContainer { Text("No sessions found for selected project") }
        }
        Button(onClick = { onEvent(ManageEvent.BackTapped) }) {
            Text("Back")
        }
    }
}
