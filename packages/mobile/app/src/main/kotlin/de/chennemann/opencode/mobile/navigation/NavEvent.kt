package de.chennemann.opencode.mobile.navigation

sealed interface NavEvent {
    data object ToManage : NavEvent

    data object ToConversation : NavEvent

    data object Back : NavEvent
}
