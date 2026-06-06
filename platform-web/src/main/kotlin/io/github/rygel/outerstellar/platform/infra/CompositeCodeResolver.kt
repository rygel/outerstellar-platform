package io.github.rygel.outerstellar.platform.infra

import gg.jte.CodeResolver

/**
 * Composite resolver that lets downstream projects resolve their own templates from disk while picking up shared
 * platform templates from the classpath JAR.
 */
class CompositeCodeResolver(private val primary: CodeResolver, private val secondary: CodeResolver) : CodeResolver {
    override fun resolve(name: String): String =
        if (primary.exists(name)) primary.resolve(name) else secondary.resolve(name)

    override fun exists(name: String): Boolean = primary.exists(name) || secondary.exists(name)

    override fun getLastModified(name: String): Long =
        if (primary.exists(name)) primary.getLastModified(name) else secondary.getLastModified(name)

    override fun resolveAllTemplateNames(): MutableList<String> {
        val names = LinkedHashSet(primary.resolveAllTemplateNames())
        names.addAll(secondary.resolveAllTemplateNames())
        return names.toMutableList()
    }
}
