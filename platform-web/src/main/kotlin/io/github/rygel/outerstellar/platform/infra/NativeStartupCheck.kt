package io.github.rygel.outerstellar.platform.infra

import org.slf4j.LoggerFactory

object NativeStartupCheck {
    private val logger = LoggerFactory.getLogger(NativeStartupCheck::class.java)

    private val IS_NATIVE = System.getProperty("org.graalvm.nativeimage.imagekind") != null

    private data class CheckResult(val name: String, val passed: Boolean, val detail: String = "")

    fun run() {
        if (!IS_NATIVE) {
            logger.info("Native startup check skipped (not running in native-image mode)")
            return
        }

        logger.info("Running native-image startup diagnostics...")
        val results = mutableListOf<CheckResult>()

        results += checkResource("db/migration/migrations.index", "Migration manifest")
        results += checkResource("application.yaml", "Application config")
        results += checkResource("logback.xml", "Logging config")
        results += checkResource("messages.properties", "i18n English bundle")
        results += checkResource("messages_fr.properties", "i18n French bundle")

        results +=
            checkClassLoadable("gg.jte.generated.precompiled.outerstellar.JteHomeGenerated", "JTE template registry")

        results += checkMigrationIndexNonEmpty()

        val failures = results.filter { !it.passed }
        if (failures.isEmpty()) {
            logger.info("Native startup check passed ({} checks)", results.size)
        } else {
            failures.forEach { logger.error("NATIVE CHECK FAILED: {} - {}", it.name, it.detail) }
            throw IllegalStateException(
                "Native startup check failed (${failures.size}/${results.size} checks). " +
                    "See errors above. This usually means reachability-metadata.json is missing entries. " +
                    "Run: scripts/generate-reachability-resources.ps1"
            )
        }
    }

    private fun checkResource(path: String, name: String): CheckResult {
        val stream = Thread.currentThread().contextClassLoader.getResourceAsStream(path)
        val passed = stream != null
        stream?.close()
        return if (passed) {
            logger.debug("  [OK] {} - found {}", name, path)
            CheckResult(name, true)
        } else {
            CheckResult(name, false, "$path not found on classpath - add to reachability-metadata.json resources")
        }
    }

    private fun checkClassLoadable(className: String, name: String): CheckResult {
        return try {
            Class.forName(className)
            logger.debug("  [OK] {} - class loaded", name)
            CheckResult(name, true)
        } catch (e: ClassNotFoundException) {
            CheckResult(name, false, "$className not found - check JteClassRegistry and reflection metadata")
        }
    }

    private fun checkMigrationIndexNonEmpty(): CheckResult {
        val name = "Migration index content"
        val stream =
            Thread.currentThread().contextClassLoader.getResourceAsStream("db/migration/migrations.index")
                ?: return CheckResult(name, false, "migrations.index not found on classpath")
        val content = stream.bufferedReader().readText().trim()
        stream.close()
        return if (content.isNotBlank()) {
            val count = content.lines().filter { it.isNotBlank() }.size
            logger.debug("  [OK] {} - {} migrations listed", name, count)
            CheckResult(name, true)
        } else {
            CheckResult(name, false, "migrations.index is empty - migrations will not run")
        }
    }
}
