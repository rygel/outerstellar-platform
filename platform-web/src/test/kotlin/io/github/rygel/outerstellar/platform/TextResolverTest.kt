package io.github.rygel.outerstellar.platform

import java.io.File
import kotlin.test.assertEquals
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
}
