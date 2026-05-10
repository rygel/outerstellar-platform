@file:Suppress("TooManyFunctions")

package io.github.rygel.outerstellar.platform.sync.engine

import io.github.rygel.outerstellar.platform.analytics.AnalyticsService
import io.github.rygel.outerstellar.platform.model.AuthTokenResponse
import io.github.rygel.outerstellar.platform.model.ConflictStrategy
import io.github.rygel.outerstellar.platform.model.ContactSummary
import io.github.rygel.outerstellar.platform.model.MessageSummary
import io.github.rygel.outerstellar.platform.model.NotificationSummary
import io.github.rygel.outerstellar.platform.model.SessionExpiredException
import io.github.rygel.outerstellar.platform.model.UserProfileResponse
import io.github.rygel.outerstellar.platform.model.UserSummary
import io.github.rygel.outerstellar.platform.service.ContactService
import io.github.rygel.outerstellar.platform.service.MessageService
import io.github.rygel.outerstellar.platform.sync.SyncService
import io.github.rygel.outerstellar.platform.sync.SyncStats
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class DesktopSyncEngineTest {

    private lateinit var syncService: SyncService
    private lateinit var messageService: MessageService
    private lateinit var contactService: ContactService
    private lateinit var analytics: AnalyticsService
    private lateinit var connectivityChecker: ConnectivityChecker
    private lateinit var notifier: EngineNotifier
    private lateinit var engine: DesktopSyncEngine

    private var connectivityObserver: ((Boolean) -> Unit)? = null

    @BeforeEach
    fun setUp() {
        syncService = mockk(relaxed = true)
        messageService = mockk(relaxed = true)
        contactService = mockk(relaxed = true)
        analytics = mockk(relaxed = true)
        connectivityChecker = mockk(relaxed = true)
        notifier = mockk(relaxed = true)

        val observerSlot = slot<(Boolean) -> Unit>()
        every { connectivityChecker.addObserver(capture(observerSlot)) } answers
            {
                connectivityObserver = observerSlot.captured
            }

        engine =
            DesktopSyncEngine(
                syncService = syncService,
                messageService = messageService,
                contactService = contactService,
                analytics = analytics,
                connectivityChecker = connectivityChecker,
                notifier = notifier,
            )
    }

    private fun stubLoggedIn() {
        every { syncService.login("user", "pass") } returns AuthTokenResponse("tok", "user", "USER")
        engine.login("user", "pass")
    }

    private fun stubSyncSuccess(pushed: Int = 0, pulled: Int = 0, conflicts: Int = 0): SyncStats {
        val stats = SyncStats(pushedCount = pushed, pulledCount = pulled, conflictCount = conflicts)
        every { syncService.sync() } returns stats
        return stats
    }

    @Test
    fun `login success updates state and starts auto-sync`() {
        every { syncService.login("alice", "pw") } returns AuthTokenResponse("tok", "alice", "ADMIN")

        val result = engine.login("alice", "pw")

        assertTrue(result.isSuccess)
        assertTrue(engine.state.isLoggedIn)
        assertEquals("alice", engine.state.userName)
        assertEquals("ADMIN", engine.state.userRole)
        assertEquals("Logged in", engine.state.status)
        verify { analytics.identify("alice", mapOf("role" to "ADMIN")) }
        verify { analytics.track("alice", "user_login") }
        verify { notifier.notifySuccess("Logged in as alice") }
    }

    @Test
    fun `login failure returns Result failure`() {
        every { syncService.login("alice", "bad") } throws RuntimeException("Bad credentials")

        val result = engine.login("alice", "bad")

        assertTrue(result.isFailure)
        assertFalse(engine.state.isLoggedIn)
        assertEquals("Login failed: Bad credentials", engine.state.status)
        verify { notifier.notifyFailure("Login failed: Bad credentials") }
    }

    @Test
    fun `login session expired fires session expired`() {
        every { syncService.login("alice", "pw") } throws SessionExpiredException()

        val result = engine.login("alice", "pw")

        assertTrue(result.isFailure)
        assertEquals("Session expired", engine.state.status)
        assertFalse(engine.state.isLoggedIn)
    }

    @Test
    fun `register success updates state`() {
        every { syncService.register("bob", "pw") } returns AuthTokenResponse("tok2", "bob", "USER")

        val result = engine.register("bob", "pw")

        assertTrue(result.isSuccess)
        assertTrue(engine.state.isLoggedIn)
        assertEquals("bob", engine.state.userName)
        assertEquals("Registered", engine.state.status)
        verify { analytics.track("bob", "user_register") }
        verify { notifier.notifySuccess("Registered as bob") }
    }

    @Test
    fun `register failure returns Result failure`() {
        every { syncService.register("bob", "pw") } throws RuntimeException("Taken")

        val result = engine.register("bob", "pw")

        assertTrue(result.isFailure)
        assertEquals("Registration failed: Taken", engine.state.status)
        verify { notifier.notifyFailure("Registration failed: Taken") }
    }

    @Test
    fun `logout clears state and tracks analytics`() {
        stubLoggedIn()

        engine.logout()

        assertFalse(engine.state.isLoggedIn)
        assertEquals("Logged out", engine.state.status)
        assertEquals("", engine.state.userName)
    }

    @Test
    fun `loadData fetches messages and contacts`() {
        every { messageService.listMessages(any()) } returns
            io.github.rygel.outerstellar.platform.model.PagedResult(
                items = listOf(MessageSummary("s1", "a", "c", 1L, false)),
                metadata = io.github.rygel.outerstellar.platform.model.PaginationMetadata(1, 100, 1),
            )
        every { contactService.listContacts(any()) } returns
            listOf(ContactSummary("c1", "Alice", emptyList(), emptyList(), emptyList(), "", "", "", 1L, false))

        engine.loadData()

        assertEquals(1, engine.state.messages.size)
        assertEquals(1, engine.state.contacts.size)
        assertEquals("s1", engine.state.messages[0].syncId)
        assertEquals("c1", engine.state.contacts[0].syncId)
    }

    @Test
    fun `loadData with search query passes query to services`() {
        every { messageService.listMessages(query = "test") } returns
            io.github.rygel.outerstellar.platform.model.PagedResult(
                items = emptyList(),
                metadata = io.github.rygel.outerstellar.platform.model.PaginationMetadata(1, 100, 0),
            )

        engine.setSearchQuery("test")
        engine.loadData()

        verify { messageService.listMessages(query = "test") }
        verify { contactService.listContacts(query = "test") }
    }

    @Test
    fun `createLocalMessage success`() {
        every { messageService.createLocalMessage("author", "content") } returns mockk(relaxed = true)
        every { messageService.listMessages(any()) } returns
            io.github.rygel.outerstellar.platform.model.PagedResult(
                items = emptyList(),
                metadata = io.github.rygel.outerstellar.platform.model.PaginationMetadata(1, 100, 0),
            )

        val result = engine.createLocalMessage("author", "content")

        assertTrue(result.isSuccess)
        verify { messageService.createLocalMessage("author", "content") }
        verify { messageService.listMessages(any()) }
    }

    @Test
    fun `createLocalMessage validation failure returns Result failure`() {
        every { messageService.createLocalMessage("", "content") } throws
            io.github.rygel.outerstellar.platform.model.ValidationException(listOf("Author is required."))

        val result = engine.createLocalMessage("", "content")

        assertTrue(result.isFailure)
    }

    @Test
    fun `createContact success`() {
        stubLoggedIn()
        every { contactService.createContact(any(), any(), any(), any(), any(), any(), any()) } returns
            mockk(relaxed = true)
        every { contactService.listContacts(any()) } returns emptyList()

        val result = engine.createContact("Alice", listOf("a@b.c"), emptyList(), emptyList(), "Co", "Addr", "Dept")

        assertTrue(result.isSuccess)
        verify { analytics.track("user", "contact_created", mapOf("name" to "Alice")) }
    }

    @Test
    fun `createContact without contact service returns failure`() {
        val engineNoContact =
            DesktopSyncEngine(
                syncService = syncService,
                messageService = messageService,
                contactService = null,
                analytics = analytics,
            )

        val result = engineNoContact.createContact("A", emptyList(), emptyList(), emptyList(), "", "", "")

        assertTrue(result.isFailure)
        assertEquals("Contact service not available", result.exceptionOrNull()?.message)
    }

    @Test
    fun `sync success updates state and tracks analytics`() {
        stubLoggedIn()
        stubSyncSuccess(pushed = 3, pulled = 2, conflicts = 1)

        val result = engine.sync()

        assertTrue(result.isSuccess)
        assertFalse(engine.state.isSyncing)
        assertEquals("Synced: pushed 3, pulled 2, 1 conflict(s)", engine.state.status)
        verify { analytics.track("user", "manual_sync") }
        verify { notifier.notifySuccess(engine.state.status) }
    }

    @Test
    fun `sync when offline returns failure`() {
        stubLoggedIn()
        connectivityObserver?.invoke(false)

        val result = engine.sync()

        assertTrue(result.isFailure)
        assertEquals("Offline", result.exceptionOrNull()?.message)
        verify { notifier.notifyFailure("Cannot sync while offline") }
    }

    @Test
    fun `sync when not logged in returns failure`() {
        val result = engine.sync()

        assertTrue(result.isFailure)
        assertEquals("Not logged in", result.exceptionOrNull()?.message)
    }

    @Test
    fun `sync when already syncing is no-op`() {
        stubLoggedIn()
        every { syncService.sync() } answers
            {
                Thread.sleep(100)
                SyncStats()
            }

        val results = mutableListOf<Result<Unit>>()
        val t1 = Thread { results.add(engine.sync()) }
        val t2 = Thread { results.add(engine.sync()) }
        t1.start()
        Thread.sleep(20)
        t2.start()
        t1.join()
        t2.join()

        assertTrue(results.all { it.isSuccess })
        verify(exactly = 1) { syncService.sync() }
    }

    @Test
    fun `sync failure calls notifier with failure`() {
        stubLoggedIn()
        every { syncService.sync() } throws RuntimeException("Network error")

        val result = engine.sync()

        assertTrue(result.isFailure)
        assertFalse(engine.state.isSyncing)
        assertEquals("Sync failed: Network error", engine.state.status)
        verify { notifier.notifyFailure("Sync failed: Network error") }
    }

    @Test
    fun `sync session expired fires handleSessionExpired`() {
        stubLoggedIn()
        every { syncService.sync() } throws SessionExpiredException()

        val result = engine.sync()

        assertTrue(result.isFailure)
        assertEquals("Session expired", engine.state.status)
        assertFalse(engine.state.isLoggedIn)
    }

    @Test
    fun `sync auto mode does not notify`() {
        stubLoggedIn()
        clearMocks(notifier)
        stubSyncSuccess(pushed = 1)

        val result = engine.sync(isAuto = true)

        assertTrue(result.isSuccess)
        verify(exactly = 0) { notifier.notifySuccess(any()) }
        verify { analytics.track("user", "auto_sync") }
    }

    @Test
    fun `startAutoSync creates executor and stopAutoSync shuts down`() {
        engine.startAutoSync()
        engine.stopAutoSync()
    }

    @Test
    fun `double startAutoSync is no-op`() {
        engine.startAutoSync()
        engine.startAutoSync()
        engine.stopAutoSync()
    }

    @Test
    fun `loadUsers success updates state`() {
        val users = listOf(UserSummary("1", "alice", "a@b.c", "USER", true))
        every { syncService.listUsers() } returns users

        engine.loadUsers()

        assertEquals(1, engine.state.adminUsers.size)
        assertEquals("alice", engine.state.adminUsers[0].username)
    }

    @Test
    fun `loadUsers session expired fires handleSessionExpired`() {
        every { syncService.listUsers() } throws SessionExpiredException()

        engine.loadUsers()

        assertEquals("Session expired", engine.state.status)
        assertFalse(engine.state.isLoggedIn)
    }

    @Test
    fun `setUserEnabled success`() {
        stubLoggedIn()
        every { syncService.setUserEnabled("1", false) } returns Unit
        every { syncService.listUsers() } returns emptyList()

        val result = engine.setUserEnabled("1", false)

        assertTrue(result.isSuccess)
        verify { analytics.track("user", "user_enabled_changed", mapOf("userId" to "1", "enabled" to false)) }
    }

    @Test
    fun `setUserEnabled session expired`() {
        every { syncService.setUserEnabled("1", true) } throws SessionExpiredException()

        val result = engine.setUserEnabled("1", true)

        assertTrue(result.isFailure)
        assertEquals("Session expired", engine.state.status)
    }

    @Test
    fun `setUserRole success`() {
        stubLoggedIn()
        every { syncService.setUserRole("1", "ADMIN") } returns Unit
        every { syncService.listUsers() } returns emptyList()

        val result = engine.setUserRole("1", "ADMIN")

        assertTrue(result.isSuccess)
        verify { analytics.track("user", "user_role_changed", mapOf("userId" to "1", "role" to "ADMIN")) }
    }

    @Test
    fun `setUserRole failure`() {
        every { syncService.setUserRole("1", "ADMIN") } throws RuntimeException("Fail")

        val result = engine.setUserRole("1", "ADMIN")

        assertTrue(result.isFailure)
    }

    @Test
    fun `loadNotifications success`() {
        val notifs = listOf(NotificationSummary("n1", "Title", "Body", "INFO", false, "2025-01-01"))
        every { syncService.listNotifications() } returns notifs

        engine.loadNotifications()

        assertEquals(1, engine.state.notifications.size)
        assertEquals("n1", engine.state.notifications[0].id)
    }

    @Test
    fun `loadNotifications session expired`() {
        every { syncService.listNotifications() } throws SessionExpiredException()

        engine.loadNotifications()

        assertEquals("Session expired", engine.state.status)
    }

    @Test
    fun `markNotificationRead reloads notifications`() {
        every { syncService.markNotificationRead("n1") } returns Unit
        every { syncService.listNotifications() } returns emptyList()

        engine.markNotificationRead("n1")

        verify { syncService.markNotificationRead("n1") }
        verify { syncService.listNotifications() }
    }

    @Test
    fun `markAllNotificationsRead reloads notifications`() {
        every { syncService.markAllNotificationsRead() } returns Unit
        every { syncService.listNotifications() } returns emptyList()

        engine.markAllNotificationsRead()

        verify { syncService.markAllNotificationsRead() }
        verify { syncService.listNotifications() }
    }

    @Test
    fun `markNotificationRead session expired`() {
        every { syncService.markNotificationRead("n1") } throws SessionExpiredException()

        engine.markNotificationRead("n1")

        assertEquals("Session expired", engine.state.status)
    }

    @Test
    fun `loadProfile success`() {
        every { syncService.fetchProfile() } returns UserProfileResponse("alice", "a@b.c", "http://avatar", true, false)

        engine.loadProfile()

        assertEquals("alice", engine.state.userName)
        assertEquals("a@b.c", engine.state.userEmail)
        assertEquals("http://avatar", engine.state.userAvatarUrl)
        assertTrue(engine.state.emailNotificationsEnabled)
        assertFalse(engine.state.pushNotificationsEnabled)
    }

    @Test
    fun `loadProfile session expired`() {
        every { syncService.fetchProfile() } throws SessionExpiredException()

        engine.loadProfile()

        assertEquals("Session expired", engine.state.status)
    }

    @Test
    fun `updateProfile success`() {
        stubLoggedIn()
        every { syncService.updateProfile("new@b.c", any(), any()) } returns Unit
        every { syncService.fetchProfile() } returns UserProfileResponse("user", "new@b.c", null, true, true)

        val result = engine.updateProfile("new@b.c")

        assertTrue(result.isSuccess)
        verify { analytics.track("user", "profile_updated") }
        verify { notifier.notifySuccess("Profile updated") }
    }

    @Test
    fun `updateProfile session expired`() {
        every { syncService.updateProfile("x@b.c", any(), any()) } throws SessionExpiredException()

        val result = engine.updateProfile("x@b.c")

        assertTrue(result.isFailure)
        assertEquals("Session expired", engine.state.status)
    }

    @Test
    fun `updateNotificationPreferences success`() {
        val result = engine.updateNotificationPreferences(emailEnabled = false, pushEnabled = true)

        assertTrue(result.isSuccess)
        assertFalse(engine.state.emailNotificationsEnabled)
        assertTrue(engine.state.pushNotificationsEnabled)
    }

    @Test
    fun `updateNotificationPreferences session expired`() {
        every { syncService.updateNotificationPreferences(any(), any()) } throws SessionExpiredException()

        val result = engine.updateNotificationPreferences(true, true)

        assertTrue(result.isFailure)
        assertEquals("Session expired", engine.state.status)
    }

    @Test
    fun `deleteAccount success`() {
        stubLoggedIn()

        val result = engine.deleteAccount()

        assertTrue(result.isSuccess)
        assertFalse(engine.state.isLoggedIn)
        assertEquals("Account deleted", engine.state.status)
        verify { syncService.deleteAccount() }
        verify { syncService.logout() }
        verify { analytics.track("user", "account_deleted") }
        verify { notifier.notifySuccess("Account deleted") }
    }

    @Test
    fun `deleteAccount failure`() {
        stubLoggedIn()
        every { syncService.deleteAccount() } throws RuntimeException("Fail")

        val result = engine.deleteAccount()

        assertTrue(result.isFailure)
        verify { notifier.notifyFailure("Account deletion failed: Fail") }
    }

    @Test
    fun `changePassword success`() {
        stubLoggedIn()

        val result = engine.changePassword("old", "new")

        assertTrue(result.isSuccess)
        verify { syncService.changePassword("old", "new") }
        verify { analytics.track("user", "password_changed") }
        verify { notifier.notifySuccess("Password changed") }
    }

    @Test
    fun `changePassword session expired`() {
        every { syncService.changePassword(any(), any()) } throws SessionExpiredException()

        val result = engine.changePassword("old", "new")

        assertTrue(result.isFailure)
        assertEquals("Session expired", engine.state.status)
    }

    @Test
    fun `changePassword failure`() {
        every { syncService.changePassword(any(), any()) } throws RuntimeException("Weak")

        val result = engine.changePassword("old", "new")

        assertTrue(result.isFailure)
        verify { notifier.notifyFailure("Password change failed: Weak") }
    }

    @Test
    fun `requestPasswordReset success`() {
        val result = engine.requestPasswordReset("a@b.c")

        assertTrue(result.isSuccess)
        verify { syncService.requestPasswordReset("a@b.c") }
        verify { notifier.notifySuccess("Password reset email sent") }
    }

    @Test
    fun `requestPasswordReset failure`() {
        every { syncService.requestPasswordReset(any()) } throws RuntimeException("Fail")

        val result = engine.requestPasswordReset("a@b.c")

        assertTrue(result.isFailure)
        verify { notifier.notifyFailure("Password reset request failed: Fail") }
    }

    @Test
    fun `resetPassword success`() {
        val result = engine.resetPassword("token123", "newpass")

        assertTrue(result.isSuccess)
        verify { syncService.resetPassword("token123", "newpass") }
        verify { notifier.notifySuccess("Password has been reset") }
    }

    @Test
    fun `resetPassword failure`() {
        every { syncService.resetPassword(any(), any()) } throws RuntimeException("Expired")

        val result = engine.resetPassword("tok", "pw")

        assertTrue(result.isFailure)
        verify { notifier.notifyFailure("Password reset failed: Expired") }
    }

    @Test
    fun `resolveConflict success`() {
        every { messageService.resolveConflict("s1", ConflictStrategy.MINE) } returns Unit
        every { messageService.listMessages(any()) } returns
            io.github.rygel.outerstellar.platform.model.PagedResult(
                items = emptyList(),
                metadata = io.github.rygel.outerstellar.platform.model.PaginationMetadata(1, 100, 0),
            )

        engine.resolveConflict("s1", ConflictStrategy.MINE)

        verify { messageService.resolveConflict("s1", ConflictStrategy.MINE) }
    }

    @Test
    fun `resolveConflict failure fires error`() {
        every { messageService.resolveConflict("s1", ConflictStrategy.SERVER) } throws
            RuntimeException("No conflict data")

        val listener = mockk<EngineListener>(relaxed = true)
        engine.addListener(listener)

        engine.resolveConflict("s1", ConflictStrategy.SERVER)

        verify { listener.onError("resolveConflict", "No conflict data") }
    }

    @Test
    fun `connectivity observer updates isOnline state`() {
        val listener = mockk<EngineListener>(relaxed = true)
        engine.addListener(listener)

        connectivityObserver?.invoke(false)

        assertFalse(engine.state.isOnline)
        verify { listener.onStateChanged(engine.state) }

        connectivityObserver?.invoke(true)

        assertTrue(engine.state.isOnline)
    }

    @Test
    fun `setSearchQuery updates state and triggers loadData`() {
        every { messageService.listMessages(query = "hello") } returns
            io.github.rygel.outerstellar.platform.model.PagedResult(
                items = emptyList(),
                metadata = io.github.rygel.outerstellar.platform.model.PaginationMetadata(1, 100, 0),
            )

        engine.setSearchQuery("hello")

        assertEquals("hello", engine.state.searchQuery)
        verify { messageService.listMessages(query = "hello") }
    }

    @Test
    fun `addListener receives onStateChanged`() {
        val listener = mockk<EngineListener>(relaxed = true)
        engine.addListener(listener)

        every { syncService.login("a", "b") } returns AuthTokenResponse("t", "a", "USER")
        engine.login("a", "b")

        verify(atLeast = 1) { listener.onStateChanged(any()) }
    }

    @Test
    fun `onSessionExpired fired on session expiry`() {
        val listener = mockk<EngineListener>(relaxed = true)
        engine.addListener(listener)

        every { syncService.listNotifications() } throws SessionExpiredException()
        engine.loadNotifications()

        verify { listener.onSessionExpired() }
        verify { notifier.notifyFailure("Session expired. Please log in again.") }
    }

    @Test
    fun `removeListener stops receiving events`() {
        val listener = mockk<EngineListener>(relaxed = true)
        engine.addListener(listener)
        engine.removeListener(listener)

        every { syncService.login("a", "b") } returns AuthTokenResponse("t", "a", "USER")
        engine.login("a", "b")

        verify(exactly = 0) { listener.onStateChanged(any()) }
    }

    @Test
    fun `shutdown clears listeners and stops auto-sync`() {
        val listener = mockk<EngineListener>(relaxed = true)
        engine.addListener(listener)
        engine.startAutoSync()

        engine.shutdown()

        assertTrue(engine.listeners.isEmpty())
        verify { connectivityChecker.stop() }
    }

    @Test
    fun `unreadNotificationCount counts unread`() {
        every { syncService.listNotifications() } returns
            listOf(
                NotificationSummary("1", "A", "B", "INFO", false, "2025-01-01"),
                NotificationSummary("2", "C", "D", "INFO", true, "2025-01-01"),
                NotificationSummary("3", "E", "F", "INFO", false, "2025-01-01"),
            )

        engine.loadNotifications()

        assertEquals(2, engine.state.unreadNotificationCount)
    }

    @Test
    fun `sync with zero stats shows up to date`() {
        stubLoggedIn()
        stubSyncSuccess(pushed = 0, pulled = 0, conflicts = 0)

        engine.sync()

        assertEquals("Everything up to date", engine.state.status)
    }

    @Test
    fun `sync offline auto mode does not notify`() {
        stubLoggedIn()
        connectivityObserver?.invoke(false)

        val result = engine.sync(isAuto = true)

        assertTrue(result.isFailure)
        verify(exactly = 0) { notifier.notifyFailure(any()) }
    }

    @Test
    fun `loadMessages passes search query`() {
        every { messageService.listMessages(query = "find") } returns
            io.github.rygel.outerstellar.platform.model.PagedResult(
                items = emptyList(),
                metadata = io.github.rygel.outerstellar.platform.model.PaginationMetadata(1, 100, 0),
            )

        engine.setSearchQuery("find")

        verify { messageService.listMessages(query = "find") }
    }

    @Test
    fun `loadMessages with blank query passes null`() {
        every { messageService.listMessages(query = null) } returns
            io.github.rygel.outerstellar.platform.model.PagedResult(
                items = emptyList(),
                metadata = io.github.rygel.outerstellar.platform.model.PaginationMetadata(1, 100, 0),
            )

        engine.loadMessages()

        verify { messageService.listMessages(query = null) }
    }

    @Test
    fun `loadContacts without contactService is no-op`() {
        val engineNoContact =
            DesktopSyncEngine(
                syncService = syncService,
                messageService = messageService,
                contactService = null,
                analytics = analytics,
            )

        engineNoContact.loadContacts()

        verify(exactly = 0) { contactService.listContacts(any()) }
    }

    @Test
    fun `handleSessionExpired clears login state and notifies listeners`() {
        stubLoggedIn()
        val listener = mockk<EngineListener>(relaxed = true)
        engine.addListener(listener)

        every { syncService.fetchProfile() } throws SessionExpiredException()
        engine.loadProfile()

        assertFalse(engine.state.isLoggedIn)
        assertEquals("Session expired", engine.state.status)
        verify { listener.onSessionExpired() }
        verify { syncService.logout() }
        verify { notifier.notifyFailure("Session expired. Please log in again.") }
    }

    @Test
    fun `startConnectivityChecker delegates to checker`() {
        engine.startConnectivityChecker()
        verify { connectivityChecker.start() }
    }

    @Test
    fun `stopConnectivityChecker delegates to checker`() {
        engine.stopConnectivityChecker()
        verify { connectivityChecker.stop() }
    }

    @Test
    fun `initial state has defaults`() {
        val freshEngine =
            DesktopSyncEngine(syncService = syncService, messageService = messageService, analytics = analytics)

        assertFalse(freshEngine.state.isLoggedIn)
        assertEquals("", freshEngine.state.userName)
        assertNull(freshEngine.state.userRole)
        assertTrue(freshEngine.state.isOnline)
        assertFalse(freshEngine.state.isSyncing)
        assertEquals("", freshEngine.state.status)
        assertTrue(freshEngine.state.messages.isEmpty())
        assertTrue(freshEngine.state.contacts.isEmpty())
    }

    @Test
    fun `sync failure auto mode does not fire error to listeners`() {
        stubLoggedIn()
        val listener = mockk<EngineListener>(relaxed = true)
        engine.addListener(listener)
        every { syncService.sync() } throws RuntimeException("fail")

        engine.sync(isAuto = true)

        verify(exactly = 0) { listener.onError(any(), any()) }
    }
}
