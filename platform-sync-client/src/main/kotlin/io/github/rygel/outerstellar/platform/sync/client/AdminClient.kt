package io.github.rygel.outerstellar.platform.sync.client

import io.github.rygel.outerstellar.platform.model.UserSummary

interface AdminClient {
    fun listUsers(): List<UserSummary>

    fun setUserEnabled(userId: String, enabled: Boolean)

    fun setUserRole(userId: String, role: String)
}
