package de.chennemann.opencode.mobile.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.chennemann.opencode.mobile.data.ServerRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HomeViewModel(
    private val repo: ServerRepository,
) : ViewModel() {
    private val input = MutableStateFlow(repo.endpoint.value)
    private var manual = false

    val state: StateFlow<HomeState> = combine(
        input,
        repo.found,
        repo.status,
    ) { url, discovered, status ->
        HomeState(
            url = url,
            discovered = discovered,
            status = status,
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        HomeState(
            url = repo.endpoint.value,
            discovered = null,
            status = ServerState.Idle,
        )
    )

    init {
        repo.start(viewModelScope)
        viewModelScope.launch {
            repo.endpoint.collect { url ->
                if (manual) return@collect
                input.value = url
            }
        }
    }

    fun updateUrl(value: String) {
        manual = true
        input.value = value
    }

    fun useDiscovered() {
        val value = repo.found.value ?: return
        manual = true
        input.value = value
    }

    fun refresh() {
        viewModelScope.launch {
            repo.setUrl(input.value)
            repo.refresh()
        }
    }
}
