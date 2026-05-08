package io.github.rygel.outerstellar.platform

import io.github.rygel.outerstellar.i18n.I18nService
import java.io.File
import java.util.Locale
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

class TextResolverTest {

    @Test
    fun `resolve returns value for existing key`() {
        val resolver = DefaultTextResolver(mapOf("nav.inbox" to "Inbox"))
        assertEquals("Inbox", resolver.resolve("nav.inbox"))
    }

    @Test
    fun `resolve returns key itself for missing key`() {
        val resolver = DefaultTextResolver(emptyMap())
        assertEquals("missing.key", resolver.resolve("missing.key"))
    }

    @Test
    fun `resolve formats args into template`() {
        val resolver = DefaultTextResolver(mapOf("greeting" to "Hello, %s!"))
        assertEquals("Hello, World!", resolver.resolve("greeting", "World"))
    }

    @Test
    fun `resolve returns plain value when no args`() {
        val resolver = DefaultTextResolver(mapOf("simple" to "Just text"))
        assertEquals("Just text", resolver.resolve("simple"))
    }

    @Test
    fun `loads from properties file`() {
        val props = listOf("nav.inbox=Inbox", "nav.contacts=Contacts").joinToString("\n")
        val file = File.createTempFile("test-texts-", ".properties")
        file.writeText(props)
        val resolver = DefaultTextResolver.fromFile(file.absolutePath)
        assertEquals("Inbox", resolver.resolve("nav.inbox"))
        assertEquals("Contacts", resolver.resolve("nav.contacts"))
        file.delete()
    }

    @Test
    fun `I18nTextResolver delegates to I18nService`() {
        val i18n = I18nService.create("messages")
        i18n.setLocale(Locale.ENGLISH)
        val resolver = I18nTextResolver(i18n)
        val resolved = resolver.resolve("web.nav.home")
        assertEquals("Home", resolved)
    }

    @Test
    fun `I18nTextResolver returns key for missing entries`() {
        val i18n = I18nService.create("messages")
        i18n.setLocale(Locale.ENGLISH)
        val resolver = I18nTextResolver(i18n)
        assertEquals("nonexistent.key", resolver.resolve("nonexistent.key"))
    }

    @Test
    fun `I18nTextResolver formats args`() {
        val i18n = I18nService.create("messages")
        i18n.setLocale(Locale.ENGLISH)
        val resolver = I18nTextResolver(i18n)
        val result = resolver.resolve("web.footer.version", "1.0")
        assertTrue(result.contains("1.0"), "Expected formatted version in: $result")
    }

    @Test
    fun `I18nTextResolver filters null args`() {
        val i18n = I18nService.create("messages")
        i18n.setLocale(Locale.ENGLISH)
        val resolver = I18nTextResolver(i18n)
        val result = resolver.resolve("web.nav.home", null)
        assertEquals("Home", result)
    }
}
