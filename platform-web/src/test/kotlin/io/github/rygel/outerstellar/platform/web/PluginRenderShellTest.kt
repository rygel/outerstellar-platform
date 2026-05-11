package io.github.rygel.outerstellar.platform.web

import io.github.rygel.outerstellar.platform.security.SecurityService
import io.github.rygel.outerstellar.platform.security.UserRepository
import io.mockk.mockk
import kotlin.test.assertTrue
import org.http4k.template.TemplateRenderer
import org.junit.jupiter.api.Test

class PluginRenderShellTest {

    private fun createContext(): PluginContext {
        val renderer: TemplateRenderer = { "" }
        return PluginContext.forTesting(
            renderer = renderer,
            securityService = mockk<SecurityService>(relaxed = true),
            userRepository = mockk<UserRepository>(relaxed = true),
        )
    }

    private fun createShell(): ShellView =
        ShellView(
            pageTitle = "Test Page",
            appTitle = "Outerstellar",
            appTagline = "Tagline",
            currentPath = "/test",
            localeTag = "en",
            themeName = "dark",
            layoutClass = "",
            navLinks = emptyList(),
            themeSelector = SidebarSelector("", "", "", "", emptyList(), emptyList(), ""),
            languageSelector = SidebarSelector("", "", "", "", emptyList(), emptyList(), ""),
            layoutSelector = SidebarSelector("", "", "", "", emptyList(), emptyList(), ""),
            footerCopy = "Acme Corp",
            footerVersion = "1.0",
            footerStatusUrl = "",
            version = "1.0",
        )

    @Test
    fun `renderShell wraps bodyHtml in layout shell`() {
        val ctx = createContext()
        val shell = createShell()
        val bodyHtml = "<div id=\"plugin-content\">Hello from plugin</div>"

        val result = ctx.renderShell(shell, bodyHtml)

        assertTrue(result.contains("<!DOCTYPE html>"), "Should contain DOCTYPE html")
        assertTrue(
            result.contains("<div id=\"plugin-content\">Hello from plugin</div>"),
            "Should contain plugin body HTML",
        )
        assertTrue(result.contains("Outerstellar"), "Should contain app title from shell")
        assertTrue(result.contains("Test Page"), "Should contain page title from shell")
        assertTrue(result.contains("</html>"), "Should end with closing html tag")
    }

    @Test
    fun `renderShell respects sidebar layout style`() {
        val ctx = createContext()
        val shell = createShell().copy(layoutStyle = "sidebar")

        val result = ctx.renderShell(shell, "<p>content</p>")

        assertTrue(result.contains("drawer"), "Sidebar layout should have drawer class")
        assertTrue(result.contains("drawer-content"), "Sidebar layout should have drawer-content")
    }

    @Test
    fun `renderShell respects topbar layout style`() {
        val ctx = createContext()
        val shell = createShell().copy(layoutStyle = "topbar")

        val result = ctx.renderShell(shell, "<p>content</p>")

        assertTrue(result.contains("min-h-screen"), "Topbar layout should have min-h-screen class")
        assertTrue(!result.contains("drawer"), "Topbar layout should not have drawer class")
    }
}
