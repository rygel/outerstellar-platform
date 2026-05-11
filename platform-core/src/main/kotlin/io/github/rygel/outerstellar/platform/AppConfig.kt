package io.github.rygel.outerstellar.platform

import org.koin.dsl.module
import org.slf4j.LoggerFactory
import org.snakeyaml.engine.v2.api.Load
import org.snakeyaml.engine.v2.api.LoadSettings

private const val DEFAULT_HTTP_PORT = 8080
private const val DEFAULT_SMTP_PORT = 587
private const val DEFAULT_SESSION_TIMEOUT_MINUTES = 30
private const val MIN_SESSION_TIMEOUT_MINUTES = 1
private const val DEFAULT_JWT_EXPIRY_SECONDS = 86400L
private const val MAX_HTTP_PORT = 65535
private const val MIN_HTTP_PORT = 1
private const val DEFAULT_MAX_FAILED_LOGIN_ATTEMPTS = 10
private const val DEFAULT_LOCKOUT_DURATION_SECONDS = 900L
private const val DEFAULT_CSP_POLICY =
    "default-src 'self'; script-src 'self' 'unsafe-inline'; " +
        "style-src 'self' 'unsafe-inline'; font-src 'self'; connect-src 'self' ws: wss:; img-src 'self' data:;"

val configModule
    get() = module { single { AppConfig.fromEnvironment() } }

data class SegmentConfig(val writeKey: String = "", val enabled: Boolean = false)

data class JwtConfig(
    val enabled: Boolean = false,
    val secret: String = "",
    val issuer: String = "outerstellar",
    val expirySeconds: Long = DEFAULT_JWT_EXPIRY_SECONDS,
)

data class EmailConfig(
    val enabled: Boolean = false,
    val host: String = "localhost",
    val port: Int = DEFAULT_SMTP_PORT,
    val username: String = "",
    val password: String = "",
    val from: String = "noreply@example.com",
    val startTls: Boolean = true,
)

data class AppConfig(
    val version: String = "dev",
    val port: Int = DEFAULT_HTTP_PORT,
    val jdbcUrl: String = "jdbc:postgresql://localhost:5432/outerstellar",
    val jdbcUser: String = "outerstellar",
    val jdbcPassword: String = "outerstellar",
    val devDashboardEnabled: Boolean = false,
    val devMode: Boolean = false,
    val sessionCookieSecure: Boolean = true,
    val sessionTimeoutMinutes: Int = DEFAULT_SESSION_TIMEOUT_MINUTES,
    val corsOrigins: String = "",
    val csrfEnabled: Boolean = true,
    val segment: SegmentConfig = SegmentConfig(),
    val email: EmailConfig = EmailConfig(),
    val appBaseUrl: String = "http://localhost:8080",
    val maxFailedLoginAttempts: Int = DEFAULT_MAX_FAILED_LOGIN_ATTEMPTS,
    val lockoutDurationSeconds: Long = DEFAULT_LOCKOUT_DURATION_SECONDS,
    val jwt: JwtConfig = JwtConfig(),
    val cspPolicy: String = DEFAULT_CSP_POLICY,
) {
    companion object {
        private val logger = LoggerFactory.getLogger(AppConfig::class.java)

        fun fromEnvironment(environment: Map<String, String> = System.getenv()): AppConfig {
            val profile = environment["APP_PROFILE"] ?: "default"
            val yamlData = loadYaml(profile)
            return buildFromYaml(yamlData, environment)
        }

        private fun loadYaml(profile: String): Map<String, Any>? {
            val loader = Load(LoadSettings.builder().build())
            if (profile != "default") {
                val result = readResource(loader, "/application-$profile.yaml")
                if (result != null) return result
            }
            return readResource(loader, "/application.yaml")
        }

        private fun readResource(loader: Load, path: String): Map<String, Any>? {
            val stream = AppConfig::class.java.getResourceAsStream(path) ?: return null
            return try {
                loader.loadFromInputStream(stream) as? Map<String, Any>
            } finally {
                stream.close()
            }
        }

        private fun buildFromYaml(yaml: Map<String, Any>?, env: Map<String, String>): AppConfig {
            if (yaml == null) return AppConfig()
            val port = yaml.int("port", env, "PORT", DEFAULT_HTTP_PORT).coerceIn(MIN_HTTP_PORT, MAX_HTTP_PORT)
            val timeout =
                yaml
                    .int("sessionTimeoutMinutes", env, "SESSIONTIMEOUTMINUTES", DEFAULT_SESSION_TIMEOUT_MINUTES)
                    .coerceAtLeast(MIN_SESSION_TIMEOUT_MINUTES)
            val jdbcUrl = yaml.str("jdbcUrl", env, "JDBC_URL", "jdbc:postgresql://localhost:5432/outerstellar")
            if (jdbcUrl.isBlank()) {
                logger.warn("JDBC_URL is blank — database connection will fail at runtime")
            }
            return AppConfig(
                version = yaml.str("version", env, "VERSION", "dev"),
                port = port,
                jdbcUrl = jdbcUrl,
                jdbcUser = yaml.str("jdbcUser", env, "JDBC_USER", "outerstellar"),
                jdbcPassword = yaml.str("jdbcPassword", env, "JDBC_PASSWORD", "outerstellar"),
                devDashboardEnabled = yaml.bool("devDashboardEnabled", env, "DEV_DASHBOARD_ENABLED", false),
                devMode = yaml.bool("devMode", env, "DEVMODE", false),
                sessionCookieSecure = yaml.bool("sessionCookieSecure", env, "SESSIONCOOKIESECURE", true),
                sessionTimeoutMinutes = timeout,
                corsOrigins = yaml.str("corsOrigins", env, "CORSORIGINS", "*"),
                csrfEnabled = yaml.bool("csrfEnabled", env, "CSRFENABLED", true),
                segment = buildSegmentConfig(yaml["segment"] as? Map<String, Any>, env),
                email = buildEmailConfig(yaml["email"] as? Map<String, Any>, env),
                appBaseUrl = yaml.str("appBaseUrl", env, "APPBASEURL", "http://localhost:8080"),
                maxFailedLoginAttempts =
                    yaml.int(
                        "maxFailedLoginAttempts",
                        env,
                        "MAX_FAILED_LOGIN_ATTEMPTS",
                        DEFAULT_MAX_FAILED_LOGIN_ATTEMPTS,
                    ),
                lockoutDurationSeconds =
                    yaml.long(
                        "lockoutDurationSeconds",
                        env,
                        "LOCKOUT_DURATION_SECONDS",
                        DEFAULT_LOCKOUT_DURATION_SECONDS,
                    ),
                jwt = buildJwtConfig(yaml["jwt"] as? Map<String, Any>, env),
                cspPolicy = (yaml["cspPolicy"] as? String) ?: DEFAULT_CSP_POLICY,
            )
        }

        private fun buildSegmentConfig(yaml: Map<String, Any>?, env: Map<String, String>): SegmentConfig {
            if (yaml == null) return SegmentConfig()
            return SegmentConfig(
                writeKey = yaml.str("writeKey", env, "SEGMENT_WRITEKEY", ""),
                enabled = yaml.bool("enabled", env, "SEGMENT_ENABLED", false),
            )
        }

        private fun buildEmailConfig(yaml: Map<String, Any>?, env: Map<String, String>): EmailConfig {
            if (yaml == null) return EmailConfig()
            return EmailConfig(
                enabled = yaml.bool("enabled", env, "EMAIL_ENABLED", false),
                host = yaml.str("host", env, "EMAIL_HOST", "localhost"),
                port = yaml.int("port", env, "EMAIL_PORT", DEFAULT_SMTP_PORT),
                username = yaml.str("username", env, "EMAIL_USERNAME", ""),
                password = yaml.str("password", env, "EMAIL_PASSWORD", ""),
                from = yaml.str("from", env, "EMAIL_FROM", "noreply@example.com"),
                startTls = yaml.bool("startTls", env, "EMAIL_STARTTLS", true),
            )
        }

        private fun buildJwtConfig(yaml: Map<String, Any>?, env: Map<String, String>): JwtConfig {
            if (yaml == null) return JwtConfig()
            return JwtConfig(
                enabled = yaml.bool("enabled", env, "JWT_ENABLED", false),
                secret = yaml.str("secret", env, "JWT_SECRET", ""),
                issuer = yaml.str("issuer", env, "JWT_ISSUER", "outerstellar"),
                expirySeconds = yaml.long("expirySeconds", env, "JWT_EXPIRYSECONDS", DEFAULT_JWT_EXPIRY_SECONDS),
            )
        }
    }
}

private fun Map<String, Any>.str(key: String, env: Map<String, String>, envKey: String, default: String): String =
    env[envKey] ?: (this[key] as? String) ?: default

private fun Map<String, Any>.int(key: String, env: Map<String, String>, envKey: String, default: Int): Int =
    env[envKey]?.toInt() ?: (this[key] as? Int) ?: default

private fun Map<String, Any>.bool(key: String, env: Map<String, String>, envKey: String, default: Boolean): Boolean =
    env[envKey]?.toBoolean() ?: (this[key] as? Boolean) ?: default

private fun Map<String, Any>.long(key: String, env: Map<String, String>, envKey: String, default: Long): Long =
    env[envKey]?.toLong() ?: (this[key] as? Long) ?: default
