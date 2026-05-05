package io.github.rygel.outerstellar.platform.infra

import kotlin.test.assertEquals
import org.http4k.template.ViewModel
import org.junit.jupiter.api.Test

class PluginTemplateRendererTest {

    private data class TestViewModel(val value: String) : ViewModel {
        override fun template() = "TestTemplate"
    }

    @Test
    fun `delegates to base renderer when template is not overridden`() {
        val baseRenderer: org.http4k.template.TemplateRenderer = { "<base>${it.template()}</base>" }
        val pluginRenderer = PluginTemplateRenderer(baseRenderer, emptySet(), null)
        val result = pluginRenderer(TestViewModel("test"))
        assertEquals("<base>TestTemplate</base>", result)
    }

    @Test
    fun `override set is checked but base renderer handles all rendering`() {
        val baseRenderer: org.http4k.template.TemplateRenderer = { "<base>${it.template()}</base>" }
        val pluginRenderer = PluginTemplateRenderer(baseRenderer, setOf("SomeTemplate"), null)
        val result = pluginRenderer(TestViewModel("test"))
        assertEquals("<base>TestTemplate</base>", result)
    }
}
