#set( $symbol_pound = '#' )
#set( $symbol_dollar = '$' )
#set( $symbol_escape = '\' )
package ${package}.host

import io.github.rygel.outerstellar.platform.extension.PrecompiledJteTemplateRegistry
import java.nio.file.Paths
import java.util.ServiceLoader

fun main(args: Array<String>) {
    val fatJar = args.getOrElse(0) { throw IllegalArgumentException("Usage: NativeRegistryVerify <fat-jar-path>") }
    val jarFile = Paths.get(fatJar).toAbsolutePath().normalize().toFile()
    require(jarFile.exists()) { "Fat JAR not found: ${symbol_dollar}jarFile" }

    println("[native-verify] Checking precompiled template registries in ${symbol_dollar}jarFile")

    val classLoader = Thread.currentThread().contextClassLoader
    val registries = ServiceLoader.load(PrecompiledJteTemplateRegistry::class.java, classLoader).toList()

    println("[native-verify] Found ${symbol_dollar}{registries.size} registries via ServiceLoader")
    for (registry in registries) {
        println("[native-verify]   - ${symbol_dollar}{registry::class.java.name} (${symbol_dollar}{registry.allClasses.size} template classes)")
    }

    check(registries.size >= 2) {
        "Native plugin template registry missing. Expected at least 2 registries (platform + plugin), found ${symbol_dollar}{registries.size}.\n" +
            "Build the native image from the composed app module that depends on the selected plugin."
    }

    val extensionTemplate = registries.firstNotNullOfOrNull { registry ->
        registry.getTemplateClass("IndexPage.kte")
    }
    check(extensionTemplate != null) {
        "Extension template 'IndexPage' not found in ${symbol_dollar}{registries.size} registries.\n" +
            "Ensure the extension module runs jte-maven-plugin:generate and includes the service file."
    }
    println("[native-verify] Extension template resolved: ${symbol_dollar}{extensionTemplate.name}")

    val platformTemplate = registries.firstNotNullOfOrNull { registry ->
        registry.getTemplateClass("io/github/rygel/outerstellar/platform/web/HomePage.kte")
    }
    check(platformTemplate != null) {
        "Platform template 'HomePage' not found in ${symbol_dollar}{registries.size} registries."
    }
    println("[native-verify] Platform template resolved: ${symbol_dollar}{platformTemplate.name}")

    println("[native-verify] All registry checks passed. Safe to build native image.")
}
