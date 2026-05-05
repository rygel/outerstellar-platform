package io.github.rygel.outerstellar.platform.infra

import org.http4k.template.TemplateRenderer
import org.http4k.template.ViewModel

class PluginTemplateRenderer(
    private val delegate: TemplateRenderer,
    private val overrideTemplates: Set<String>,
    private val pluginClassLoader: ClassLoader?,
) : TemplateRenderer {

    override fun invoke(viewModel: ViewModel): String {
        val templateName = "${viewModel.template()}.kte"
        if (overrideTemplates.contains(templateName) && pluginClassLoader != null) {
            return delegate(viewModel)
        }
        return delegate(viewModel)
    }
}
