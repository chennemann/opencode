package de.chennemann.opencode.mobile.ui.manage

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.chennemann.opencode.mobile.domain.session.ProjectState
import de.chennemann.opencode.mobile.domain.session.ServerState
import de.chennemann.opencode.mobile.domain.session.SessionState
import de.chennemann.opencode.mobile.icons.Heart
import de.chennemann.opencode.mobile.icons.HeartOutline
import de.chennemann.opencode.mobile.icons.Icons
import de.chennemann.opencode.mobile.icons.Star
import de.chennemann.opencode.mobile.icons.StarOutline
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun ManageScreen(state: ManageUiState, onEvent: (ManageEvent) -> Unit) {
    val projects = state.favoriteProjects + state.otherProjects
    val selected = projects.firstOrNull { it.worktree == state.selectedProject }
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
            ServerCard(state, onEvent)
        }
        item("project-input") {
            ProjectInputCard(state, onEvent)
        }
        item("projects") {
            ProjectListCard(state, onEvent)
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
            OutlinedButton(
                onClick = { onEvent(ManageEvent.BackTapped) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Back")
            }
        }
    }
}

@Composable
private fun ServerCard(state: ManageUiState, onEvent: (ManageEvent) -> Unit) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Server", style = MaterialTheme.typography.titleMedium)
                Text(
                    text = statusLabel(state.status),
                    color = statusColor(state.status),
                    style = MaterialTheme.typography.labelLarge,
                )
            }
            TextField(
                value = state.url,
                onValueChange = { onEvent(ManageEvent.UrlChanged(it)) },
                label = { Text("Server URL") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            if (state.discovered != null) {
                OutlinedButton(
                    onClick = { onEvent(ManageEvent.UseDiscoveredTapped) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = "Use discovered: ${state.discovered}",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Button(
                onClick = { onEvent(ManageEvent.ConnectTapped) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Connect")
            }
        }
    }
}

@Composable
private fun ProjectInputCard(state: ManageUiState, onEvent: (ManageEvent) -> Unit) {
    Card {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Project Access", style = MaterialTheme.typography.titleMedium)
            TextField(
                value = state.projectPath,
                onValueChange = { onEvent(ManageEvent.ProjectPathChanged(it)) },
                label = { Text("Open project path") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = { onEvent(ManageEvent.OpenProjectTapped) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Open path")
            }
            TextField(
                value = state.projectQuery,
                onValueChange = { onEvent(ManageEvent.ProjectQueryChanged(it)) },
                label = { Text("Search projects") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun ProjectListCard(state: ManageUiState, onEvent: (ManageEvent) -> Unit) {
    Card {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Projects", style = MaterialTheme.typography.titleMedium)
            if (state.favoriteProjects.isEmpty() && state.otherProjects.isEmpty()) {
                Text(
                    text = if (state.loadingProjects) "Loading projects..." else "No projects found",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                return@Column
            }
            if (state.favoriteProjects.isNotEmpty()) {
                Text("Favorites", style = MaterialTheme.typography.labelLarge)
                state.favoriteProjects.forEach {
                    ProjectRow(
                        project = it,
                        selected = state.selectedProject == it.worktree,
                        onFavoriteToggle = { onEvent(ManageEvent.ProjectFavoriteToggled(it.worktree)) },
                        onSelect = { onEvent(ManageEvent.ProjectSelected(it.worktree)) },
                    )
                }
            }
            if (state.otherProjects.isEmpty()) {
                return@Column
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Other Projects", style = MaterialTheme.typography.labelLarge)
                OutlinedButton(onClick = { onEvent(ManageEvent.ProjectListToggleTapped) }) {
                    Text(if (state.projectsExpanded) "Collapse" else "Expand")
                }
            }
            if (!state.projectsExpanded) {
                val count = state.otherProjects.size
                val suffix = if (count == 1) "project" else "projects"
                Text(
                    text = "$count $suffix hidden. Tap Expand to show.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                return@Column
            }
            state.otherProjects.forEach {
                ProjectRow(
                    project = it,
                    selected = state.selectedProject == it.worktree,
                    onFavoriteToggle = { onEvent(ManageEvent.ProjectFavoriteToggled(it.worktree)) },
                    onSelect = { onEvent(ManageEvent.ProjectSelected(it.worktree)) },
                )
            }
        }
    }
}

@Composable
private fun ProjectRow(
    project: ProjectState,
    selected: Boolean,
    onFavoriteToggle: () -> Unit,
    onSelect: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.secondaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = project.name,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onFavoriteToggle) {
                Icon(
                    imageVector = if (project.favorite) Icons.Star else Icons.StarOutline,
                    contentDescription = if (project.favorite) "Unfavorite project" else "Favorite project",
                    tint = if (project.favorite) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
            OutlinedButton(
                onClick = onSelect,
                modifier = Modifier.width(92.dp),
            ) {
                Text(if (selected) "Selected" else "Open")
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
                OutlinedButton(
                    onClick = { onEvent(ManageEvent.LoadMoreSessionsTapped) },
                    enabled = !state.loadingSessions,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(if (state.sessionRecentOnly) "Load older" else "Load more")
                }
            }
            if (state.loadingSessions) {
                Text(
                    text = "Loading sessions...",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (state.sessionSections.isEmpty() && !state.loadingSessions) {
                Text(
                    text = if (state.sessionRecentOnly) {
                        "No sessions updated today or yesterday"
                    } else {
                        "No sessions found for this project"
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                return@Column
            }
            state.sessionSections.forEach { section ->
                Text(section.title, style = MaterialTheme.typography.labelLarge)
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
                                text = sessionSubtitle(session),
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
    }
}

private fun statusLabel(state: ServerState): String {
    return when (state) {
        is ServerState.Idle -> "Idle"
        is ServerState.Loading -> "Connecting"
        is ServerState.Connected -> "Connected ${state.version}"
        is ServerState.Failed -> "Failed"
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

private fun sessionSubtitle(session: SessionState): String {
    val updated = session.updatedAt?.let {
        val value = Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault())
        "Updated ${SessionFormatter.format(value)}"
    } ?: "Updated unknown"
    return "$updated | ${session.version}"
}

private val SessionFormatter = DateTimeFormatter.ofPattern("MMM d HH:mm")
private const val SessionsItemIndex = 4
