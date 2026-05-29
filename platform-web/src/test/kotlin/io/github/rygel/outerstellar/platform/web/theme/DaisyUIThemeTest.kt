package io.github.rygel.outerstellar.platform.web.theme

import org.http4k.core.Method
import org.http4k.core.Request
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DaisyUIThemeTest {
    private val theme = DaisyUITheme()
    private val request = Request(Method.GET, "/")

    @Test
    fun `default theme has id daisyui-default`() {
        assertEquals("daisyui-default", theme.id)
    }

    @Test
    fun `default theme has no template overrides`() {
        assertTrue(theme.templateOverrides().isEmpty())
    }

    @Test
    fun `default theme has no head injections`() {
        assertTrue(theme.headInjections(request).isEmpty())
    }

    @Test
    fun `default theme has no body injections`() {
        assertTrue(theme.bodyInjections(request).isEmpty())
    }
}
