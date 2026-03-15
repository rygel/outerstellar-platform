package dev.outerstellar.starter.swing

import com.sksamuel.hoplite.ConfigLoaderBuilder
import com.sksamuel.hoplite.ExperimentalHoplite
import com.sksamuel.hoplite.addEnvironmentSource
import com.sksamuel.hoplite.addMapSource

data class SwingAppConfig(
    val serverBaseUrl: String = "http://localhost:8080",
    val jdbcUrl: String =
        "jdbc:h2:file:./data/outerstellar-swing-client;MODE=PostgreSQL;AUTO_SERVER=TRUE",
    val jdbcUser: String = "sa",
    val jdbcPassword: String = "",
    val version: String = "1.0.0",
    val updateUrl: String = "",
    val devMode: Boolean = false,
    val segmentWriteKey: String = "",
    val analyticsEnabled: Boolean = false,
    val analyticsFlushIntervalHours: Long = 24,
    val analyticsMaxFileSizeKb: Long = 2048,
    val analyticsMaxEventAgeDays: Long = 30,
) {
    companion object {
        @OptIn(ExperimentalHoplite::class)
        fun fromEnvironment(environment: Map<String, String> = System.getenv()): SwingAppConfig =
            ConfigLoaderBuilder.default()
                .withExplicitSealedTypes()
                .addMapSource(environment)
                .addEnvironmentSource()
                .build()
                .loadConfigOrThrow<SwingAppConfig>()
    }
}
