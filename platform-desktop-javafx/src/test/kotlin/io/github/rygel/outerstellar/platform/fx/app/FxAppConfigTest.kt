package io.github.rygel.outerstellar.platform.fx.app

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class FxAppConfigTest {

    @Test
    fun `default config has expected values`() {
        val config = FxAppConfig()
        assertThat(config.serverBaseUrl).isEqualTo("http://localhost:8080")
        assertThat(config.jdbcUrl).isEqualTo("jdbc:postgresql://localhost:5432/outerstellar")
        assertThat(config.jdbcUser).isEqualTo("outerstellar")
        assertThat(config.jdbcPassword).isEqualTo("outerstellar")
        assertThat(config.version).isEqualTo("1.0.0")
        assertThat(config.updateUrl).isEmpty()
        assertThat(config.devMode).isFalse()
        assertThat(config.devUsername).isEmpty()
        assertThat(config.devPassword).isEmpty()
        assertThat(config.segmentWriteKey).isEmpty()
        assertThat(config.analyticsEnabled).isFalse()
        assertThat(config.analyticsFlushIntervalHours).isEqualTo(24L)
        assertThat(config.analyticsMaxFileSizeKb).isEqualTo(2048L)
        assertThat(config.analyticsMaxEventAgeDays).isEqualTo(30L)
    }

    @Test
    fun `fromEnvironment with empty env and no yaml returns defaults`() {
        val config = FxAppConfig.fromEnvironment(emptyMap())
        assertThat(config.serverBaseUrl).isEqualTo("http://localhost:8080")
        assertThat(config.jdbcUrl).isEqualTo("jdbc:postgresql://localhost:5432/outerstellar")
        assertThat(config.jdbcUser).isEqualTo("outerstellar")
        assertThat(config.jdbcPassword).isEqualTo("outerstellar")
        assertThat(config.version).isEqualTo("1.0.0")
        assertThat(config.devMode).isFalse()
    }

    @Test
    fun `fromEnvironment uses env vars over defaults`() {
        val env =
            mapOf(
                "SERVER_BASE_URL" to "https://prod.example.com",
                "JDBC_URL" to "jdbc:postgresql://db:5432/mydb",
                "JDBC_USER" to "admin",
                "JDBC_PASSWORD" to "secret",
                "DEV_MODE" to "true",
                "VERSION" to "2.0.0",
            )
        val config = FxAppConfig.fromEnvironment(env)
        assertThat(config.serverBaseUrl).isEqualTo("https://prod.example.com")
        assertThat(config.jdbcUrl).isEqualTo("jdbc:postgresql://db:5432/mydb")
        assertThat(config.jdbcUser).isEqualTo("admin")
        assertThat(config.jdbcPassword).isEqualTo("secret")
        assertThat(config.devMode).isTrue()
        assertThat(config.version).isEqualTo("2.0.0")
    }

    @Test
    fun `fromEnvironment uses analytics env vars`() {
        val env =
            mapOf(
                "ANALYTICS_ENABLED" to "true",
                "ANALYTICS_FLUSH_INTERVAL_HOURS" to "12",
                "ANALYTICS_MAX_FILE_SIZE_KB" to "4096",
                "ANALYTICS_MAX_EVENT_AGE_DAYS" to "60",
                "SEGMENT_WRITEKEY" to "test-key-123",
            )
        val config = FxAppConfig.fromEnvironment(env)
        assertThat(config.analyticsEnabled).isTrue()
        assertThat(config.analyticsFlushIntervalHours).isEqualTo(12L)
        assertThat(config.analyticsMaxFileSizeKb).isEqualTo(4096L)
        assertThat(config.analyticsMaxEventAgeDays).isEqualTo(60L)
        assertThat(config.segmentWriteKey).isEqualTo("test-key-123")
    }

    @Test
    fun `data class copy preserves non-overridden fields`() {
        val original = FxAppConfig()
        val modified = original.copy(serverBaseUrl = "https://custom.example.com", devMode = true)

        assertThat(modified.serverBaseUrl).isEqualTo("https://custom.example.com")
        assertThat(modified.devMode).isTrue()
        assertThat(modified.jdbcUrl).isEqualTo(original.jdbcUrl)
        assertThat(modified.version).isEqualTo(original.version)
    }
}
