package io.github.rygel.outerstellar.platform

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows

class AppConfigTest {

    @Test
    fun `clamps port to valid range`() {
        val env = mapOf("PORT" to "0")
        val config = AppConfig.fromEnvironment(env)
        assert(config.port in 1..65535) { "Expected port in 1..65535 but was ${config.port}" }
    }

    @Test
    fun `clamps negative port`() {
        val env = mapOf("PORT" to "-5")
        val config = AppConfig.fromEnvironment(env)
        assert(config.port >= 1) { "Expected port >= 1 but was ${config.port}" }
    }

    @Test
    fun `clamps session timeout to minimum of 1`() {
        val env = mapOf("SESSIONTIMEOUTMINUTES" to "0")
        val config = AppConfig.fromEnvironment(env)
        assert(config.sessionTimeoutMinutes >= 1) { "Expected timeout >= 1 but was ${config.sessionTimeoutMinutes}" }
    }

    @Test
    fun `clamps negative session timeout`() {
        val env = mapOf("SESSIONTIMEOUTMINUTES" to "-10")
        val config = AppConfig.fromEnvironment(env)
        assert(config.sessionTimeoutMinutes >= 1) { "Expected timeout >= 1 but was ${config.sessionTimeoutMinutes}" }
    }

    @Test
    fun `warns when jdbcUrl is missing`() {
        val env = mapOf("JDBC_URL" to "", "JDBC_USER" to "", "JDBC_PASSWORD" to "")
        assertDoesNotThrow { AppConfig.fromEnvironment(env) }
    }

    @Test
    fun `static dir is set from STATIC_DIR env var`() {
        val config = AppConfig.fromEnvironment(mapOf("STATIC_DIR" to "C:\\outerstellar\\assets"))

        assert(config.staticDir == "C:\\outerstellar\\assets") {
            "Expected STATIC_DIR value but was ${config.staticDir}"
        }
    }

    @Test
    fun `static dir is set from ASSETS_DIR env var`() {
        val config = AppConfig.fromEnvironment(mapOf("ASSETS_DIR" to "C:\\outerstellar\\assets"))

        assert(config.staticDir == "C:\\outerstellar\\assets") {
            "Expected ASSETS_DIR value but was ${config.staticDir}"
        }
    }

    @Test
    fun `accepts valid config without warnings`() {
        val env =
            mapOf(
                "PORT" to "9091",
                "JDBC_URL" to "jdbc:postgresql://localhost:5432/outerstellar",
                "JDBC_USER" to "outerstellar",
                "JDBC_PASSWORD" to "outerstellar",
                "SESSIONTIMEOUTMINUTES" to "60",
            )
        assertDoesNotThrow {
            val config = AppConfig.fromEnvironment(env)
            assert(config.port == 9091)
            assert(config.sessionTimeoutMinutes == 60)
        }
    }

    @Test
    fun `profile defaults to default when APP_PROFILE not set`() {
        val config = AppConfig.fromEnvironment(emptyMap())
        assert(config.profile == "default") { "Expected 'default' but was ${config.profile}" }
    }

    @Test
    fun `profile is set from APP_PROFILE env var`() {
        val config = AppConfig.fromEnvironment(mapOf("APP_PROFILE" to "prod"))
        assert(config.profile == "prod") { "Expected 'prod' but was ${config.profile}" }
    }

    @Test
    fun `DEFAULT_JDBC_PASSWORD constant matches data class default`() {
        assert(AppConfig.DEFAULT_JDBC_PASSWORD == AppConfig().jdbcPassword)
    }

    @Test
    fun `permissions policy is set from PERMISSIONS_POLICY env var`() {
        val config = AppConfig.fromEnvironment(mapOf("PERMISSIONS_POLICY" to "geolocation=(self)"))
        assert(config.securityHeaders.permissionsPolicy == "geolocation=(self)") {
            "Expected geolocation=(self) but was ${config.securityHeaders.permissionsPolicy}"
        }
    }

    @Test
    fun `permissions policy defaults to camera microphone geolocation`() {
        val config = AppConfig.fromEnvironment(emptyMap())
        assert(config.securityHeaders.permissionsPolicy == "camera=(), microphone=(), geolocation=()")
    }

    @Test
    fun `x frame options is set from X_FRAME_OPTIONS env var`() {
        val config = AppConfig.fromEnvironment(mapOf("X_FRAME_OPTIONS" to "SAMEORIGIN"))
        assert(config.securityHeaders.xFrameOptions == "SAMEORIGIN")
    }

    @Test
    fun `referrer policy is set from REFERRER_POLICY env var`() {
        val config = AppConfig.fromEnvironment(mapOf("REFERRER_POLICY" to "no-referrer"))
        assert(config.securityHeaders.referrerPolicy == "no-referrer")
    }

    @Test
    fun `strict transport security is set from env var`() {
        val config = AppConfig.fromEnvironment(mapOf("STRICT_TRANSPORT_SECURITY" to "max-age=999"))
        assert(config.securityHeaders.strictTransportSecurity == "max-age=999")
    }

    @Test
    fun `security headers defaults match current hard-coded values`() {
        val config = AppConfig.fromEnvironment(emptyMap())
        assert(config.securityHeaders.xContentTypeOptions == "nosniff")
        assert(config.securityHeaders.xFrameOptions == "DENY")
        assert(config.securityHeaders.referrerPolicy == "strict-origin-when-cross-origin")
        assert(config.securityHeaders.strictTransportSecurity == "max-age=31536000; includeSubDomains")
        assert(config.securityHeaders.perRouteOverrides.isEmpty())
    }

    @Test
    fun `management token is set from environment`() {
        val config = AppConfig.fromEnvironment(mapOf("MANAGEMENT_TOKEN" to "probe-token"))

        assert(config.managementToken == "probe-token")
    }

    @Test
    fun `AppConfig toString masks the JDBC password and nested secrets`() {
        val config =
            AppConfig(
                jdbcPassword = "super-secret-db-password",
                managementToken = "management-probe-secret",
                jwt = JwtConfig(enabled = true, secret = "jwt-hmac-signing-key"),
                email = EmailConfig(enabled = true, password = "smtp-password"),
                appleOAuth = AppleOAuthConfig(enabled = true, privateKeyPem = "-----BEGIN PRIVATE KEY-----"),
                pushNotifications =
                    PushNotificationConfig(
                        enabled = true,
                        fcmServiceAccountJson = """{"type":"service_account"}""",
                        apnsPrivateKeyPem = "-----BEGIN APNS KEY-----",
                    ),
                segment = SegmentConfig(writeKey = "segment-write-key"),
            )
        val rendered = config.toString()

        assert(!rendered.contains("super-secret-db-password")) { "JDBC password leaked in toString: $rendered" }
        assert(!rendered.contains("management-probe-secret")) { "Management token leaked in toString: $rendered" }
        assert(!rendered.contains("jwt-hmac-signing-key")) { "JWT secret leaked in toString: $rendered" }
        assert(!rendered.contains("smtp-password")) { "SMTP password leaked in toString: $rendered" }
        assert(!rendered.contains("BEGIN PRIVATE KEY")) { "Apple OAuth private key leaked in toString: $rendered" }
        assert(!rendered.contains("service_account")) { "FCM service account JSON leaked in toString: $rendered" }
        assert(!rendered.contains("BEGIN APNS KEY")) { "APNS private key leaked in toString: $rendered" }
        assert(!rendered.contains("segment-write-key")) { "Segment write key leaked in toString: $rendered" }
    }

    @Test
    fun `AppConfig toString preserves non-secret fields for diagnostics`() {
        // jdbcPassword="" (explicitly blank) exercises the empty-secret marker path; the data-class
        // default is "outerstellar", which would be masked as "***" — so set it explicitly here.
        val config = AppConfig(port = 9090, jdbcUser = "outerstellar", jdbcPassword = "", profile = "prod")
        val rendered = config.toString()

        // Non-secret values remain visible so toString() is still useful for diagnostics.
        assert(rendered.contains("port=9090")) { "Expected port in toString: $rendered" }
        assert(rendered.contains("jdbcUser=outerstellar")) { "Expected jdbcUser in toString: $rendered" }
        assert(rendered.contains("profile=prod")) { "Expected profile in toString: $rendered" }
        // A blank password renders as an explicit empty marker, not the literal secret.
        assert(rendered.contains("jdbcPassword=\"\"")) { "Expected blank jdbcPassword marker: $rendered" }
    }

    @Test
    fun `JwtConfig toString masks the HMAC secret`() {
        val rendered = JwtConfig(secret = "do-not-leak-me").toString()
        assert(!rendered.contains("do-not-leak-me")) { "JWT secret leaked: $rendered" }
        assert(rendered.contains("***")) { "Expected mask marker: $rendered" }
    }

    @Test
    fun `EmailConfig toString masks the SMTP password`() {
        val rendered = EmailConfig(password = "do-not-leak-me").toString()
        assert(!rendered.contains("do-not-leak-me")) { "SMTP password leaked: $rendered" }
    }

    @Test
    fun `usesDefaultJdbcPassword detects the shipped default`() {
        assert(AppConfig().usesDefaultJdbcPassword()) { "Default config should use the shipped JDBC password" }
        assert(AppConfig(jdbcPassword = "overridden").usesDefaultJdbcPassword().not()) {
            "An overridden password should not be flagged as default"
        }
    }

    @Test
    fun `bool env var rejects non-boolean values instead of silently defaulting to false`() {
        // Fail-loud parsing (issue #527): a typo like SESSIONCOOKIESECURE=yes must crash-start rather
        // than silently disable the Secure cookie flag.
        assertThrows<IllegalArgumentException> { AppConfig.fromEnvironment(mapOf("SESSIONCOOKIESECURE" to "yes")) }
    }

    @Test
    fun `bool env var accepts true and false case-insensitively`() {
        assert(AppConfig.fromEnvironment(mapOf("SESSIONCOOKIESECURE" to "TRUE")).sessionCookieSecure)
        assert(AppConfig.fromEnvironment(mapOf("SESSIONCOOKIESECURE" to "False")).sessionCookieSecure.not())
    }

    @Test
    fun `JwtConfig rejects enabled with a blank secret at construction`() {
        assertThrows<IllegalArgumentException> { JwtConfig(enabled = true, secret = "") }
        // Disabled with blank secret is still allowed (JWT simply won't be used).
        JwtConfig(enabled = false, secret = "")
    }
}
