package io.github.rygel.outerstellar.platform.web

import io.github.rygel.outerstellar.platform.extension.ExtensionHostContext
import io.github.rygel.outerstellar.platform.persistence.UserRepository
import io.github.rygel.outerstellar.platform.security.ApiKeyService
import io.github.rygel.outerstellar.platform.security.OAuthService
import io.mockk.mockk
import kotlin.test.assertTrue
import org.http4k.template.TemplateRenderer
import org.junit.jupiter.api.Test

class ExtensionRenderShellTest {

    private fun createContext(): ExtensionHostContext {
        val renderer: TemplateRenderer = { "" }
        return hostedAppContextForTesting(
            renderer = renderer,
            apiKeyService = mockk<ApiKeyService>(relaxed = true),
            oauthService = mockk<OAuthService>(relaxed = true),
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
        val bodyHtml = "<div id=\"extension-content\">Hello from extension</div>"

        val result = ctx.renderShell(shell, bodyHtml)

        assertTrue(result.contains("<!DOCTYPE html>"), "Should contain DOCTYPE html")
        assertTrue(
            result.contains("<div id=\"extension-content\">Hello from extension</div>"),
            "Should contain extension body HTML",
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

    @Test
    fun `renderShell delegates to extension layout renderer when provided`() {
        val ctx = createContext()
        val renderer = ExtensionLayoutRenderer { shell, content ->
            gg.jte.Content { output ->
                output.writeUnsafeContent("<extension-layout data-title=\"${shell.pageTitle}\">")
                content.writeTo(output)
                output.writeUnsafeContent("</extension-layout>")
            }
        }
        val shell = createShell().copy(extensionLayoutRenderer = renderer)

        val result = ctx.renderShell(shell, "<main>extension body</main>")

        assertTrue(result.contains("<extension-layout data-title=\"Test Page\">"))
        assertTrue(result.contains("<main>extension body</main>"))
        assertTrue(!result.contains("drawer"), "Platform sidebar layout should be bypassed")
    }

    @Test
    fun `renderShell includes extension stylesheets and scripts`() {
        val ctx = createContext()
        val shell =
            createShell()
                .copy(
                    extensionStylesheets = listOf("/extensions/reports/assets/reports.css"),
                    extensionScripts = listOf("/extensions/reports/assets/reports.js"),
                )

        val result = ctx.renderShell(shell, "<p>content</p>")

        assertTrue(result.contains("""<link rel="stylesheet" href="/extensions/reports/assets/reports.css">"""))
        assertTrue(result.contains("""<script defer src="/extensions/reports/assets/reports.js"></script>"""))
    }
}
