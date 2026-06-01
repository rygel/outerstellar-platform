package io.github.rygel.outerstellar.platform.infra

import org.http4k.template.TemplateRenderer
import org.http4k.template.ViewModel

class ExtensionTemplateRenderer(
    private val delegate: TemplateRenderer,
    private val overrideTemplates: Set<String>,
    private val extensionClassLoader: ClassLoader?,
) : TemplateRenderer {

    override fun invoke(viewModel: ViewModel): String {
        val templateName = "${viewModel.template()}.kte"
        if (overrideTemplates.contains(templateName) && extensionClassLoader != null) {
            return delegate(viewModel)
        }
        return delegate(viewModel)
    }
}
