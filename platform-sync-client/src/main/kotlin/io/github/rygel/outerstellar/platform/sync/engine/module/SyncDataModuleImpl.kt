@file:Suppress("TooGenericExceptionCaught")

package io.github.rygel.outerstellar.platform.sync.engine.module

import io.github.rygel.outerstellar.platform.analytics.AnalyticsService
import io.github.rygel.outerstellar.platform.model.ConflictStrategy
import io.github.rygel.outerstellar.platform.model.SessionExpiredException
import io.github.rygel.outerstellar.platform.persistence.MessageRepository
import io.github.rygel.outerstellar.platform.persistence.TransactionManager
import io.github.rygel.outerstellar.platform.service.ContactService
import io.github.rygel.outerstellar.platform.service.MessageService
import io.github.rygel.outerstellar.platform.sync.SyncPullResponse
import io.github.rygel.outerstellar.platform.sync.SyncPushRequest
import io.github.rygel.outerstellar.platform.sync.SyncStats
import io.github.rygel.outerstellar.platform.sync.client.SyncClient
import io.github.rygel.outerstellar.platform.sync.engine.ConnectivityChecker
import io.github.rygel.outerstellar.platform.sync.engine.SessionLifecycle
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import org.slf4j.LoggerFactory

class SyncDataModuleImpl(
    private val syncClient: SyncClient,
    private val messageService: MessageService,
    private val contactService: ContactService?,
    private val analytics: AnalyticsService,
    private val repository: MessageRepository,
    private val transactionManager: TransactionManager,
    private val lifecycle: SessionLifecycle,
    private val notifier: ModuleNotifier? = null,
    connectivityChecker: ConnectivityChecker? = null,
) : SyncDataModule {
    private val logger = LoggerFactory.getLogger(SyncDataModuleImpl::class.java)

    private val _syncDataState = AtomicReference(SyncDataState())
    override val syncDataState: SyncDataState
        get() = _syncDataState.get()

    private val listeners = CopyOnWriteArrayList<SyncDataListener>()

    private val syncInProgress = AtomicBoolean(false)
    private var autoSyncExecutor: ScheduledExecutorService? = null
    private val autoSyncIntervalMinutes: Long = 5L

    init {
        connectivityChecker?.addObserver { online -> updateState { it.copy(isOnline = online) } }
    }

    override fun addListener(listener: SyncDataListener) {
        listeners.add(listener)
    }

    override fun removeListener(listener: SyncDataListener) {
        listeners.remove(listener)
    }

    private fun updateState(transform: (SyncDataState) -> SyncDataState) {
        val newState = _syncDataState.updateAndGet(transform)
        listeners.forEach { it.onSyncDataStateChanged(newState) }
    }

    override fun sync(isAuto: Boolean): Result<Unit> {
        if (!lifecycle.authState.isLoggedIn) {
            return Result.failure(IllegalStateException("Not logged in"))
        }
        if (!syncDataState.isOnline) {
            if (!isAuto) {
                notifier?.notifyFailure("Cannot sync while offline")
            }
            return Result.failure(IllegalStateException("Offline"))
        }
        if (!syncInProgress.compareAndSet(false, true)) {
            return Result.success(Unit)
        }
        updateState { it.copy(isSyncing = true, syncStatus = if (isAuto) "Auto-syncing..." else "Syncing...") }
        return try {
            val stats = doSync()
            updateState {
                it.copy(
                    isSyncing = false,
                    syncStatus = buildSyncStatusMessage(stats.pushedCount, stats.pulledCount, stats.conflictCount),
                )
            }
            if (isAuto) {
                analytics.track(lifecycle.authState.userName, "auto_sync")
            } else {
                analytics.track(lifecycle.authState.userName, "manual_sync")
            }
            loadData()
            if (!isAuto) {
                notifier?.notifySuccess(syncDataState.syncStatus)
            }
            Result.success(Unit)
        } catch (e: SessionExpiredException) {
            syncInProgress.set(false)
            updateState { it.copy(isSyncing = false) }
            onSessionExpired(e)
            Result.failure(e)
        } catch (e: Exception) {
            syncInProgress.set(false)
            updateState { it.copy(isSyncing = false, syncStatus = "Sync failed: ${e.message}") }
            if (!isAuto) {
                notifier?.notifyFailure("Sync failed: ${e.message}")
                fireError("sync", e.message ?: "Unknown error")
            }
            Result.failure(e)
        } finally {
            syncInProgress.set(false)
        }
    }

    private fun doSync(): SyncStats {
        val lastSync = repository.getLastSyncEpochMs()

        val allPulled = mutableListOf<io.github.rygel.outerstellar.platform.sync.SyncMessage>()
        var pullHasMore = true
        var pullSince = lastSync
        var latestTimestamp = 0L

        while (pullHasMore) {
            val pullBody: SyncPullResponse = syncClient.pull(pullSince)
            allPulled.addAll(pullBody.messages)
            pullHasMore = pullBody.hasMore
            latestTimestamp = pullBody.serverTimestamp
            if (pullBody.messages.isNotEmpty()) {
                pullSince = pullBody.messages.maxOf { it.updatedAtEpochMs }
            }
        }

        val dirtyMessages = repository.listDirtyMessages()
        val pushRequestData = SyncPushRequest(dirtyMessages.map { it.toSyncMessage() })

        val pushBody = syncClient.push(pushRequestData)

        transactionManager.inTransaction {
            allPulled.forEach { repository.upsertSyncedMessage(it, false) }
            pushBody.conflicts.forEach { conflict ->
                conflict.serverMessage?.let { repository.upsertSyncedMessage(it, false) }
            }
            repository.setLastSyncEpochMs(latestTimestamp)
        }

        return SyncStats(
            pushedCount = pushBody.appliedCount,
            pulledCount = allPulled.size,
            conflictCount = pushBody.conflicts.size,
        )
    }

    override fun startAutoSync() {
        stopAutoSync()
        autoSyncExecutor =
            Executors.newSingleThreadScheduledExecutor { runnable ->
                    Thread(runnable, "auto-sync").also { it.isDaemon = true }
                }
                .also { executor ->
                    executor.scheduleAtFixedRate(
                        { sync(isAuto = true) },
                        autoSyncIntervalMinutes,
                        autoSyncIntervalMinutes,
                        TimeUnit.MINUTES,
                    )
                }
    }

    override fun stopAutoSync() {
        autoSyncExecutor?.shutdownNow()
        autoSyncExecutor = null
    }

    override fun loadMessages() {
        try {
            val query = syncDataState.searchQuery.ifBlank { null }
            val result = messageService.listMessages(query = query)
            updateState { it.copy(messages = result.items) }
        } catch (e: Exception) {
            logger.warn("Failed to load messages", e)
            fireError("loadMessages", e.message ?: "Unknown error")
        }
    }

    override fun loadContacts() {
        val svc = contactService ?: return
        try {
            val query = syncDataState.searchQuery.ifBlank { null }
            val contacts = svc.listContacts(query = query)
            updateState { it.copy(contacts = contacts) }
        } catch (e: Exception) {
            logger.warn("Failed to load contacts", e)
            fireError("loadContacts", e.message ?: "Unknown error")
        }
    }

    override fun loadData() {
        loadMessages()
        loadContacts()
    }

    override fun setSearchQuery(query: String) {
        updateState { it.copy(searchQuery = query) }
        loadData()
    }

    override fun createLocalMessage(author: String, content: String): Result<Unit> =
        runGuardedResult("createLocalMessage") {
            messageService.createLocalMessage(author, content)
            loadMessages()
            Result.success(Unit)
        }

    override fun resolveConflict(syncId: String, strategy: ConflictStrategy) =
        runGuarded("resolveConflict") {
            messageService.resolveConflict(syncId, strategy)
            loadMessages()
        }

    override fun createContact(
        name: String,
        emails: List<String>,
        phones: List<String>,
        socialMedia: List<String>,
        company: String,
        companyAddress: String,
        department: String,
    ): Result<Unit> {
        val svc = contactService
        if (svc == null) {
            return Result.failure(IllegalStateException("Contact service not available"))
        }
        return try {
            svc.createContact(name, emails, phones, socialMedia, company, companyAddress, department)
            loadContacts()
            analytics.track(lifecycle.authState.userName, "contact_created", mapOf("name" to name))
            Result.success(Unit)
        } catch (e: Exception) {
            logger.warn("Failed to create contact", e)
            fireError("createContact", e.message ?: "Unknown error")
            Result.failure(e)
        }
    }

    override fun updateContact(
        syncId: String,
        name: String,
        emails: List<String>,
        phones: List<String>,
        socialMedia: List<String>,
        company: String,
        companyAddress: String,
        department: String,
    ): Result<Unit> {
        val svc = contactService
        if (svc == null) {
            return Result.failure(IllegalStateException("Contact service not available"))
        }
        return try {
            val stored =
                svc.getContactBySyncId(syncId)
                    ?: return Result.failure(IllegalStateException("Contact not found: $syncId"))
            val updated =
                stored.copy(
                    name = name,
                    emails = emails,
                    phones = phones,
                    socialMedia = socialMedia,
                    company = company,
                    companyAddress = companyAddress,
                )
            svc.updateContact(updated)
            loadContacts()
            analytics.track(lifecycle.authState.userName, "contact_updated", mapOf("name" to name))
            Result.success(Unit)
        } catch (e: Exception) {
            logger.warn("Failed to update contact", e)
            fireError("updateContact", e.message ?: "Unknown error")
            Result.failure(e)
        }
    }

    override fun clearState() {
        stopAutoSync()
        updateState { SyncDataState(isOnline = it.isOnline) }
        listeners.forEach { it.onSyncError("session", "Session expired") }
    }

    private fun onSessionExpired(e: Exception? = null) {
        if (e != null) {
            logger.warn("Session expired: ${e.message}", e)
        }
        lifecycle.onSessionExpired()
        clearState()
        notifier?.notifyFailure("Session expired. Please log in again.")
    }

    private fun fireError(operation: String, message: String) {
        listeners.forEach { it.onSyncError(operation, message) }
    }

    private fun buildSyncStatusMessage(pushed: Int, pulled: Int, conflicts: Int): String {
        val parts = mutableListOf<String>()
        if (pushed > 0) parts.add("pushed $pushed")
        if (pulled > 0) parts.add("pulled $pulled")
        if (conflicts > 0) parts.add("$conflicts conflict(s)")
        return if (parts.isEmpty()) "Everything up to date" else "Synced: ${parts.joinToString(", ")}"
    }

    private fun runGuarded(operation: String, onError: (Exception) -> Unit = {}, block: () -> Unit) {
        try {
            block()
        } catch (e: SessionExpiredException) {
            onSessionExpired(e)
        } catch (e: Exception) {
            logger.warn("Failed to {}", operation, e)
            onError(e)
            fireError(operation, e.message ?: "Unknown error")
        }
    }

    private fun runGuardedResult(
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
            fireError(operation, e.message ?: "Unknown error")
            onError(e)
            Result.failure(e)
        }
    }
}
