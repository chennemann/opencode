package de.chennemann.opencode.mobile.ui.manage

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.chennemann.opencode.mobile.domain.session.SessionState
import de.chennemann.opencode.mobile.icons.Heart
import de.chennemann.opencode.mobile.icons.HeartOutline
import de.chennemann.opencode.mobile.icons.Icons
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun ManageScreen(state: ManageUiState, onEvent: (ManageEvent) -> Unit) {
    val projects = state.favoriteProjects + state.otherProjects
    val selected = projects.firstOrNull {
        workspaceId(it.worktree) == workspaceId(state.selectedProject.orEmpty())
    }
    val selectedWorktree = state.selectedProject
    val selectedName = state.selectedProjectName ?: selectedWorktree
    val selectedFavorite = selected?.favorite == true
    val list = rememberLazyListState()
    LaunchedEffect(state.sessionScroll) {
        if (state.sessionScroll == 0L) return@LaunchedEffect
        list.animateScrollToItem(SessionsItemIndex)
    }
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 24.dp),
        state = list,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item("title") {
            Text(
                text = "Workspace Hub",
                style = MaterialTheme.typography.headlineSmall,
            )
        }
        item("server") {
            ServerCard(
                status = state.status,
                url = state.url,
                urlError = state.urlError,
                connecting = state.connecting,
                discovered = state.discovered,
                onConnect = { onEvent(ManageEvent.ConnectTapped(it)) },
            )
        }
        item("projects") {
            ProjectListCard(
                projectQuery = state.projectQuery,
                projectPath = state.projectPath,
                loadingProjects = state.loadingProjects,
                projectsExpanded = state.projectsExpanded,
                favoriteProjects = state.favoriteProjects,
                otherProjects = state.otherProjects,
                selectedProject = state.selectedProject,
                onProjectQueryChange = { onEvent(ManageEvent.ProjectQueryChanged(it)) },
                onProjectPathChange = { onEvent(ManageEvent.ProjectPathChanged(it)) },
                onOpenProject = { onEvent(ManageEvent.OpenProjectTapped) },
                onProjectListToggle = { onEvent(ManageEvent.ProjectListToggleTapped) },
                onProjectFavoriteToggle = { onEvent(ManageEvent.ProjectFavoriteToggled(it)) },
                onProjectSelect = { onEvent(ManageEvent.ProjectSelected(it)) },
                onProjectRemove = { onEvent(ManageEvent.ProjectRemoved(it)) },
            )
        }
        item("sessions") {
            SessionCard(
                state = state,
                selectedName = selectedName,
                selectedWorktree = selectedWorktree,
                selectedFavorite = selectedFavorite,
                onEvent = onEvent,
            )
        }
        if (!state.message.isNullOrBlank()) {
            item("message") {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                    ),
                ) {
                    Text(
                        text = state.message.orEmpty(),
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(12.dp),
                    )
                }
            }
        }
        item("back") {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = { onEvent(ManageEvent.OpenLogsTapped) },
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Logs")
                }
                OutlinedButton(
                    onClick = { onEvent(ManageEvent.BackTapped) },
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Back")
                }
            }
        }
    }
}

@Composable
private fun SessionCard(
    state: ManageUiState,
    selectedName: String?,
    selectedWorktree: String?,
    selectedFavorite: Boolean,
    onEvent: (ManageEvent) -> Unit,
) {
    var workspaceMenu by remember { mutableStateOf(false) }
    Card {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Project Sessions", style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = selectedName ?: "Choose a project above to view its sessions",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (selectedWorktree != null) {
                    IconButton(
                        onClick = { onEvent(ManageEvent.ProjectFavoriteToggled(selectedWorktree)) },
                    ) {
                        Icon(
                            imageVector = if (selectedFavorite) Icons.Heart else Icons.HeartOutline,
                            contentDescription = if (selectedFavorite) {
                                "Unfavorite selected project"
                            } else {
                                "Favorite selected project"
                            },
                            tint = if (selectedFavorite) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
                }
            }
            if (selectedWorktree == null) {
                return@Column
            }
            if (state.loadingSessions) {
                Text(
                    text = "Loading sessions...",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (state.sessionSections.isEmpty() && !state.loadingSessions) {
                Text(
                    text = "No sessions found for this project",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                state.sessionSections.forEach { section ->
                    Text(section.workspace.title, style = MaterialTheme.typography.labelLarge)
                    section.sessions.forEach { session ->
                        OutlinedButton(
                            onClick = { onEvent(ManageEvent.OpenSessionTapped(session)) },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Text(
                                    text = session.title,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = sessionSubtitle(session, selectedWorktree),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = { onEvent(ManageEvent.CreateSessionTapped) },
                    enabled = !state.loadingSessions,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(if (state.loadingSessions) "Creating..." else "New session")
                }
                Box(modifier = Modifier.weight(1f)) {
                    OutlinedButton(
                        onClick = { workspaceMenu = true },
                        enabled = !state.loadingSessions && state.workspaceOptions.isNotEmpty(),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = "Create in: ${state.selectedWorkspaceName ?: "Local"}",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    DropdownMenu(
                        expanded = workspaceMenu,
                        onDismissRequest = { workspaceMenu = false },
                    ) {
                        state.workspaceOptions.forEach { option ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = option.title,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                },
                                onClick = {
                                    workspaceMenu = false
                                    onEvent(ManageEvent.WorkspaceSelected(option.directory))
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun sessionSubtitle(session: SessionState, selectedWorktree: String?): String {
    val updated = session.updatedAt?.let {
        val value = Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault())
        "Updated ${SessionFormatter.format(value)}"
    } ?: "Updated unknown"
    val workspace = if (selectedWorktree.isNullOrBlank()) {
        false
    } else {
        workspaceId(session.directory) != workspaceId(selectedWorktree)
    }
    val prefix = if (workspace) "Workspace session • " else ""
    return "$prefix$updated | ${session.version}"
}

internal fun workspaceId(path: String): String {
    val value = path.trimEnd('/', '\\')
    if (value.isBlank()) return path
    return value
}

private val SessionFormatter = DateTimeFormatter.ofPattern("MMM d HH:mm")
private const val SessionsItemIndex = 3
