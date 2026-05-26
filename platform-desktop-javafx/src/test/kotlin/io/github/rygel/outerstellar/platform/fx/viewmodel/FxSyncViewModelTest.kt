package io.github.rygel.outerstellar.platform.fx.viewmodel

import io.github.rygel.outerstellar.i18n.I18nService
import io.github.rygel.outerstellar.platform.model.ConflictStrategy
import io.github.rygel.outerstellar.platform.sync.engine.module.AdminModule
import io.github.rygel.outerstellar.platform.sync.engine.module.AuthModule
import io.github.rygel.outerstellar.platform.sync.engine.module.NotificationModule
import io.github.rygel.outerstellar.platform.sync.engine.module.ProfileModule
import io.github.rygel.outerstellar.platform.sync.engine.module.SyncDataModule
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.util.Locale
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
            } catch (_: IllegalStateException) {}
        }
    }

    private lateinit var authModule: AuthModule
    private lateinit var syncDataModule: SyncDataModule
    private lateinit var profileModule: ProfileModule
    private lateinit var adminModule: AdminModule
    private lateinit var notificationModule: NotificationModule
    private lateinit var i18n: I18nService
    private lateinit var viewModel: FxSyncViewModel

    @BeforeEach
    fun setUp() {
        authModule = mockk(relaxed = true)
        syncDataModule = mockk(relaxed = true)
        profileModule = mockk(relaxed = true)
        adminModule = mockk(relaxed = true)
        notificationModule = mockk(relaxed = true)
        i18n = I18nService.create("messages").also { it.setLocale(Locale.ENGLISH) }
        viewModel = FxSyncViewModel(authModule, syncDataModule, profileModule, adminModule, notificationModule, i18n)
    }

    @Test
    fun `initial state reflects module defaults`() {
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
    fun `login delegates to authModule`() {
        every { authModule.login("user", "pass") } returns Result.success(Unit)
        val task = viewModel.login("user", "pass")
        task.run()
        verify { authModule.login("user", "pass") }
    }

    @Test
    fun `register delegates to authModule`() {
        every { authModule.register("newuser", "pass") } returns Result.success(Unit)
        val task = viewModel.register("newuser", "pass")
        task.run()
        verify { authModule.register("newuser", "pass") }
    }

    @Test
    fun `logout delegates to authModule`() {
        viewModel.logout().run()
        verify { authModule.logout() }
    }

    @Test
    fun `sync delegates to syncDataModule`() {
        every { syncDataModule.sync(false) } returns Result.success(Unit)
        viewModel.sync().run()
        verify { syncDataModule.sync(false) }
    }

    @Test
    fun `sync with auto parameter delegates to syncDataModule`() {
        every { syncDataModule.sync(true) } returns Result.success(Unit)
        viewModel.sync(isAuto = true).run()
        verify { syncDataModule.sync(true) }
    }

    @Test
    fun `changePassword delegates to authModule`() {
        every { authModule.changePassword("old", "new") } returns Result.success(Unit)
        viewModel.changePassword("old", "new").run()
        verify { authModule.changePassword("old", "new") }
    }

    @Test
    fun `requestPasswordReset delegates to authModule`() {
        every { authModule.requestPasswordReset("a@b.com") } returns Result.success(Unit)
        viewModel.requestPasswordReset("a@b.com").run()
        verify { authModule.requestPasswordReset("a@b.com") }
    }

    @Test
    fun `resetPassword delegates to authModule`() {
        every { authModule.resetPassword("token", "newpass") } returns Result.success(Unit)
        viewModel.resetPassword("token", "newpass").run()
        verify { authModule.resetPassword("token", "newpass") }
    }

    @Test
    fun `loadUsers delegates to adminModule`() {
        viewModel.loadUsers().run()
        verify { adminModule.loadUsers() }
    }

    @Test
    fun `setUserEnabled delegates to adminModule`() {
        every { adminModule.setUserEnabled("id", true) } returns Result.success(Unit)
        viewModel.setUserEnabled("id", true).run()
        verify { adminModule.setUserEnabled("id", true) }
    }

    @Test
    fun `setUserRole delegates to adminModule`() {
        every { adminModule.setUserRole("id", "ADMIN") } returns Result.success(Unit)
        viewModel.setUserRole("id", "ADMIN").run()
        verify { adminModule.setUserRole("id", "ADMIN") }
    }

    @Test
    fun `loadNotifications delegates to notificationModule`() {
        viewModel.loadNotifications().run()
        verify { notificationModule.loadNotifications() }
    }

    @Test
    fun `markNotificationRead delegates to notificationModule`() {
        viewModel.markNotificationRead("notif1").run()
        verify { notificationModule.markNotificationRead("notif1") }
    }

    @Test
    fun `markAllNotificationsRead delegates to notificationModule`() {
        viewModel.markAllNotificationsRead().run()
        verify { notificationModule.markAllNotificationsRead() }
    }

    @Test
    fun `loadProfile delegates to profileModule`() {
        viewModel.loadProfile().run()
        verify { profileModule.loadProfile() }
    }

    @Test
    fun `updateProfile delegates to profileModule`() {
        every { profileModule.updateProfile("e@m.com", "user", "av") } returns Result.success(Unit)
        viewModel.updateProfile("e@m.com", "user", "av").run()
        verify { profileModule.updateProfile("e@m.com", "user", "av") }
    }

    @Test
    fun `deleteAccount delegates to profileModule`() {
        every { profileModule.deleteAccount("secret") } returns Result.success(Unit)
        viewModel.deleteAccount("secret").run()
        verify { profileModule.deleteAccount("secret") }
    }

    @Test
    fun `updateNotificationPreferences delegates to profileModule`() {
        every { profileModule.updateNotificationPreferences(true, false) } returns Result.success(Unit)
        viewModel.updateNotificationPreferences(true, false).run()
        verify { profileModule.updateNotificationPreferences(true, false) }
    }

    @Test
    fun `createLocalMessage delegates to syncDataModule`() {
        every { syncDataModule.createLocalMessage("author", "content") } returns Result.success(Unit)
        viewModel.createLocalMessage("author", "content").run()
        verify { syncDataModule.createLocalMessage("author", "content") }
    }

    @Test
    fun `resolveConflict delegates to syncDataModule`() {
        viewModel.resolveConflict("id", ConflictStrategy.MINE).run()
        verify { syncDataModule.resolveConflict("id", ConflictStrategy.MINE) }
    }

    @Test
    fun `updateContact delegates to syncDataModule`() {
        every { syncDataModule.updateContact(any(), any(), any(), any(), any(), any(), any(), any()) } returns
            Result.success(Unit)
        viewModel.updateContact("id", "n", listOf("e"), listOf("p"), listOf("s"), "c", "a", "d").run()
        verify { syncDataModule.updateContact("id", "n", listOf("e"), listOf("p"), listOf("s"), "c", "a", "d") }
    }

    @Test
    fun `createContact delegates to syncDataModule`() {
        every { syncDataModule.createContact(any(), any(), any(), any(), any(), any(), any()) } returns
            Result.success(Unit)
        viewModel.createContact("n", listOf("e"), listOf("p"), listOf("s"), "c", "a", "d").run()
        verify { syncDataModule.createContact("n", listOf("e"), listOf("p"), listOf("s"), "c", "a", "d") }
    }

    @Test
    fun `loadData delegates to syncDataModule`() {
        viewModel.loadData().run()
        verify { syncDataModule.loadData() }
    }

    @Test
    fun `loadMessages delegates to syncDataModule`() {
        viewModel.loadMessages().run()
        verify { syncDataModule.loadMessages() }
    }

    @Test
    fun `loadContacts delegates to syncDataModule`() {
        viewModel.loadContacts().run()
        verify { syncDataModule.loadContacts() }
    }

    @Test
    fun `addListener is called on all modules in constructor`() {
        verify { syncDataModule.addListener(any()) }
        verify { authModule.addListener(any()) }
        verify { profileModule.addListener(any()) }
        verify { adminModule.addListener(any()) }
        verify { notificationModule.addListener(any()) }
    }

    @Test
    fun `shutdown removes listeners from all modules`() {
        viewModel.shutdown()
        verify { syncDataModule.removeListener(any()) }
        verify { authModule.removeListener(any()) }
        verify { profileModule.removeListener(any()) }
        verify { adminModule.removeListener(any()) }
        verify { notificationModule.removeListener(any()) }
    }

    @Test
    fun `startAutoSync delegates to syncDataModule`() {
        viewModel.startAutoSync()
        verify { syncDataModule.startAutoSync() }
    }

    @Test
    fun `stopAutoSync delegates to syncDataModule`() {
        viewModel.stopAutoSync()
        verify { syncDataModule.stopAutoSync() }
    }
}
