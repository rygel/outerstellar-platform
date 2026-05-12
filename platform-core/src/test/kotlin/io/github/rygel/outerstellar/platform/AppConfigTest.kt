package io.github.rygel.outerstellar.platform

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow

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
        }
    }
}
