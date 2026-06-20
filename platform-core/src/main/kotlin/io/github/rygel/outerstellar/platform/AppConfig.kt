package io.github.rygel.outerstellar.platform

import io.github.rygel.outerstellar.platform.composition.PlatformMode
import org.slf4j.LoggerFactory
import org.snakeyaml.engine.v2.api.Load
import org.snakeyaml.engine.v2.api.LoadSettings

private const val DEFAULT_HTTP_PORT = 8080
private const val DEFAULT_SMTP_PORT = 587
private const val DEFAULT_SESSION_TIMEOUT_MINUTES = 30
private const val DEFAULT_TOKEN_PEPPER = "outerstellar-dev-token-pepper"
private const val MIN_SESSION_TIMEOUT_MINUTES = 1
private const val DEFAULT_JWT_EXPIRY_SECONDS = 86400L
private const val MAX_HTTP_PORT = 65535
private const val MIN_HTTP_PORT = 1
private const val DEFAULT_MAX_FAILED_LOGIN_ATTEMPTS = 10
private const val DEFAULT_MAX_REQUEST_BODY_BYTES =
    2L * 1024 * 1024 // 2 MiB — generous for forms/JSON, blocks oversized-body DoS
private const val DEFAULT_LOCKOUT_DURATION_SECONDS = 900L
private const val DEFAULT_REGISTRATION_ENABLED = true
private const val DEFAULT_SESSION_ABSOLUTE_TIMEOUT_MINUTES = 1440
private const val DEFAULT_CSP_POLICY =
    "default-src 'self'; script-src 'self' {nonce}; " +
        "style-src 'self' 'unsafe-inline'; font-src 'self'; connect-src 'self' wss:; img-src 'self' data:; " +
        "base-uri 'self'; form-action 'self'"

const val DEFAULT_PERMISSIONS_POLICY = "camera=(), microphone=(), geolocation=()"

data class SegmentConfig(val writeKey: String = "", val enabled: Boolean = false) {
    override fun toString(): String = "SegmentConfig(writeKey=${mask(writeKey)}, enabled=$enabled)"
}

data class JwtConfig(
    val enabled: Boolean = false,
    val secret: String = "",
    val issuer: String = "outerstellar",
    val expirySeconds: Long = DEFAULT_JWT_EXPIRY_SECONDS,
) {
    init {
        require(!enabled || secret.isNotBlank()) {
            "JWT is enabled but the HMAC secret is blank. Set JWT_SECRET to a strong value (>= 32 chars) before enabling JWT."
        }
    }

    // HMAC secret is a signing key — never expose it in logs, stack traces, or debug output.
    override fun toString(): String =
        "JwtConfig(enabled=$enabled, secret=${mask(secret)}, issuer=$issuer, expirySeconds=$expirySeconds)"
}

data class EmailConfig(
    val enabled: Boolean = false,
    val host: String = "localhost",
    val port: Int = DEFAULT_SMTP_PORT,
    val username: String = "",
    val password: String = "",
    val from: String = "noreply@example.com",
    val startTls: Boolean = true,
) {
    override fun toString(): String =
        "EmailConfig(enabled=$enabled, host=$host, port=$port, username=$username, password=${mask(password)}, from=$from, startTls=$startTls)"
}

data class AppleOAuthConfig(
    val enabled: Boolean = false,
    val teamId: String = "",
    val clientId: String = "",
    val keyId: String = "",
    val privateKeyPem: String = "",
) {
    override fun toString(): String =
        "AppleOAuthConfig(enabled=$enabled, teamId=$teamId, clientId=$clientId, keyId=$keyId, privateKeyPem=${mask(privateKeyPem)})"
}

data class PushNotificationConfig(
    val enabled: Boolean = false,
    val provider: String = "console",
    val fcmServiceAccountJson: String = "",
    val apnsTeamId: String = "",
    val apnsKeyId: String = "",
    val apnsPrivateKeyPem: String = "",
    val apnsBundleId: String = "",
) {
    override fun toString(): String =
        "PushNotificationConfig(enabled=$enabled, provider=$provider, fcmServiceAccountJson=${mask(fcmServiceAccountJson)}, " +
            "apnsTeamId=$apnsTeamId, apnsKeyId=$apnsKeyId, apnsPrivateKeyPem=${mask(apnsPrivateKeyPem)}, apnsBundleId=$apnsBundleId)"
}

data class SecurityHeadersConfig(
    val permissionsPolicy: String = DEFAULT_PERMISSIONS_POLICY,
    val referrerPolicy: String = "strict-origin-when-cross-origin",
    val xFrameOptions: String = "DENY",
    val xContentTypeOptions: String = "nosniff",
    val strictTransportSecurity: String = "max-age=31536000; includeSubDomains",
    val perRouteOverrides: List<RouteHeaderOverride> = emptyList(),
)

data class RouteHeaderOverride(
    val pattern: String,
    val permissionsPolicy: String? = null,
    val referrerPolicy: String? = null,
    val xFrameOptions: String? = null,
    val xContentTypeOptions: String? = null,
    val strictTransportSecurity: String? = null,
    val csp: String? = null,
    val corsAllowedOrigins: List<String>? = null,
)

data class AppConfig(
    val version: String = "dev",
    val port: Int = DEFAULT_HTTP_PORT,
    val jdbcUrl: String = "jdbc:postgresql://localhost:5432/outerstellar",
    val jdbcUser: String = "outerstellar",
    val jdbcPassword: String = DEFAULT_JDBC_PASSWORD,
    val profile: String = "default",
    val devDashboardEnabled: Boolean = false,
    val devMode: Boolean = false,
    val sessionCookieSecure: Boolean = true,
    val sessionTimeoutMinutes: Int = DEFAULT_SESSION_TIMEOUT_MINUTES,
    val tokenPepper: String = DEFAULT_TOKEN_PEPPER,
    val corsOrigins: String = "",
    val csrfEnabled: Boolean = true,
    val segment: SegmentConfig = SegmentConfig(),
    val email: EmailConfig = EmailConfig(),
    val appBaseUrl: String = DEFAULT_APP_BASE_URL,
    val maxFailedLoginAttempts: Int = DEFAULT_MAX_FAILED_LOGIN_ATTEMPTS,
    val maxRequestBodyBytes: Long = DEFAULT_MAX_REQUEST_BODY_BYTES,
    val lockoutDurationSeconds: Long = DEFAULT_LOCKOUT_DURATION_SECONDS,
    val registrationEnabled: Boolean = DEFAULT_REGISTRATION_ENABLED,
    val sessionAbsoluteTimeoutMinutes: Int = DEFAULT_SESSION_ABSOLUTE_TIMEOUT_MINUTES,
    val jwt: JwtConfig = JwtConfig(),
    val cspPolicy: String = DEFAULT_CSP_POLICY,
    val staticDir: String = "",
    val trustedProxies: String = "",
    val appleOAuth: AppleOAuthConfig = AppleOAuthConfig(),
    val pushNotifications: PushNotificationConfig = PushNotificationConfig(),
    val securityHeaders: SecurityHeadersConfig = SecurityHeadersConfig(),
    val runtime: RuntimeConfig = RuntimeConfig(),
    val platformMode: PlatformMode = PlatformMode.FullPlatform,
) {
    /** True when the JDBC password was not overridden from the shipped dev default. */
    fun usesDefaultJdbcPassword(): Boolean = jdbcPassword == DEFAULT_JDBC_PASSWORD

    // Override the data-class toString() so the DB password (and the secrets nested in jwt/email/appleOAuth/
    // pushNotifications, whose own toString() implementations mask them) are never written to logs, stack
    // traces, error messages, or observability output. See issue #524.
    override fun toString(): String =
        "AppConfig(version=$version, port=$port, jdbcUrl=$jdbcUrl, jdbcUser=$jdbcUser, jdbcPassword=${mask(jdbcPassword)}, " +
            "profile=$profile, devDashboardEnabled=$devDashboardEnabled, devMode=$devMode, sessionCookieSecure=$sessionCookieSecure, " +
            "sessionTimeoutMinutes=$sessionTimeoutMinutes, tokenPepper=${mask(tokenPepper)}, corsOrigins=$corsOrigins, csrfEnabled=$csrfEnabled, segment=$segment, " +
            "email=$email, appBaseUrl=$appBaseUrl, maxFailedLoginAttempts=$maxFailedLoginAttempts, maxRequestBodyBytes=$maxRequestBodyBytes, " +
            "lockoutDurationSeconds=$lockoutDurationSeconds, registrationEnabled=$registrationEnabled, " +
            "sessionAbsoluteTimeoutMinutes=$sessionAbsoluteTimeoutMinutes, jwt=$jwt, cspPolicy=$cspPolicy, staticDir=$staticDir, " +
            "trustedProxies=$trustedProxies, appleOAuth=$appleOAuth, pushNotifications=$pushNotifications, " +
            "securityHeaders=$securityHeaders, runtime=$runtime, platformMode=$platformMode)"

    companion object {
        const val DEFAULT_APP_BASE_URL = "http://localhost:8080"

        /**
         * Shipped default JDBC password used only for local/dev convenience. Internal to avoid leaking the known-value
         * credential through the public API; [AppConfig.usesDefaultJdbcPassword] is the supported way to detect that
         * the operator did not override it.
         */
        internal const val DEFAULT_JDBC_PASSWORD = "outerstellar"
        private val logger = LoggerFactory.getLogger(AppConfig::class.java)

        fun fromEnvironment(environment: Map<String, String> = System.getenv()): AppConfig {
            val profile = environment["APP_PROFILE"] ?: "default"
            val yamlData = loadYaml(profile)
            return buildFromYaml(yamlData, environment, profile)
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

        private fun buildFromYaml(
            yaml: Map<String, Any>?,
            env: Map<String, String>,
            profile: String = "default",
        ): AppConfig {
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
                version = yaml.str("version", env, "APP_VERSION", "dev"),
                port = port,
                jdbcUrl = jdbcUrl,
                jdbcUser = yaml.str("jdbcUser", env, "JDBC_USER", "outerstellar"),
                jdbcPassword = yaml.str("jdbcPassword", env, "JDBC_PASSWORD", "outerstellar"),
                profile = profile,
                devDashboardEnabled = yaml.bool("devDashboardEnabled", env, "DEV_DASHBOARD_ENABLED", false),
                devMode = yaml.bool("devMode", env, "DEVMODE", false),
                sessionCookieSecure = yaml.bool("sessionCookieSecure", env, "SESSIONCOOKIESECURE", true),
                sessionTimeoutMinutes = timeout,
                tokenPepper = yaml.str("tokenPepper", env, "TOKEN_PEPPER", DEFAULT_TOKEN_PEPPER),
                corsOrigins = yaml.str("corsOrigins", env, "CORSORIGINS", ""),
                csrfEnabled = yaml.bool("csrfEnabled", env, "CSRFENABLED", true),
                segment = buildSegmentConfig(yaml["segment"] as? Map<String, Any>, env),
                email = buildEmailConfig(yaml["email"] as? Map<String, Any>, env),
                appBaseUrl = yaml.str("appBaseUrl", env, "APPBASEURL", DEFAULT_APP_BASE_URL),
                maxFailedLoginAttempts =
                    yaml.int(
                        "maxFailedLoginAttempts",
                        env,
                        "MAX_FAILED_LOGIN_ATTEMPTS",
                        DEFAULT_MAX_FAILED_LOGIN_ATTEMPTS,
                    ),
                maxRequestBodyBytes =
                    yaml.long("maxRequestBodyBytes", env, "MAX_REQUEST_BODY_BYTES", DEFAULT_MAX_REQUEST_BODY_BYTES),
                lockoutDurationSeconds =
                    yaml.long(
                        "lockoutDurationSeconds",
                        env,
                        "LOCKOUT_DURATION_SECONDS",
                        DEFAULT_LOCKOUT_DURATION_SECONDS,
                    ),
                registrationEnabled =
                    yaml.bool("registrationEnabled", env, "REGISTRATION_ENABLED", DEFAULT_REGISTRATION_ENABLED),
                sessionAbsoluteTimeoutMinutes =
                    yaml.int(
                        "sessionAbsoluteTimeoutMinutes",
                        env,
                        "SESSION_ABSOLUTE_TIMEOUT_MINUTES",
                        DEFAULT_SESSION_ABSOLUTE_TIMEOUT_MINUTES,
                    ),
                jwt = buildJwtConfig(yaml["jwt"] as? Map<String, Any>, env),
                cspPolicy = yaml.str("cspPolicy", env, "CSP_POLICY", DEFAULT_CSP_POLICY),
                staticDir = yaml.staticDir(env),
                trustedProxies = yaml.str("trustedProxies", env, "TRUSTED_PROXIES", ""),
                appleOAuth = buildAppleOAuthConfig(yaml["appleOAuth"] as? Map<String, Any>, env),
                pushNotifications = buildPushNotificationConfig(yaml["pushNotifications"] as? Map<String, Any>, env),
                securityHeaders = buildSecurityHeadersConfig(yaml["securityHeaders"] as? Map<String, Any>, env),
                runtime = buildRuntimeConfig(yaml["runtime"] as? Map<String, Any>, env),
                platformMode =
                    PlatformMode.entries.firstOrNull { it.name.equals(env["PLATFORM_MODE"], ignoreCase = true) }
                        ?: PlatformMode.FullPlatform,
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

        private fun buildAppleOAuthConfig(yaml: Map<String, Any>?, env: Map<String, String>): AppleOAuthConfig {
            if (yaml == null) return AppleOAuthConfig()
            return AppleOAuthConfig(
                enabled = yaml.bool("enabled", env, "APPLE_OAUTH_ENABLED", false),
                teamId = yaml.str("teamId", env, "APPLE_OAUTH_TEAMID", ""),
                clientId = yaml.str("clientId", env, "APPLE_OAUTH_CLIENTID", ""),
                keyId = yaml.str("keyId", env, "APPLE_OAUTH_KEYID", ""),
                privateKeyPem = yaml.str("privateKeyPem", env, "APPLE_OAUTH_PRIVATEKEYPEM", ""),
            )
        }

        private fun buildPushNotificationConfig(
            yaml: Map<String, Any>?,
            env: Map<String, String>,
        ): PushNotificationConfig {
            if (yaml == null) return PushNotificationConfig()
            return PushNotificationConfig(
                enabled = yaml.bool("enabled", env, "PUSH_ENABLED", false),
                provider = yaml.str("provider", env, "PUSH_PROVIDER", "console"),
                fcmServiceAccountJson = yaml.str("fcmServiceAccountJson", env, "PUSH_FCM_SERVICEACCOUNTJSON", ""),
                apnsTeamId = yaml.str("apnsTeamId", env, "PUSH_APNS_TEAMID", ""),
                apnsKeyId = yaml.str("apnsKeyId", env, "PUSH_APNS_KEYID", ""),
                apnsPrivateKeyPem = yaml.str("apnsPrivateKeyPem", env, "PUSH_APNS_PRIVATEKEYPEM", ""),
                apnsBundleId = yaml.str("apnsBundleId", env, "PUSH_APNS_BUNDLEID", ""),
            )
        }

        private fun buildSecurityHeadersConfig(
            yaml: Map<String, Any>?,
            env: Map<String, String>,
        ): SecurityHeadersConfig {
            val map = yaml ?: emptyMap()
            return SecurityHeadersConfig(
                permissionsPolicy = map.str("permissionsPolicy", env, "PERMISSIONS_POLICY", DEFAULT_PERMISSIONS_POLICY),
                referrerPolicy = map.str("referrerPolicy", env, "REFERRER_POLICY", "strict-origin-when-cross-origin"),
                xFrameOptions = map.str("xFrameOptions", env, "X_FRAME_OPTIONS", "DENY"),
                xContentTypeOptions = map.str("xContentTypeOptions", env, "X_CONTENT_TYPE_OPTIONS", "nosniff"),
                strictTransportSecurity =
                    map.str(
                        "strictTransportSecurity",
                        env,
                        "STRICT_TRANSPORT_SECURITY",
                        "max-age=31536000; includeSubDomains",
                    ),
                perRouteOverrides = buildPerRouteOverrides(map["perRouteOverrides"]),
            )
        }

        @Suppress("UNCHECKED_CAST")
        private fun buildPerRouteOverrides(raw: Any?): List<RouteHeaderOverride> {
            val list = raw as? List<*> ?: return emptyList()
            return list.mapNotNull { entry ->
                val map = entry as? Map<String, Any> ?: return@mapNotNull null
                RouteHeaderOverride(
                    pattern = map["pattern"] as? String ?: "",
                    permissionsPolicy = map["permissionsPolicy"] as? String,
                    referrerPolicy = map["referrerPolicy"] as? String,
                    xFrameOptions = map["xFrameOptions"] as? String,
                    xContentTypeOptions = map["xContentTypeOptions"] as? String,
                    strictTransportSecurity = map["strictTransportSecurity"] as? String,
                    csp = map["csp"] as? String,
                    corsAllowedOrigins = (map["corsAllowedOrigins"] as? List<*>)?.map { it.toString() },
                )
            }
        }

        @Suppress("LongMethod")
        private fun buildRuntimeConfig(yaml: Map<String, Any>?, env: Map<String, String>): RuntimeConfig {
            if (yaml == null) return RuntimeConfig()
            return RuntimeConfig(
                hikariMaximumPoolSize =
                    yaml.int(
                        "hikariMaximumPoolSize",
                        env,
                        "HIKARI_MAX_POOL_SIZE",
                        RuntimeConfig().hikariMaximumPoolSize,
                    ),
                hikariMinimumIdle =
                    yaml.int("hikariMinimumIdle", env, "HIKARI_MIN_IDLE", RuntimeConfig().hikariMinimumIdle),
                hikariIdleTimeoutMs =
                    yaml.long(
                        "hikariIdleTimeoutMs",
                        env,
                        "HIKARI_IDLE_TIMEOUT_MS",
                        RuntimeConfig().hikariIdleTimeoutMs,
                    ),
                hikariMaxLifetimeMs =
                    yaml.long(
                        "hikariMaxLifetimeMs",
                        env,
                        "HIKARI_MAX_LIFETIME_MS",
                        RuntimeConfig().hikariMaxLifetimeMs,
                    ),
                hikariConnectionTimeoutMs =
                    yaml.long(
                        "hikariConnectionTimeoutMs",
                        env,
                        "HIKARI_CONNECTION_TIMEOUT_MS",
                        RuntimeConfig().hikariConnectionTimeoutMs,
                    ),
                hikariLeakDetectionThresholdMs =
                    yaml.long(
                        "hikariLeakDetectionThresholdMs",
                        env,
                        "HIKARI_LEAK_DETECTION_THRESHOLD_MS",
                        RuntimeConfig().hikariLeakDetectionThresholdMs,
                    ),
                flywayEnabled = yaml.bool("flywayEnabled", env, "FLYWAY_ENABLED", RuntimeConfig().flywayEnabled),
                jtePreloadEnabled =
                    yaml.bool("jtePreloadEnabled", env, "JTE_PRELOAD_ENABLED", RuntimeConfig().jtePreloadEnabled),
                cacheMessageMaxSize =
                    yaml.int("cacheMessageMaxSize", env, "CACHE_MESSAGE_MAX_SIZE", RuntimeConfig().cacheMessageMaxSize),
                cacheMessageExpireMinutes =
                    yaml.int(
                        "cacheMessageExpireMinutes",
                        env,
                        "CACHE_MESSAGE_EXPIRE_MINUTES",
                        RuntimeConfig().cacheMessageExpireMinutes,
                    ),
                cacheGravatarMaxSize =
                    yaml.long(
                        "cacheGravatarMaxSize",
                        env,
                        "CACHE_GRAVATAR_MAX_SIZE",
                        RuntimeConfig().cacheGravatarMaxSize,
                    ),
                rateLimitIpCapacity =
                    yaml.int("rateLimitIpCapacity", env, "RATE_LIMIT_IP_CAPACITY", RuntimeConfig().rateLimitIpCapacity),
                rateLimitIpRefillPerMinute =
                    yaml.int(
                        "rateLimitIpRefillPerMinute",
                        env,
                        "RATE_LIMIT_IP_REFILL_PER_MINUTE",
                        RuntimeConfig().rateLimitIpRefillPerMinute,
                    ),
                rateLimitAccountCapacity =
                    yaml.int(
                        "rateLimitAccountCapacity",
                        env,
                        "RATE_LIMIT_ACCOUNT_CAPACITY",
                        RuntimeConfig().rateLimitAccountCapacity,
                    ),
                rateLimitAccountWindowMs =
                    yaml.long(
                        "rateLimitAccountWindowMs",
                        env,
                        "RATE_LIMIT_ACCOUNT_WINDOW_MS",
                        RuntimeConfig().rateLimitAccountWindowMs,
                    ),
            )
        }
    }
}

private fun Map<String, Any>.str(key: String, env: Map<String, String>, envKey: String, default: String): String =
    env[envKey] ?: (this[key] as? String) ?: default

private fun Map<String, Any>.staticDir(env: Map<String, String>): String =
    env["STATIC_DIR"] ?: env["ASSETS_DIR"] ?: (this["staticDir"] as? String) ?: (this["assetsDir"] as? String) ?: ""

private fun Map<String, Any>.int(key: String, env: Map<String, String>, envKey: String, default: Int): Int =
    env[envKey]?.toInt() ?: (this[key] as? Int) ?: default

private fun Map<String, Any>.bool(key: String, env: Map<String, String>, envKey: String, default: Boolean): Boolean {
    // Fail loud on a malformed boolean value rather than silently disabling security-sensitive flags.
    // Kotlin's String.toBoolean() returns false for any non-"true" string, so SESSION_COOKIE_SECURE=yes
    // would silently disable the Secure flag; require an explicit true/false (case-insensitive) instead.
    val raw = env[envKey] ?: return (this[key] as? Boolean) ?: default
    return when (raw.lowercase()) {
        "true" -> true
        "false" -> false
        else ->
            throw IllegalArgumentException(
                "Environment variable $envKey='$raw' is not a valid boolean (expected 'true' or 'false')."
            )
    }
}

private fun Map<String, Any>.long(key: String, env: Map<String, String>, envKey: String, default: Long): Long =
    env[envKey]?.toLong() ?: (this[key] as? Long) ?: default

/**
 * Masks a secret value for safe inclusion in `toString()` output. Empty/blank values are reported as `""` (so it's
 * clear the secret is genuinely unset); any non-blank value is reported as `***`. The actual secret material is never
 * rendered, not even partially — leaking a suffix would still leak entropy.
 */
private fun mask(secret: String): String = if (secret.isBlank()) "\"\"" else "\"***\""
