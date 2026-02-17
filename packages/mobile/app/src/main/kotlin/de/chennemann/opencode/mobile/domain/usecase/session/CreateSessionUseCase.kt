package de.chennemann.opencode.mobile.domain.usecase.session

import de.chennemann.opencode.mobile.data.repository.ProjectRepository
import de.chennemann.opencode.mobile.data.repository.SessionRepository
import de.chennemann.opencode.mobile.data.repository.SyncReason
import de.chennemann.opencode.mobile.domain.session.ProjectGateway
import kotlinx.coroutines.flow.first

class CreateSessionUseCase(
    private val project: ProjectGateway,
    private val projects: ProjectRepository,
    private val session: SessionRepository,
) {
    suspend operator fun invoke(worktree: String): Boolean {
        val directory = worktree.trim()
        if (directory.isBlank()) return false
        val created = runCatching {
            project.createSession(directory, NewSessionTitle)
        }.getOrNull() ?: return false
        val rows = projects.observeProjects().first()
        val selected = rows.firstOrNull {
            workspaceId(it.worktree) == workspaceId(directory) || it.sandboxes.any { sandbox -> workspaceId(sandbox) == workspaceId(directory) }
        }
        if (selected != null) {
            projects.select(selected.id)
        }
        session.focus(created.id)
        session.requestSync(created.id, SyncReason.FOCUS)
        return true
    }
}

private fun workspaceId(path: String): String {
    return path.trimEnd('/', '\\')
}

private const val NewSessionTitle = "New Session"
