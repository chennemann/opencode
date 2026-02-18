package de.chennemann.opencode.mobile.ui.manage

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.chennemann.opencode.mobile.domain.session.ProjectState
import de.chennemann.opencode.mobile.icons.Add
import de.chennemann.opencode.mobile.icons.ChevronDown
import de.chennemann.opencode.mobile.icons.ChevronUp
import de.chennemann.opencode.mobile.icons.FilterList
import de.chennemann.opencode.mobile.icons.Icons
import de.chennemann.opencode.mobile.icons.Star
import de.chennemann.opencode.mobile.icons.StarOutline

@Composable
fun ProjectListCard(
    projectQuery: String,
    projectPath: String,
    loadingProjects: Boolean,
    projectsExpanded: Boolean,
    favoriteProjects: List<ProjectState>,
    otherProjects: List<ProjectState>,
    selectedProject: String?,
    onProjectQueryChange: (String) -> Unit,
    onProjectPathChange: (String) -> Unit,
    onOpenProject: () -> Unit,
    onProjectListToggle: () -> Unit,
    onProjectFavoriteToggle: (String) -> Unit,
    onProjectSelect: (String) -> Unit,
    onProjectRemove: (String) -> Unit,
) {
    var filterOpen by remember { mutableStateOf(projectQuery.isNotBlank()) }
    var pathOpen by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf(TextFieldValue(projectQuery)) }
    var path by remember { mutableStateOf(TextFieldValue(projectPath)) }

    LaunchedEffect(projectQuery) {
        if (projectQuery.isBlank()) return@LaunchedEffect
        filterOpen = true
    }

    LaunchedEffect(projectQuery) {
        if (projectQuery == query.text) return@LaunchedEffect
        query = TextFieldValue(
            text = projectQuery,
            selection = TextRange(projectQuery.length),
        )
    }

    LaunchedEffect(projectPath) {
        if (projectPath == path.text) return@LaunchedEffect
        path = TextFieldValue(
            text = projectPath,
            selection = TextRange(projectPath.length),
        )
    }

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
                Text("Projects", style = MaterialTheme.typography.titleMedium)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = { filterOpen = !filterOpen }) {
                        Icon(
                            imageVector = Icons.FilterList,
                            contentDescription = "Filter projects",
                            tint = if (filterOpen) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
                    IconButton(onClick = { pathOpen = !pathOpen }) {
                        Icon(
                            imageVector = Icons.Add,
                            contentDescription = "Add project path",
                            tint = if (pathOpen) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
                }
            }

            if (filterOpen) {
                TextField(
                    value = query,
                    onValueChange = {
                        query = it
                        onProjectQueryChange(it.text)
                    },
                    label = { Text("Search projects") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            if (pathOpen) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextField(
                        value = path,
                        onValueChange = {
                            path = it
                            onProjectPathChange(it.text)
                        },
                        label = { Text("Open project path") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Button(
                        onClick = {
                            if (projectPath != path.text) {
                                onProjectPathChange(path.text)
                            }
                            onOpenProject()
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Open path")
                    }
                }
            }

            if (favoriteProjects.isEmpty() && otherProjects.isEmpty()) {
                Text(
                    text = if (loadingProjects) "Loading projects..." else "No projects found",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                return@Column
            }

            val selected = workspaceId(selectedProject.orEmpty())
            if (favoriteProjects.isNotEmpty()) {
                favoriteProjects.chunked(2).forEach { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        row.forEach { project ->
                            ProjectCard(
                                project = project,
                                selected = selected == workspaceId(project.worktree),
                                compact = true,
                                modifier = Modifier.weight(1f),
                                onFavoriteToggle = { onProjectFavoriteToggle(project.worktree) },
                                onSelect = { onProjectSelect(project.worktree) },
                            )
                        }
                        if (row.size == 1) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }

            val removable = selectedProject?.takeIf { value ->
                (favoriteProjects + otherProjects)
                    .any { workspaceId(it.worktree) == workspaceId(value) }
            }

            if (otherProjects.isEmpty()) {
                if (removable != null) {
                    OutlinedButton(
                        onClick = { onProjectRemove(removable) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Remove selected project")
                    }
                }
                return@Column
            }

            val count = otherProjects.size
            val suffix = if (count == 1) "project" else "projects"
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onProjectListToggle)
                    .heightIn(min = 48.dp)
                    .padding(horizontal = 4.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = if (projectsExpanded) {
                        "Hide $count $suffix"
                    } else {
                        "$count $suffix hidden"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Icon(
                    imageVector = if (projectsExpanded) Icons.ChevronUp else Icons.ChevronDown,
                    contentDescription = if (projectsExpanded) {
                        "Collapse other projects"
                    } else {
                        "Expand other projects"
                    },
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (!projectsExpanded) {
                if (removable != null) {
                    OutlinedButton(
                        onClick = { onProjectRemove(removable) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Remove selected project")
                    }
                }
                return@Column
            }

            otherProjects.forEach {
                ProjectCard(
                    project = it,
                    selected = selected == workspaceId(it.worktree),
                    compact = false,
                    modifier = Modifier.fillMaxWidth(),
                    onFavoriteToggle = { onProjectFavoriteToggle(it.worktree) },
                    onSelect = { onProjectSelect(it.worktree) },
                )
            }
            if (removable != null) {
                OutlinedButton(
                    onClick = { onProjectRemove(removable) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Remove selected project")
                }
            }
        }
    }
}

@Composable
private fun ProjectCard(
    project: ProjectState,
    selected: Boolean,
    compact: Boolean,
    modifier: Modifier = Modifier,
    onFavoriteToggle: () -> Unit,
    onSelect: () -> Unit,
) {
    Card(
        modifier = modifier.clickable(onClick = onSelect),
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
                .padding(if (compact) 8.dp else 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = if (compact) Alignment.CenterVertically else Alignment.Top,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = if (compact) Arrangement.Center else Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = project.name,
                    style = if (compact) {
                        MaterialTheme.typography.labelLarge
                    } else {
                        MaterialTheme.typography.bodyLarge
                    },
                    maxLines = if (compact) 2 else 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!compact) {
                    Text(
                        text = project.worktree,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
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
        }
    }
}
