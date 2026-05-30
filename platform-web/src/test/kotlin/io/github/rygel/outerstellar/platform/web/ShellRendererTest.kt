package io.github.rygel.outerstellar.platform.web

import io.github.rygel.outerstellar.platform.composition.PlatformMode
import io.github.rygel.outerstellar.platform.model.SessionLookup
import io.github.rygel.outerstellar.platform.model.User
import io.github.rygel.outerstellar.platform.model.UserRole
import io.github.rygel.outerstellar.platform.security.SessionService
import io.github.rygel.outerstellar.platform.web.composition.PlatformPageSets
import io.mockk.every
import io.mockk.mockk
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.http4k.core.Method.GET
import org.http4k.core.Request
import org.http4k.core.cookie.Cookie
import org.http4k.core.cookie.cookie

class ShellRendererTest {
    private val user =
        User(
            id = UUID.randomUUID(),
            username = "repoquality-admin",
            email = "repoquality@example.com",
            passwordHash = "hash",
            role = UserRole.ADMIN,
        )

    private fun requestContext(shellCookie: String = "sidebar"): RequestContext {
        val sessionService = mockk<SessionService>()
        every { sessionService.lookupSession("session-token") } returns SessionLookup.Active(user)
        return RequestContext(
            Request(GET, "/repoquality")
                .cookie(Cookie(RequestContext.SESSION_COOKIE, "session-token"))
                .cookie(Cookie(RequestContext.SHELL_COOKIE, shellCookie)),
            sessionService = sessionService,
        )
    }

    @Test
    fun `plugin hosted shell uses plugin branding and hides default chrome`() {
        val shell =
            ShellRenderer(
                    requestContext(shellCookie = "topbar"),
                    appVersion = "1.6.4",
                    shellConfig =
                        ShellConfig(
                            mode = PlatformMode.PluginHostedApp,
                            appLabel = "RepoQuality",
                            appHomeUrl = "/repoquality",
                        ),
                )
                .shell("RepoQuality", "/repoquality")

        assertEquals("RepoQuality", shell.appTitle)
        assertEquals("RepoQuality", shell.appTagline)
        assertEquals("RepoQuality", shell.footerCopy)
        assertEquals("v1.6.4", shell.footerVersion)
        assertEquals("/repoquality", shell.appHomeUrl)
        assertNull(shell.searchUrl)
        assertNull(shell.notificationsUrl)
        assertNull(shell.profileUrl)
        assertNull(shell.changePasswordUrl)
        assertTrue(shell.navLinks.isEmpty())
    }

    @Test
    fun `nav cache keeps full and plugin hosted shells separate`() {
        val requestContext = requestContext()
        val fullShell =
            ShellRenderer(requestContext, shellConfig = ShellConfig(mode = PlatformMode.FullPlatformApp))
                .shell("Outerstellar", "/")
        val pluginShell =
            ShellRenderer(
                    requestContext,
                    shellConfig =
                        ShellConfig(
                            mode = PlatformMode.PluginHostedApp,
                            appLabel = "RepoQuality",
                            appHomeUrl = "/repoquality",
                            includedPlatformPages = setOf(PlatformPageSets.SEARCH, PlatformPageSets.NOTIFICATIONS),
                        ),
                )
                .shell("RepoQuality", "/repoquality")

        assertTrue(fullShell.navLinks.any { it.url == "/contacts" })
        assertTrue(fullShell.navLinks.any { it.url == "/admin/users" })
        assertFalse(pluginShell.navLinks.any { it.url == "/contacts" })
        assertFalse(pluginShell.navLinks.any { it.url == "/admin/users" })
        assertTrue(pluginShell.navLinks.any { it.url == "/search" })
        assertEquals("/notifications", pluginShell.notificationsUrl)
        assertNull(pluginShell.profileUrl)
        assertNull(pluginShell.changePasswordUrl)
    }
}
