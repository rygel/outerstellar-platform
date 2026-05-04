package io.github.rygel.outerstellar.platform

import com.sksamuel.hoplite.ConfigLoaderBuilder
import com.sksamuel.hoplite.ExperimentalHoplite
import com.sksamuel.hoplite.addEnvironmentSource
import com.sksamuel.hoplite.addResourceSource
import com.sksamuel.hoplite.sources.SystemPropertiesPropertySource
import org.koin.dsl.module

val configModule
    get() = module { single { AppConfig.fromEnvironment() } }

data class SegmentConfig(val writeKey: String = "", val enabled: Boolean = false)

/**
 * JWT authentication configuration. Disabled by default. Enable for apps that need stateless token auth (e.g.
 * device/API clients). Set [enabled] = true and provide a strong random [secret] to activate.
 */
data class JwtConfig(
    val enabled: Boolean = false,
    val secret: String = "",
    val issuer: String = "outerstellar",
    val expirySeconds: Long = 86400L,
)

/**
 * SMTP email configuration. Email sending is **disabled by default**. Set [enabled] = true and provide
 * [host]/[username]/[password] to activate.
 */
data class EmailConfig(
    val enabled: Boolean = false,
    val host: String = "localhost",
    val port: Int = 587,
    val username: String = "",
    val password: String = "",
    val from: String = "noreply@example.com",
    val startTls: Boolean = true,
)

data class AppConfig(
    val version: String = "dev",
    val port: Int = 8080,
    val jdbcUrl: String = "jdbc:postgresql://localhost:5432/outerstellar",
    val jdbcUser: String = "outerstellar",
    val jdbcPassword: String = "outerstellar",
    val devDashboardEnabled: Boolean = false,
    val devMode: Boolean = false,
    // Defaults to false for local development (HTTP). Set to true in production via
    // APP_PROFILE=prod or the SESSIONCOOKIESECURE environment variable.
    val sessionCookieSecure: Boolean = false,
    val sessionTimeoutMinutes: Int = 30,
    // WARNING: "*" allows any origin. Override with a comma-separated allow-list in production
    // (e.g. CORSORIGINS=https://app.example.com) to prevent cross-origin credential theft.
    val corsOrigins: String = "*",
    val csrfEnabled: Boolean = true,
    val segment: SegmentConfig = SegmentConfig(),
    val email: EmailConfig = EmailConfig(),
    /** Public-facing base URL used in emails, e.g. https://app.example.com */
    val appBaseUrl: String = "http://localhost:8080",
    val jwt: JwtConfig = JwtConfig(),
    val cspPolicy: String =
        "default-src 'self'; " +
            "script-src 'self' 'unsafe-inline'; " +
            "style-src 'self' 'unsafe-inline'; " +
            "font-src 'self'; " +
            "connect-src 'self' ws: wss:; " +
            "img-src 'self' data:;",
) {
    companion object {
        @OptIn(ExperimentalHoplite::class)
        fun fromEnvironment(environment: Map<String, String> = System.getenv()): AppConfig {
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

            return builder.build().loadConfigOrThrow<AppConfig>()
        }
    }
}
