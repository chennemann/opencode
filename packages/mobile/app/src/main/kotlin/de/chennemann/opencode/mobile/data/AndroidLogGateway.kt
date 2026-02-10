package de.chennemann.opencode.mobile.data

import android.util.Log
import de.chennemann.opencode.mobile.domain.session.LogGateway

class AndroidLogGateway : LogGateway {
    override fun debug(tag: String, message: String) {
        Log.d(tag, message)
    }

    override fun warn(tag: String, message: String) {
        Log.w(tag, message)
    }
}
