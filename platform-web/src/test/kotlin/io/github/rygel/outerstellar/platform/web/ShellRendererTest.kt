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
    fun `extension hosted shell uses extension branding and hides default chrome`() {
        val shell =
            ShellRenderer(
                    requestContext(shellCookie = "topbar"),
                    appVersion = "1.6.4",
                    shellConfig =
                        ShellConfig(
                            mode = PlatformMode.ExtensionHost,
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
    fun `nav cache keeps full and extension hosted shells separate`() {
        val requestContext = requestContext()
        val fullShell =
            ShellRenderer(requestContext, shellConfig = ShellConfig(mode = PlatformMode.FullPlatform))
                .shell("Outerstellar", "/")
        val extensionShell =
            ShellRenderer(
                    requestContext,
                    shellConfig =
                        ShellConfig(
                            mode = PlatformMode.ExtensionHost,
                            appLabel = "RepoQuality",
                            appHomeUrl = "/repoquality",
                            includedPlatformPages = setOf(PlatformPageSets.SEARCH, PlatformPageSets.NOTIFICATIONS),
                        ),
                )
                .shell("RepoQuality", "/repoquality")

        assertTrue(fullShell.navLinks.any { it.url == "/contacts" })
        assertTrue(fullShell.navLinks.any { it.url == "/admin/users" })
        assertFalse(extensionShell.navLinks.any { it.url == "/contacts" })
        assertFalse(extensionShell.navLinks.any { it.url == "/admin/users" })
        assertTrue(extensionShell.navLinks.any { it.url == "/search" })
        assertEquals("/notifications", extensionShell.notificationsUrl)
        assertNull(extensionShell.profileUrl)
        assertNull(extensionShell.changePasswordUrl)
    }

    /**
     * Regression for #594: the platform must resolve its own web.* bundle regardless of the host's thread context
     * classloader. If the bundle load falls back to the TCCL, a host app either can't see platform-core's
     * messages.properties or hits a host-shipped one first, and translate() returns the raw key. Here we assert that
     * ShellRenderer.i18n resolves localized text for both English and French rather than echoing the key.
     */
    @Test
    fun `platform i18n bundle resolves web keys for english and french`() {
        val englishRenderer = ShellRenderer(requestContextForLang("en"))
        val frenchRenderer = ShellRenderer(requestContextForLang("fr"))

        val englishHeading = englishRenderer.i18n.translate("web.auth.heading")
        val frenchHeading = frenchRenderer.i18n.translate("web.auth.heading")

        assertEquals("Authentication page examples", englishHeading)
        assertEquals("Exemples de pages d'authentification", frenchHeading)
    }

    @Test
    fun `platform i18n bundle never returns the raw key for known web keys`() {
        val renderer = ShellRenderer(requestContextForLang("en"))

        val heading = renderer.i18n.translate("web.auth.heading")
        val navAuth = renderer.i18n.translate("web.nav.auth")

        assertFalse(heading.startsWith("web."), "Expected resolved text, got raw key: $heading")
        assertFalse(navAuth.startsWith("web."), "Expected resolved text, got raw key: $navAuth")
    }

    private fun requestContextForLang(lang: String): RequestContext {
        val sessionService = mockk<SessionService>()
        every { sessionService.lookupSession("session-token") } returns SessionLookup.Active(user)
        return RequestContext(
            Request(GET, "/repoquality?lang=$lang")
                .cookie(Cookie(RequestContext.SESSION_COOKIE, "session-token"))
                .cookie(Cookie(RequestContext.SHELL_COOKIE, "sidebar")),
            sessionService = sessionService,
        )
    }
}
