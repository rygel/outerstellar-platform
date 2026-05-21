package io.github.rygel.outerstellar.platform.sync.engine.module

import io.github.rygel.outerstellar.platform.model.ConflictStrategy
import io.github.rygel.outerstellar.platform.model.ContactSummary
import io.github.rygel.outerstellar.platform.model.MessageSummary

data class SyncDataState(
    val isSyncing: Boolean = false,
    val isOnline: Boolean = true,
    val messages: List<MessageSummary> = emptyList(),
    val contacts: List<ContactSummary> = emptyList(),
    val searchQuery: String = "",
    val syncStatus: String = "",
)

interface SyncDataListener {
    fun onSyncDataStateChanged(state: SyncDataState) {}

    fun onSyncError(operation: String, message: String) {}
}

interface SyncDataModule {
    val syncDataState: SyncDataState

    fun addListener(listener: SyncDataListener)

    fun removeListener(listener: SyncDataListener)

    fun sync(isAuto: Boolean = false): Result<Unit>

    fun startAutoSync()

    fun stopAutoSync()

    fun loadMessages()

    fun loadContacts()

    fun loadData()

    fun setSearchQuery(query: String)

    fun createLocalMessage(author: String, content: String): Result<Unit>

    fun resolveConflict(syncId: String, strategy: ConflictStrategy)

    fun createContact(
        name: String,
        emails: List<String>,
        phones: List<String>,
        socialMedia: List<String>,
        company: String,
        companyAddress: String,
        department: String,
    ): Result<Unit>

    fun updateContact(
        syncId: String,
        name: String,
        emails: List<String>,
        phones: List<String>,
        socialMedia: List<String>,
        company: String,
        companyAddress: String,
        department: String,
    ): Result<Unit>
}
