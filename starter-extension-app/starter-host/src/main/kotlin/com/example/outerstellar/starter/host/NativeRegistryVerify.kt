package com.example.outerstellar.starter.host

import io.github.rygel.outerstellar.platform.extension.PrecompiledJteTemplateRegistry
import java.nio.file.Paths
import java.util.ServiceLoader

fun main(args: Array<String>) {
    val fatJar = args.getOrElse(0) { throw IllegalArgumentException("Usage: NativeRegistryVerify <fat-jar-path>") }
    val jarFile = Paths.get(fatJar).toAbsolutePath().normalize().toFile()
    require(jarFile.exists()) { "Fat JAR not found: $jarFile" }

    println("[native-verify] Checking precompiled template registries in $jarFile")

    val classLoader = Thread.currentThread().contextClassLoader
    val registries = ServiceLoader.load(PrecompiledJteTemplateRegistry::class.java, classLoader).toList()

    println("[native-verify] Found ${registries.size} registries via ServiceLoader")
    for (registry in registries) {
        println("[native-verify]   - ${registry::class.java.name} (${registry.allClasses.size} template classes)")
    }

    check(registries.size >= 2) {
        "Native plugin template registry missing. Expected at least 2 registries (platform + plugin), found ${registries.size}.\n" +
            "Build the native image from the composed app module that depends on the selected plugin."
    }

    val starterTemplate = registries.firstNotNullOfOrNull { registry ->
        registry.getTemplateClass("com/example/outerstellar/starter/extension/StarterIndexPage.kte")
    }
    check(starterTemplate != null) {
        "Starter extension template 'StarterIndexPage' not found in ${registries.size} registries.\n" +
            "Ensure the starter-extension module runs jte-maven-plugin:generate and includes the service file."
    }
    println("[native-verify] Extension template resolved: ${starterTemplate.name}")

    val platformTemplate = registries.firstNotNullOfOrNull { registry ->
        registry.getTemplateClass("io/github/rygel/outerstellar/platform/web/HomePage.kte")
    }
    check(platformTemplate != null) {
        "Platform template 'HomePage' not found in ${registries.size} registries."
    }
    println("[native-verify] Platform template resolved: ${platformTemplate.name}")

    println("[native-verify] All registry checks passed. Safe to build native image.")
}
