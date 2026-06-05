package io.github.rygel.outerstellar.platform.sync.engine

import io.github.rygel.outerstellar.platform.sync.engine.module.AuthState

interface SessionLifecycle {
    fun afterAuthSuccess()

    fun beforeLogout()

    fun onSessionExpired()

    val authState: AuthState
}
