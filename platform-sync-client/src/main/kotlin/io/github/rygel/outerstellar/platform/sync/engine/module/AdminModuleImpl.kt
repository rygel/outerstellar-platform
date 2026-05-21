@file:Suppress("TooGenericExceptionCaught")

package io.github.rygel.outerstellar.platform.sync.engine.module

import io.github.rygel.outerstellar.platform.analytics.AnalyticsService
import io.github.rygel.outerstellar.platform.model.SessionExpiredException
import io.github.rygel.outerstellar.platform.sync.client.AdminClient
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicReference
import org.slf4j.LoggerFactory

class AdminModuleImpl(
    private val adminClient: AdminClient,
    private val analytics: AnalyticsService,
    private val authStateProvider: () -> AuthState,
    private val onStopAutoSync: () -> Unit,
    private val onLogout: () -> Unit,
) : AdminModule {
    private val logger = LoggerFactory.getLogger(AdminModuleImpl::class.java)

    private val _adminState = AtomicReference(AdminState())
    override val adminState: AdminState
        get() = _adminState.get()

    private val listeners = CopyOnWriteArrayList<AdminListener>()

    override fun addListener(listener: AdminListener) {
        listeners.add(listener)
    }

    override fun removeListener(listener: AdminListener) {
        listeners.remove(listener)
    }

    private fun updateState(transform: (AdminState) -> AdminState) {
        val newState = _adminState.updateAndGet(transform)
        listeners.forEach { it.onAdminStateChanged(newState) }
    }

    override fun loadUsers() =
        runGuarded("loadUsers") {
            val users = adminClient.listUsers()
            updateState { it.copy(adminUsers = users) }
        }

    override fun setUserEnabled(userId: String, enabled: Boolean): Result<Unit> =
        runGuardedResult("setUserEnabled") {
            adminClient.setUserEnabled(userId, enabled)
            loadUsers()
            analytics.track(
                authStateProvider().userName,
                "user_enabled_changed",
                mapOf("userId" to userId, "enabled" to enabled),
            )
            Result.success(Unit)
        }

    override fun setUserRole(userId: String, role: String): Result<Unit> =
        runGuardedResult("setUserRole") {
            adminClient.setUserRole(userId, role)
            loadUsers()
            analytics.track(
                authStateProvider().userName,
                "user_role_changed",
                mapOf("userId" to userId, "role" to role),
            )
            Result.success(Unit)
        }

    private fun handleSessionExpired(e: Exception? = null) {
        if (e != null) {
            logger.warn("Session expired: ${e.message}", e)
        }
        onStopAutoSync()
        onLogout()
        updateState { AdminState() }
        listeners.forEach { it.onSessionExpired() }
    }

    private inline fun runGuarded(operation: String, crossinline onError: (Exception) -> Unit = {}, block: () -> Unit) {
        try {
            block()
        } catch (e: SessionExpiredException) {
            handleSessionExpired(e)
        } catch (e: Exception) {
            logger.warn("Failed to {}", operation, e)
            onError(e)
        }
    }

    private inline fun runGuardedResult(
        operation: String,
        crossinline onError: (Exception) -> Unit = {},
        block: () -> Result<Unit>,
    ): Result<Unit> {
        return try {
            block()
        } catch (e: SessionExpiredException) {
            handleSessionExpired(e)
            Result.failure(e)
        } catch (e: Exception) {
            logger.warn("Failed to {}", operation, e)
            onError(e)
            Result.failure(e)
        }
    }
}
