package io.github.rygel.outerstellar.platform.sync.engine.module

import io.github.rygel.outerstellar.platform.model.UserSummary

data class AdminState(val adminUsers: List<UserSummary> = emptyList())

interface AdminListener {
    fun onAdminStateChanged(state: AdminState) {}

    fun onSessionExpired() {}
}

interface AdminModule {
    val adminState: AdminState

    fun addListener(listener: AdminListener)

    fun removeListener(listener: AdminListener)

    fun loadUsers()

    fun setUserEnabled(userId: String, enabled: Boolean): Result<Unit>

    fun setUserRole(userId: String, role: String): Result<Unit>
}
