package dev.outerstellar.starter

import com.sksamuel.hoplite.ConfigLoaderBuilder
import com.sksamuel.hoplite.ExperimentalHoplite
import com.sksamuel.hoplite.addEnvironmentSource
import com.sksamuel.hoplite.addResourceSource

data class AppConfig(
    val port: Int = 8080,
    val jdbcUrl: String =
        "jdbc:h2:file:./data/outerstellar-starter;MODE=PostgreSQL;AUTO_SERVER=TRUE",
    val jdbcUser: String = "sa",
    val jdbcPassword: String = "",
    val devDashboardEnabled: Boolean = false,
    val devMode: Boolean = false,
    // Defaults to false for local development (HTTP). Set to true in production via
    // APP_PROFILE=prod or the SESSIONCOOKIESECURE environment variable.
    val sessionCookieSecure: Boolean = false,
    val sessionTimeoutMinutes: Int = 30,
) {
    companion object {
        @OptIn(ExperimentalHoplite::class)
        fun fromEnvironment(environment: Map<String, String> = System.getenv()): AppConfig {
            val profile = environment["APP_PROFILE"] ?: "default"
            val builder =
                ConfigLoaderBuilder.default()
                    .withExplicitSealedTypes()
                    .addEnvironmentSource()
                    .addResourceSource("/application.yaml", optional = true)

            if (profile != "default") {
                builder.addResourceSource("/application-$profile.yaml", optional = true)
            }

            return builder.build().loadConfigOrThrow<AppConfig>()
        }
    }
}
