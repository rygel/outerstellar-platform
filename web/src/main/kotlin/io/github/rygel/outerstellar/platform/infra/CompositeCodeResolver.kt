package io.github.rygel.outerstellar.platform.infra

import gg.jte.CodeResolver

/**
 * Composite resolver that tries [primary] first, falling back to [fallback]. This lets downstream
 * projects resolve their own templates from disk while picking up shared platform templates from
 * the classpath JAR.
 */
class CompositeCodeResolver(private val primary: CodeResolver, private val fallback: CodeResolver) :
    CodeResolver {
    override fun resolve(name: String): String =
        if (primary.exists(name)) primary.resolve(name) else fallback.resolve(name)

    override fun exists(name: String): Boolean = primary.exists(name) || fallback.exists(name)

    override fun getLastModified(name: String): Long =
        if (primary.exists(name)) primary.getLastModified(name) else fallback.getLastModified(name)

    override fun resolveAllTemplateNames(): MutableList<String> {
        val names = LinkedHashSet(primary.resolveAllTemplateNames())
        names.addAll(fallback.resolveAllTemplateNames())
        return names.toMutableList()
    }
}
