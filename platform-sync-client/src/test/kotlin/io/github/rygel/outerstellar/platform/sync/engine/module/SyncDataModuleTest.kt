@file:Suppress("TooGenericExceptionCaught")

package io.github.rygel.outerstellar.platform.sync.engine.module

import io.github.rygel.outerstellar.platform.analytics.AnalyticsService
import io.github.rygel.outerstellar.platform.model.ConflictStrategy
import io.github.rygel.outerstellar.platform.model.ContactSummary
import io.github.rygel.outerstellar.platform.model.MessageSummary
import io.github.rygel.outerstellar.platform.model.PagedResult
import io.github.rygel.outerstellar.platform.model.PaginationMetadata
import io.github.rygel.outerstellar.platform.model.SessionExpiredException
import io.github.rygel.outerstellar.platform.model.ValidationException
import io.github.rygel.outerstellar.platform.persistence.MessageRepository
import io.github.rygel.outerstellar.platform.persistence.TransactionManager
import io.github.rygel.outerstellar.platform.service.ContactService
import io.github.rygel.outerstellar.platform.service.MessageService
import io.github.rygel.outerstellar.platform.sync.SyncMessage
import io.github.rygel.outerstellar.platform.sync.SyncPullResponse
import io.github.rygel.outerstellar.platform.sync.SyncPushResponse
import io.github.rygel.outerstellar.platform.sync.client.SyncClient
import io.github.rygel.outerstellar.platform.sync.engine.ConnectivityChecker
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

internal class SyncDataModuleTest {

    private lateinit var syncClient: SyncClient
    private lateinit var messageService: MessageService
    private lateinit var contactService: ContactService
    private lateinit var analytics: AnalyticsService
    private lateinit var repository: MessageRepository
    private lateinit var transactionManager: TransactionManager
    private lateinit var connectivityChecker: ConnectivityChecker
    private lateinit var notifier: ModuleNotifier
    private lateinit var module: SyncDataModuleImpl

    private var authState = AuthState()
    private var connectivityObserver: ((Boolean) -> Unit)? = null

    @BeforeEach
    fun setUp() {
        syncClient = mockk(relaxed = true)
        messageService = mockk(relaxed = true)
        contactService = mockk(relaxed = true)
        analytics = mockk(relaxed = true)
        repository = mockk(relaxed = true)
        transactionManager = mockk(relaxed = true)
        connectivityChecker = mockk(relaxed = true)
        notifier = mockk(relaxed = true)
        authState = AuthState()

        val observerSlot = slot<(Boolean) -> Unit>()
        every { connectivityChecker.addObserver(capture(observerSlot)) } answers
            {
                connectivityObserver = observerSlot.captured
            }

        every { transactionManager.inTransaction<Any>(any()) } answers { firstArg<() -> Any>()() }

        module =
            SyncDataModuleImpl(
                syncClient = syncClient,
                messageService = messageService,
                contactService = contactService,
                analytics = analytics,
                repository = repository,
                transactionManager = transactionManager,
                authStateProvider = { authState },
                notifier = notifier,
                connectivityChecker = connectivityChecker,
            )
    }

    private fun stubLoggedIn() {
        authState = AuthState(isLoggedIn = true, userName = "user", userRole = "USER")
    }

    private fun stubSyncSuccess(pushed: Int = 0, pulled: Int = 0, conflicts: Int = 0) {
        every { repository.getLastSyncEpochMs() } returns 0L
        every { repository.listDirtyMessages() } returns emptyList()
        every { syncClient.pull(any()) } returns
            SyncPullResponse(
                messages = (1..pulled).map { SyncMessage("s$it", "a", "c", it.toLong()) },
                serverTimestamp = 100L,
                hasMore = false,
            )
        every { syncClient.push(any()) } returns
            SyncPushResponse(
                appliedCount = pushed,
                conflicts =
                    (1..conflicts).map { io.github.rygel.outerstellar.platform.sync.SyncConflict("c$it", "reason") },
            )
    }

    @Test
    fun `sync success updates state and tracks analytics`() {
        stubLoggedIn()
        stubSyncSuccess(pushed = 3, pulled = 2, conflicts = 1)

        val result = module.sync()

        assertTrue(result.isSuccess)
        assertFalse(module.syncDataState.isSyncing)
        assertEquals("Synced: pushed 3, pulled 2, 1 conflict(s)", module.syncDataState.syncStatus)
        verify { analytics.track("user", "manual_sync") }
        verify { notifier.notifySuccess(module.syncDataState.syncStatus) }
    }

    @Test
    fun `sync when offline returns failure`() {
        stubLoggedIn()
        connectivityObserver?.invoke(false)

        val result = module.sync()

        assertTrue(result.isFailure)
        assertEquals("Offline", result.exceptionOrNull()?.message)
        verify { notifier.notifyFailure("Cannot sync while offline") }
    }

    @Test
    fun `sync when not logged in returns failure`() {
        val result = module.sync()

        assertTrue(result.isFailure)
        assertEquals("Not logged in", result.exceptionOrNull()?.message)
    }

    @Test
    fun `sync when already syncing is no-op`() {
        stubLoggedIn()
        every { repository.getLastSyncEpochMs() } returns 0L
        every { repository.listDirtyMessages() } returns emptyList()
        every { syncClient.pull(any()) } answers
            {
                Thread.sleep(100)
                SyncPullResponse(serverTimestamp = 100L)
            }
        every { syncClient.push(any()) } returns SyncPushResponse()

        val results = mutableListOf<Result<Unit>>()
        val t1 = Thread { results.add(module.sync()) }
        val t2 = Thread { results.add(module.sync()) }
        t1.start()
        Thread.sleep(20)
        t2.start()
        t1.join()
        t2.join()

        assertTrue(results.all { it.isSuccess })
        verify(exactly = 1) { syncClient.pull(any()) }
    }

    @Test
    fun `sync failure calls notifier with failure`() {
        stubLoggedIn()
        every { repository.getLastSyncEpochMs() } returns 0L
        every { repository.listDirtyMessages() } returns emptyList()
        every { syncClient.pull(any()) } throws RuntimeException("Network error")

        val result = module.sync()

        assertTrue(result.isFailure)
        assertFalse(module.syncDataState.isSyncing)
        assertEquals("Sync failed: Network error", module.syncDataState.syncStatus)
        verify { notifier.notifyFailure("Sync failed: Network error") }
    }

    @Test
    fun `sync session expired clears state`() {
        stubLoggedIn()
        every { repository.getLastSyncEpochMs() } returns 0L
        every { repository.listDirtyMessages() } returns emptyList()
        every { syncClient.pull(any()) } throws SessionExpiredException()

        val result = module.sync()

        assertTrue(result.isFailure)
        assertFalse(module.syncDataState.isSyncing)
    }

    @Test
    fun `sync auto mode does not notify`() {
        stubLoggedIn()
        stubSyncSuccess(pushed = 1)
        clearMocks(notifier)

        val result = module.sync(isAuto = true)

        assertTrue(result.isSuccess)
        verify(exactly = 0) { notifier.notifySuccess(any()) }
        verify { analytics.track("user", "auto_sync") }
    }

    @Test
    fun `startAutoSync creates executor and stopAutoSync shuts down`() {
        module.startAutoSync()
        module.stopAutoSync()
    }

    @Test
    fun `double startAutoSync is no-op`() {
        module.startAutoSync()
        module.startAutoSync()
        module.stopAutoSync()
    }

    @Test
    fun `sync with zero stats shows up to date`() {
        stubLoggedIn()
        stubSyncSuccess(pushed = 0, pulled = 0, conflicts = 0)

        module.sync()

        assertEquals("Everything up to date", module.syncDataState.syncStatus)
    }

    @Test
    fun `sync offline auto mode does not notify`() {
        stubLoggedIn()
        connectivityObserver?.invoke(false)

        val result = module.sync(isAuto = true)

        assertTrue(result.isFailure)
        verify(exactly = 0) { notifier.notifyFailure(any()) }
    }

    @Test
    fun `sync failure auto mode does not fire error to listeners`() {
        stubLoggedIn()
        val listener = mockk<SyncDataListener>(relaxed = true)
        module.addListener(listener)
        every { repository.getLastSyncEpochMs() } returns 0L
        every { repository.listDirtyMessages() } returns emptyList()
        every { syncClient.pull(any()) } throws RuntimeException("fail")

        module.sync(isAuto = true)

        verify(exactly = 0) { listener.onSyncError(any(), any()) }
    }

    @Test
    fun `loadData fetches messages and contacts`() {
        every { messageService.listMessages(any()) } returns
            PagedResult(
                items = listOf(MessageSummary("s1", "a", "c", 1L, false)),
                metadata = PaginationMetadata(1, 100, 1),
            )
        every { contactService.listContacts(any()) } returns
            listOf(ContactSummary("c1", "Alice", emptyList(), emptyList(), emptyList(), "", "", "", 1L, false))

        module.loadData()

        assertEquals(1, module.syncDataState.messages.size)
        assertEquals(1, module.syncDataState.contacts.size)
        assertEquals("s1", module.syncDataState.messages[0].syncId)
        assertEquals("c1", module.syncDataState.contacts[0].syncId)
    }

    @Test
    fun `loadData with search query passes query to services`() {
        every { messageService.listMessages(query = "test") } returns
            PagedResult(items = emptyList(), metadata = PaginationMetadata(1, 100, 0))

        module.setSearchQuery("test")
        module.loadData()

        verify { messageService.listMessages(query = "test") }
        verify { contactService.listContacts(query = "test") }
    }

    @Test
    fun `createLocalMessage success`() {
        every { messageService.createLocalMessage("author", "content") } returns mockk(relaxed = true)
        every { messageService.listMessages(any()) } returns
            PagedResult(items = emptyList(), metadata = PaginationMetadata(1, 100, 0))

        val result = module.createLocalMessage("author", "content")

        assertTrue(result.isSuccess)
        verify { messageService.createLocalMessage("author", "content") }
        verify { messageService.listMessages(any()) }
    }

    @Test
    fun `createLocalMessage validation failure returns Result failure`() {
        every { messageService.createLocalMessage("", "content") } throws
            ValidationException(listOf("Author is required."))

        val result = module.createLocalMessage("", "content")

        assertTrue(result.isFailure)
    }

    @Test
    fun `createContact success`() {
        stubLoggedIn()
        every { contactService.createContact(any(), any(), any(), any(), any(), any(), any()) } returns
            mockk(relaxed = true)
        every { contactService.listContacts(any()) } returns emptyList()

        val result = module.createContact("Alice", listOf("a@b.c"), emptyList(), emptyList(), "Co", "Addr", "Dept")

        assertTrue(result.isSuccess)
        verify { analytics.track("user", "contact_created", mapOf("name" to "Alice")) }
    }

    @Test
    fun `createContact without contact service returns failure`() {
        val moduleNoContact =
            SyncDataModuleImpl(
                syncClient = syncClient,
                messageService = messageService,
                contactService = null,
                analytics = analytics,
                repository = repository,
                transactionManager = transactionManager,
                authStateProvider = { authState },
            )

        val result = moduleNoContact.createContact("A", emptyList(), emptyList(), emptyList(), "", "", "")

        assertTrue(result.isFailure)
        assertEquals("Contact service not available", result.exceptionOrNull()?.message)
    }

    @Test
    fun `setSearchQuery updates state and triggers loadData`() {
        every { messageService.listMessages(query = "hello") } returns
            PagedResult(items = emptyList(), metadata = PaginationMetadata(1, 100, 0))

        module.setSearchQuery("hello")

        assertEquals("hello", module.syncDataState.searchQuery)
        verify { messageService.listMessages(query = "hello") }
    }

    @Test
    fun `loadMessages passes search query`() {
        every { messageService.listMessages(query = "find") } returns
            PagedResult(items = emptyList(), metadata = PaginationMetadata(1, 100, 0))

        module.setSearchQuery("find")

        verify { messageService.listMessages(query = "find") }
    }

    @Test
    fun `loadMessages with blank query passes null`() {
        every { messageService.listMessages(query = null) } returns
            PagedResult(items = emptyList(), metadata = PaginationMetadata(1, 100, 0))

        module.loadMessages()

        verify { messageService.listMessages(query = null) }
    }

    @Test
    fun `loadContacts without contactService is no-op`() {
        val moduleNoContact =
            SyncDataModuleImpl(
                syncClient = syncClient,
                messageService = messageService,
                contactService = null,
                analytics = analytics,
                repository = repository,
                transactionManager = transactionManager,
                authStateProvider = { authState },
            )

        moduleNoContact.loadContacts()

        verify(exactly = 0) { contactService.listContacts(any()) }
    }

    @Test
    fun `connectivity observer updates isOnline state`() {
        val listener = mockk<SyncDataListener>(relaxed = true)
        module.addListener(listener)

        connectivityObserver?.invoke(false)

        assertFalse(module.syncDataState.isOnline)
        verify { listener.onSyncDataStateChanged(any()) }

        connectivityObserver?.invoke(true)

        assertTrue(module.syncDataState.isOnline)
    }

    @Test
    fun `resolveConflict success`() {
        every { messageService.resolveConflict("s1", ConflictStrategy.MINE) } returns Unit
        every { messageService.listMessages(any()) } returns
            PagedResult(items = emptyList(), metadata = PaginationMetadata(1, 100, 0))

        module.resolveConflict("s1", ConflictStrategy.MINE)

        verify { messageService.resolveConflict("s1", ConflictStrategy.MINE) }
    }

    @Test
    fun `resolveConflict failure fires error`() {
        every { messageService.resolveConflict("s1", ConflictStrategy.SERVER) } throws
            RuntimeException("No conflict data")

        val listener = mockk<SyncDataListener>(relaxed = true)
        module.addListener(listener)

        module.resolveConflict("s1", ConflictStrategy.SERVER)

        verify { listener.onSyncError("resolveConflict", "No conflict data") }
    }
}
