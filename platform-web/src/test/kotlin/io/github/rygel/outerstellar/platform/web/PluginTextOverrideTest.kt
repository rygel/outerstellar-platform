package io.github.rygel.outerstellar.platform.web

import io.github.rygel.outerstellar.i18n.I18nService
import io.github.rygel.outerstellar.platform.DefaultTextResolver
import io.github.rygel.outerstellar.platform.I18nTextResolver
import io.github.rygel.outerstellar.platform.TextResolver
import java.util.Locale
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

class PluginTextOverrideTest {

    @Test
    fun `I18nTextResolver resolves known keys from messages properties`() {
        val i18n = I18nService.create("messages")
        i18n.setLocale(Locale.ENGLISH)
        val resolver = I18nTextResolver(i18n)
        assertEquals("Home", resolver.resolve("web.nav.home"))
        assertEquals("Contacts", resolver.resolve("web.nav.contacts"))
        assertEquals("Trash", resolver.resolve("web.nav.trash"))
    }

    @Test
    fun `I18nTextResolver returns key for missing entries`() {
        val i18n = I18nService.create("messages")
        i18n.setLocale(Locale.ENGLISH)
        val resolver = I18nTextResolver(i18n)
        assertEquals("nonexistent.key.12345", resolver.resolve("nonexistent.key.12345"))
    }

    @Test
    fun `I18nTextResolver formats args into resolved template`() {
        val i18n = I18nService.create("messages")
        i18n.setLocale(Locale.ENGLISH)
        val resolver = I18nTextResolver(i18n)
        val result = resolver.resolve("web.footer.version", "2.0")
        assertTrue(result.contains("2.0"), "Expected version 2.0 in: $result")
    }

    @Test
    fun `DefaultTextResolver with custom map overrides specific keys`() {
        val customResolver: TextResolver =
            DefaultTextResolver(mapOf("web.nav.home" to "My Home", "web.nav.contacts" to "My Contacts"))
        assertEquals("My Home", customResolver.resolve("web.nav.home"))
        assertEquals("My Contacts", customResolver.resolve("web.nav.contacts"))
        assertEquals("web.nav.trash", customResolver.resolve("web.nav.trash"))
    }

    @Test
    fun `PluginOptions carries text resolver override`() {
        val customResolver = DefaultTextResolver(mapOf("key" to "value"))
        val options = PluginOptions(textResolver = customResolver)
        assertNotNull(options.textResolver)
        assertEquals("value", options.textResolver!!.resolve("key"))
    }

    @Test
    fun `ShellView text method delegates to textResolver`() {
        val resolver = DefaultTextResolver(mapOf("test.key" to "Hello World"))
        val shell =
            ShellView(
                pageTitle = "Test",
                appTitle = "App",
                appTagline = "",
                currentPath = "/",
                localeTag = "en",
                themeName = "dark",
                layoutClass = "",
                navLinks = emptyList(),
                themeSelector = SidebarSelector("", "", "", "", emptyList(), emptyList(), ""),
                languageSelector = SidebarSelector("", "", "", "", emptyList(), emptyList(), ""),
                layoutSelector = SidebarSelector("", "", "", "", emptyList(), emptyList(), ""),
                footerCopy = "",
                footerVersion = "",
                footerStatusUrl = "",
                version = "1",
                textResolver = resolver,
            )
        assertEquals("Hello World", shell.text("test.key"))
        assertEquals("missing.key", shell.text("missing.key"))
    }
}
