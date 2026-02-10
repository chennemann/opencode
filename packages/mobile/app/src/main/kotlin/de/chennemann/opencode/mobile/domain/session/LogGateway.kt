package de.chennemann.opencode.mobile.domain.session

interface LogGateway {
    fun debug(tag: String, message: String)

    fun warn(tag: String, message: String)
}
