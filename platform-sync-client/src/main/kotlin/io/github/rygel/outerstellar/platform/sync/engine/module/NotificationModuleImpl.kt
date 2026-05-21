@file:Suppress("TooGenericExceptionCaught")

package io.github.rygel.outerstellar.platform.sync.engine.module

import io.github.rygel.outerstellar.platform.model.SessionExpiredException
import io.github.rygel.outerstellar.platform.sync.client.NotificationClient
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicReference
import org.slf4j.LoggerFactory

class NotificationModuleImpl(
    private val notificationClient: NotificationClient,
    private val onStopAutoSync: () -> Unit,
    private val onLogout: () -> Unit,
) : NotificationModule {
    private val logger = LoggerFactory.getLogger(NotificationModuleImpl::class.java)

    private val _notificationState = AtomicReference(NotificationState())
    override val notificationState: NotificationState
        get() = _notificationState.get()

    private val listeners = CopyOnWriteArrayList<NotificationListener>()

    override fun addListener(listener: NotificationListener) {
        listeners.add(listener)
    }

    override fun removeListener(listener: NotificationListener) {
        listeners.remove(listener)
    }

    private fun updateState(transform: (NotificationState) -> NotificationState) {
        val newState = _notificationState.updateAndGet(transform)
        listeners.forEach { it.onNotificationStateChanged(newState) }
    }

    override fun loadNotifications() =
        runGuarded("loadNotifications") {
            val notifications = notificationClient.listNotifications()
            updateState { it.copy(notifications = notifications) }
        }

    override fun markNotificationRead(notificationId: String) =
        runGuarded("markNotificationRead") {
            notificationClient.markNotificationRead(notificationId)
            loadNotifications()
        }

    override fun markAllNotificationsRead() =
        runGuarded("markAllNotificationsRead") {
            notificationClient.markAllNotificationsRead()
            loadNotifications()
        }

    private fun handleSessionExpired(e: Exception? = null) {
        if (e != null) {
            logger.warn("Session expired: ${e.message}", e)
        }
        onStopAutoSync()
        onLogout()
        updateState { NotificationState() }
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
}
