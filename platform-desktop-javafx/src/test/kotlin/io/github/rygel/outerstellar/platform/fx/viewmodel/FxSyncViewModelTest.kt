package io.github.rygel.outerstellar.platform.fx.viewmodel

import io.github.rygel.outerstellar.platform.model.ConflictStrategy
import io.github.rygel.outerstellar.platform.sync.engine.EngineState
import io.github.rygel.outerstellar.platform.sync.engine.SyncEngine
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import javafx.application.Platform
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class FxSyncViewModelTest {

    companion object {
        @BeforeAll
        @JvmStatic
        fun initJavaFX() {
            try {
                Platform.startup {}
            } catch (_: IllegalStateException) {
                // already initialized by another test
            }
        }
    }

    private lateinit var engine: SyncEngine
    private lateinit var viewModel: FxSyncViewModel

    @BeforeEach
    fun setUp() {
        engine = mockk(relaxed = true)
        every { engine.state } returns EngineState()
        viewModel = FxSyncViewModel(engine)
    }

    @Test
    fun `initial state reflects engine state`() {
        assertEquals("", viewModel.userName.get())
        assertEquals("", viewModel.userEmail.get())
        assertNull(viewModel.userAvatarUrl.get())
        assertNull(viewModel.userRole.get())
        assertFalse(viewModel.isLoggedIn.get())
        assertTrue(viewModel.isOnline.get())
        assertFalse(viewModel.isSyncing.get())
        assertEquals("Ready", viewModel.status.get())
        assertEquals("", viewModel.searchQuery.get())
        assertEquals(0, viewModel.messages.size)
        assertEquals(0, viewModel.contacts.size)
        assertEquals(0, viewModel.adminUsers.size)
        assertEquals(0, viewModel.notifications.size)
        assertEquals(0, viewModel.unreadNotificationCount.get())
    }

    @Test
    fun `login delegates to engine`() {
        every { engine.login("user", "pass") } returns Result.success(Unit)
        val task = viewModel.login("user", "pass")
        task.run()
        verify { engine.login("user", "pass") }
    }

    @Test
    fun `register delegates to engine`() {
        every { engine.register("newuser", "pass") } returns Result.success(Unit)
        val task = viewModel.register("newuser", "pass")
        task.run()
        verify { engine.register("newuser", "pass") }
    }

    @Test
    fun `logout delegates to engine`() {
        viewModel.logout().run()
        verify { engine.logout() }
    }

    @Test
    fun `sync delegates to engine`() {
        every { engine.sync(false) } returns Result.success(Unit)
        viewModel.sync().run()
        verify { engine.sync(false) }
    }

    @Test
    fun `sync with auto parameter delegates to engine`() {
        every { engine.sync(true) } returns Result.success(Unit)
        viewModel.sync(isAuto = true).run()
        verify { engine.sync(true) }
    }

    @Test
    fun `changePassword delegates to engine`() {
        every { engine.changePassword("old", "new") } returns Result.success(Unit)
        viewModel.changePassword("old", "new").run()
        verify { engine.changePassword("old", "new") }
    }

    @Test
    fun `requestPasswordReset delegates to engine`() {
        every { engine.requestPasswordReset("a@b.com") } returns Result.success(Unit)
        viewModel.requestPasswordReset("a@b.com").run()
        verify { engine.requestPasswordReset("a@b.com") }
    }

    @Test
    fun `resetPassword delegates to engine`() {
        every { engine.resetPassword("token", "newpass") } returns Result.success(Unit)
        viewModel.resetPassword("token", "newpass").run()
        verify { engine.resetPassword("token", "newpass") }
    }

    @Test
    fun `loadUsers delegates to engine`() {
        viewModel.loadUsers().run()
        verify { engine.loadUsers() }
    }

    @Test
    fun `setUserEnabled delegates to engine`() {
        every { engine.setUserEnabled("id", true) } returns Result.success(Unit)
        viewModel.setUserEnabled("id", true).run()
        verify { engine.setUserEnabled("id", true) }
    }

    @Test
    fun `setUserRole delegates to engine`() {
        every { engine.setUserRole("id", "ADMIN") } returns Result.success(Unit)
        viewModel.setUserRole("id", "ADMIN").run()
        verify { engine.setUserRole("id", "ADMIN") }
    }

    @Test
    fun `loadNotifications delegates to engine`() {
        viewModel.loadNotifications().run()
        verify { engine.loadNotifications() }
    }

    @Test
    fun `markNotificationRead delegates to engine`() {
        viewModel.markNotificationRead("notif1").run()
        verify { engine.markNotificationRead("notif1") }
    }

    @Test
    fun `markAllNotificationsRead delegates to engine`() {
        viewModel.markAllNotificationsRead().run()
        verify { engine.markAllNotificationsRead() }
    }

    @Test
    fun `loadProfile delegates to engine`() {
        viewModel.loadProfile().run()
        verify { engine.loadProfile() }
    }

    @Test
    fun `updateProfile delegates to engine`() {
        every { engine.updateProfile("e@m.com", "user", "av") } returns Result.success(Unit)
        viewModel.updateProfile("e@m.com", "user", "av").run()
        verify { engine.updateProfile("e@m.com", "user", "av") }
    }

    @Test
    fun `deleteAccount delegates to engine`() {
        every { engine.deleteAccount() } returns Result.success(Unit)
        viewModel.deleteAccount().run()
        verify { engine.deleteAccount() }
    }

    @Test
    fun `updateNotificationPreferences delegates to engine`() {
        every { engine.updateNotificationPreferences(true, false) } returns Result.success(Unit)
        viewModel.updateNotificationPreferences(true, false).run()
        verify { engine.updateNotificationPreferences(true, false) }
    }

    @Test
    fun `createLocalMessage delegates to engine`() {
        every { engine.createLocalMessage("author", "content") } returns Result.success(Unit)
        viewModel.createLocalMessage("author", "content").run()
        verify { engine.createLocalMessage("author", "content") }
    }

    @Test
    fun `resolveConflict delegates to engine`() {
        viewModel.resolveConflict("id", ConflictStrategy.MINE).run()
        verify { engine.resolveConflict("id", ConflictStrategy.MINE) }
    }

    @Test
    fun `createContact delegates to engine`() {
        every { engine.createContact(any(), any(), any(), any(), any(), any(), any()) } returns Result.success(Unit)
        viewModel.createContact("n", listOf("e"), listOf("p"), listOf("s"), "c", "a", "d").run()
        verify { engine.createContact("n", listOf("e"), listOf("p"), listOf("s"), "c", "a", "d") }
    }

    @Test
    fun `loadData delegates to engine`() {
        viewModel.loadData().run()
        verify { engine.loadData() }
    }

    @Test
    fun `loadMessages delegates to engine`() {
        viewModel.loadMessages().run()
        verify { engine.loadMessages() }
    }

    @Test
    fun `loadContacts delegates to engine`() {
        viewModel.loadContacts().run()
        verify { engine.loadContacts() }
    }

    @Test
    fun `addListener is called in constructor`() {
        verify { engine.addListener(any()) }
    }

    @Test
    fun `shutdown removes listener and shuts down engine`() {
        viewModel.shutdown()
        verify { engine.shutdown() }
    }

    @Test
    fun `startAutoSync delegates to engine`() {
        viewModel.startAutoSync()
        verify { engine.startAutoSync() }
    }

    @Test
    fun `stopAutoSync delegates to engine`() {
        viewModel.stopAutoSync()
        verify { engine.stopAutoSync() }
    }

    @Test
    fun `startConnectivityChecker delegates to engine`() {
        viewModel.startConnectivityChecker()
        verify { engine.startConnectivityChecker() }
    }

    @Test
    fun `stopConnectivityChecker delegates to engine`() {
        viewModel.stopConnectivityChecker()
        verify { engine.stopConnectivityChecker() }
    }

    @Test
    fun `shutdown does not throw when called`() {
        viewModel.shutdown()
        verify { engine.shutdown() }
    }
}
