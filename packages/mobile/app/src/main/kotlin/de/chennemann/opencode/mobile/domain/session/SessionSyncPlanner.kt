package de.chennemann.opencode.mobile.domain.session

data class IncomingMessage(
    val id: String,
    val role: String,
    val text: String,
)

data class SessionSyncPlan(
    val claimed: Boolean,
    val upserts: List<MessageState>,
    val removedIds: List<String>,
    val sorts: Map<String, String>,
)

class SessionSyncPlanner {
    fun plan(
        incoming: List<IncomingMessage>,
        cached: List<MessageState>,
        sticky: MutableMap<String, String>?,
        knownSort: (String) -> String?,
        claimPendingSort: (String) -> String?,
        nextSort: () -> String,
        retainRemoved: (String) -> Boolean,
        complete: Boolean,
    ): SessionSyncPlan {
        val cache = cached.associateBy { it.id }
        var claimed = false
        val upserts = mutableListOf<MessageState>()
        val sorts = linkedMapOf<String, String>()

        incoming.forEach { message ->
            val cachedMessage = cache[message.id]
            val known = cachedMessage?.sort ?: knownSort(message.id)
            val stickySort = sticky?.remove(message.id)
            val pendingSort = if (message.role == "user") claimPendingSort(message.text) else null
            val sort = when {
                stickySort != null -> stickySort
                known != null -> known
                pendingSort != null -> pendingSort
                else -> nextSort()
            }
            if (message.role == "user" && known == null && sort.startsWith("z-")) {
                claimed = true
            }
            sorts[message.id] = sort
            if (
                cachedMessage == null ||
                cachedMessage.role != message.role ||
                cachedMessage.text != message.text ||
                cachedMessage.sort != sort
            ) {
                upserts.add(
                    MessageState(
                        id = message.id,
                        role = message.role,
                        text = message.text,
                        sort = sort,
                    ),
                )
            }
        }

        val removed = if (!complete) {
            emptyList()
        } else {
            val nextIds = incoming.map { it.id }.toHashSet()
            cache.keys.filter {
                if (nextIds.contains(it)) return@filter false
                !retainRemoved(it)
            }
        }

        return SessionSyncPlan(
            claimed = claimed,
            upserts = upserts,
            removedIds = removed,
            sorts = sorts,
        )
    }
}
