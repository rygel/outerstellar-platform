package io.github.rygel.outerstellar.platform.swing

import com.sksamuel.hoplite.ConfigLoaderBuilder
import com.sksamuel.hoplite.ExperimentalHoplite
import com.sksamuel.hoplite.addEnvironmentSource
import com.sksamuel.hoplite.addResourceSource
import com.sksamuel.hoplite.sources.SystemPropertiesPropertySource

data class SwingAppConfig(
    val serverBaseUrl: String = "http://localhost:8080",
    val jdbcUrl: String = "jdbc:postgresql://localhost:5432/outerstellar",
    val jdbcUser: String = "outerstellar",
    val jdbcPassword: String = "outerstellar",
    val version: String = "1.0.0",
    val updateUrl: String = "",
    val devMode: Boolean = false,
    /** Credentials used for dev auto-login. Only active when devMode=true. Never hardcode. */
    val devUsername: String = "",
    val devPassword: String = "",
    val segmentWriteKey: String = "",
    val analyticsEnabled: Boolean = false,
    val analyticsFlushIntervalHours: Long = 24,
    val analyticsMaxFileSizeKb: Long = 2048,
    val analyticsMaxEventAgeDays: Long = 30,
) {
    companion object {
        @OptIn(ExperimentalHoplite::class)
        fun fromEnvironment(environment: Map<String, String> = System.getenv()): SwingAppConfig {
            val profile = environment["APP_PROFILE"] ?: "default"
            val builder =
                ConfigLoaderBuilder.default()
                    .withExplicitSealedTypes()
                    .addEnvironmentSource()
                    .addSource(SystemPropertiesPropertySource())

            if (profile != "default") {
                builder.addResourceSource("/application-$profile.yaml", optional = true)
            }

            builder.addResourceSource("/application.yaml", optional = true)

            return builder.build().loadConfigOrThrow<SwingAppConfig>()
        }
    }
}
