@file:Suppress("TooGenericExceptionCaught")

package io.github.rygel.outerstellar.platform.sync.engine.module

import io.github.rygel.outerstellar.platform.analytics.AnalyticsService
import io.github.rygel.outerstellar.platform.model.SessionExpiredException
import io.github.rygel.outerstellar.platform.sync.client.AuthClient
import io.github.rygel.outerstellar.platform.sync.engine.SessionLifecycle
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicReference
import org.slf4j.LoggerFactory

interface ModuleNotifier {
    fun notifySuccess(message: String)

    fun notifyFailure(message: String)
}

class AuthModuleImpl(
    private val authClient: AuthClient,
    private val analytics: AnalyticsService,
    private val lifecycle: SessionLifecycle,
    private val notifier: ModuleNotifier? = null,
) : AuthModule {
    private val logger = LoggerFactory.getLogger(AuthModuleImpl::class.java)

    private val _authState = AtomicReference(AuthState())
    override val authState: AuthState
        get() = _authState.get()

    private val listeners = CopyOnWriteArrayList<AuthListener>()

    override fun addListener(listener: AuthListener) {
        listeners.add(listener)
    }

    override fun removeListener(listener: AuthListener) {
        listeners.remove(listener)
    }

    private fun updateState(transform: (AuthState) -> AuthState) {
        val newState = _authState.updateAndGet(transform)
        listeners.forEach { it.onAuthStateChanged(newState) }
    }

    override fun login(username: String, password: String): Result<Unit> =
        runGuarded(
            "login",
            onError = { e ->
                updateState { it.copy() }
                notifier?.notifyFailure("Login failed: ${e.message}")
            },
        ) {
            val auth = authClient.login(username, password)
            updateState { it.copy(isLoggedIn = true, userName = auth.username, userRole = auth.role) }
            analytics.identify(auth.username, mapOf("role" to auth.role))
            analytics.track(auth.username, "user_login")
            lifecycle.afterAuthSuccess()
            notifier?.notifySuccess("Logged in as ${auth.username}")
            Result.success(Unit)
        }

    override fun register(username: String, password: String): Result<Unit> =
        runGuarded(
            "register",
            onError = { e ->
                updateState { it.copy() }
                notifier?.notifyFailure("Registration failed: ${e.message}")
            },
        ) {
            val auth = authClient.register(username, password)
            updateState { it.copy(isLoggedIn = true, userName = auth.username, userRole = auth.role) }
            analytics.identify(auth.username, mapOf("role" to auth.role))
            analytics.track(auth.username, "user_register")
            lifecycle.afterAuthSuccess()
            notifier?.notifySuccess("Registered as ${auth.username}")
            Result.success(Unit)
        }

    override fun logout() {
        lifecycle.beforeLogout()
        authClient.logout()
        updateState { AuthState() }
        val username = authState.userName
        if (username.isNotBlank()) {
            analytics.track(username, "user_logout")
        }
    }

    override fun changePassword(currentPassword: String, newPassword: String): Result<Unit> =
        runGuarded(
            "changePassword",
            onError = { e -> notifier?.notifyFailure("Password change failed: ${e.message}") },
        ) {
            authClient.changePassword(currentPassword, newPassword)
            analytics.track(authState.userName, "password_changed")
            notifier?.notifySuccess("Password changed")
            Result.success(Unit)
        }

    override fun requestPasswordReset(email: String): Result<Unit> {
        return try {
            authClient.requestPasswordReset(email)
            notifier?.notifySuccess("Password reset email sent")
            Result.success(Unit)
        } catch (e: SessionExpiredException) {
            onSessionExpired(e)
            Result.failure(e)
        } catch (e: Exception) {
            logger.warn("Password reset request failed", e)
            notifier?.notifyFailure("Password reset request failed: ${e.message}")
            Result.failure(e)
        }
    }

    override fun resetPassword(token: String, newPassword: String): Result<Unit> {
        return try {
            authClient.resetPassword(token, newPassword)
            notifier?.notifySuccess("Password has been reset")
            Result.success(Unit)
        } catch (e: SessionExpiredException) {
            onSessionExpired(e)
            Result.failure(e)
        } catch (e: Exception) {
            logger.warn("Password reset failed", e)
            notifier?.notifyFailure("Password reset failed: ${e.message}")
            Result.failure(e)
        }
    }

    override fun resetState() {
        _authState.set(AuthState())
        listeners.forEach { it.onSessionExpired() }
    }

    private fun onSessionExpired(e: Exception? = null) {
        if (e != null) {
            logger.warn("Session expired: ${e.message}", e)
        }
        lifecycle.onSessionExpired()
        resetState()
        notifier?.notifyFailure("Session expired. Please log in again.")
    }

    private fun runGuarded(
        operation: String,
        onError: (Exception) -> Unit = {},
        block: () -> Result<Unit>,
    ): Result<Unit> {
        return try {
            block()
        } catch (e: SessionExpiredException) {
            onSessionExpired(e)
            Result.failure(e)
        } catch (e: Exception) {
            logger.warn("Failed to {}", operation, e)
            onError(e)
            Result.failure(e)
        }
    }
}
