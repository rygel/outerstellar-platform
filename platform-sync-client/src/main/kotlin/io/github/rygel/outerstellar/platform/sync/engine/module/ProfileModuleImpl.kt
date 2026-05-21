@file:Suppress("TooGenericExceptionCaught")

package io.github.rygel.outerstellar.platform.sync.engine.module

import io.github.rygel.outerstellar.platform.analytics.AnalyticsService
import io.github.rygel.outerstellar.platform.model.SessionExpiredException
import io.github.rygel.outerstellar.platform.sync.client.ProfileClient
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicReference
import org.slf4j.LoggerFactory

class ProfileModuleImpl(
    private val profileClient: ProfileClient,
    private val analytics: AnalyticsService,
    private val authStateProvider: () -> AuthState,
    private val onLoadData: () -> Unit,
    private val onStopAutoSync: () -> Unit,
    private val onLogout: () -> Unit,
    private val notifier: ModuleNotifier? = null,
) : ProfileModule {
    private val logger = LoggerFactory.getLogger(ProfileModuleImpl::class.java)

    private val _profileState = AtomicReference(ProfileState())
    override val profileState: ProfileState
        get() = _profileState.get()

    private val listeners = CopyOnWriteArrayList<ProfileListener>()

    override fun addListener(listener: ProfileListener) {
        listeners.add(listener)
    }

    override fun removeListener(listener: ProfileListener) {
        listeners.remove(listener)
    }

    private fun updateState(transform: (ProfileState) -> ProfileState) {
        val newState = _profileState.updateAndGet(transform)
        listeners.forEach { it.onProfileStateChanged(newState) }
    }

    override fun loadProfile() =
        runGuarded("loadProfile") {
            val profile = profileClient.fetchProfile()
            updateState {
                it.copy(
                    userEmail = profile.email,
                    userAvatarUrl = profile.avatarUrl,
                    emailNotificationsEnabled = profile.emailNotificationsEnabled,
                    pushNotificationsEnabled = profile.pushNotificationsEnabled,
                )
            }
        }

    override fun updateProfile(email: String, username: String?, avatarUrl: String?): Result<Unit> =
        runGuardedResult(
            "updateProfile",
            onError = { e -> notifier?.notifyFailure("Profile update failed: ${e.message}") },
        ) {
            profileClient.updateProfile(email, username, avatarUrl)
            onLoadData()
            loadProfile()
            analytics.track(authStateProvider().userName, "profile_updated")
            notifier?.notifySuccess("Profile updated")
            Result.success(Unit)
        }

    override fun deleteAccount(currentPassword: String): Result<Unit> =
        runGuardedResult(
            "deleteAccount",
            onError = { e -> notifier?.notifyFailure("Account deletion failed: ${e.message}") },
        ) {
            val username = authStateProvider().userName
            profileClient.deleteAccount(currentPassword)
            onStopAutoSync()
            onLogout()
            analytics.track(username, "account_deleted")
            updateState { ProfileState() }
            notifier?.notifySuccess("Account deleted")
            Result.success(Unit)
        }

    override fun updateNotificationPreferences(emailEnabled: Boolean, pushEnabled: Boolean): Result<Unit> =
        runGuardedResult("updateNotificationPreferences") {
            profileClient.updateNotificationPreferences(emailEnabled, pushEnabled)
            updateState { it.copy(emailNotificationsEnabled = emailEnabled, pushNotificationsEnabled = pushEnabled) }
            Result.success(Unit)
        }

    private fun handleSessionExpired(e: Exception? = null) {
        if (e != null) {
            logger.warn("Session expired: ${e.message}", e)
        }
        onStopAutoSync()
        onLogout()
        updateState { ProfileState() }
        listeners.forEach { it.onSessionExpired() }
        notifier?.notifyFailure("Session expired. Please log in again.")
    }

    private inline fun runGuarded(operation: String, crossinline onError: (Exception) -> Unit = {}, block: () -> Unit) {
        try {
            block()
        } catch (e: SessionExpiredException) {
            handleSessionExpired(e)
        } catch (e: Exception) {
            logger.warn("Failed to {}", operation, e)
            onError(e)
            listeners.forEach { it.onSessionExpired() }
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
