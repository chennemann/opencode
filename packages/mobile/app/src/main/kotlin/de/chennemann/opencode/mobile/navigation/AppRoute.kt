package de.chennemann.opencode.mobile.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable
data object ConversationRoute : NavKey

@Serializable
data object ManageProjectsRoute : NavKey

@Serializable
data object LogsRoute : NavKey
