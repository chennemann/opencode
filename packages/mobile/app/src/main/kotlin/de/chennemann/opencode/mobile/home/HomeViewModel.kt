package de.chennemann.opencode.mobile.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.chennemann.opencode.mobile.data.ServerRepository
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class HomeViewModel(
    private val repo: ServerRepository,
) : ViewModel() {
    val state: StateFlow<ServerState> = repo.status

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            repo.refresh()
        }
    }
}
