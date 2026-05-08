package io.github.rygel.outerstellar.platform.swing

import org.snakeyaml.engine.v2.api.Load
import org.snakeyaml.engine.v2.api.LoadSettings

data class SwingAppConfig(
    val serverBaseUrl: String = "http://localhost:8080",
    val jdbcUrl: String = "jdbc:postgresql://localhost:5432/outerstellar",
    val jdbcUser: String = "outerstellar",
    val jdbcPassword: String = "outerstellar",
    val version: String = "1.0.0",
    val updateUrl: String = "",
    val devMode: Boolean = false,
    val devUsername: String = "",
    val devPassword: String = "",
    val segmentWriteKey: String = "",
    val analyticsEnabled: Boolean = false,
    val analyticsFlushIntervalHours: Long = 24,
    val analyticsMaxFileSizeKb: Long = 2048,
    val analyticsMaxEventAgeDays: Long = 30,
) {
    companion object {
        fun fromEnvironment(environment: Map<String, String> = System.getenv()): SwingAppConfig {
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
            val stream = SwingAppConfig::class.java.getResourceAsStream(path) ?: return null
            return try {
                loader.loadFromInputStream(stream) as? Map<String, Any>
            } finally {
                stream.close()
            }
        }

        private fun buildFromYaml(yaml: Map<String, Any>?, env: Map<String, String>): SwingAppConfig {
            if (yaml == null) return SwingAppConfig()
            return SwingAppConfig(
                serverBaseUrl = yaml.str("serverBaseUrl", env, "SERVER_BASE_URL", "http://localhost:8080"),
                jdbcUrl = yaml.str("jdbcUrl", env, "JDBC_URL", "jdbc:postgresql://localhost:5432/outerstellar"),
                jdbcUser = yaml.str("jdbcUser", env, "JDBC_USER", "outerstellar"),
                jdbcPassword = yaml.str("jdbcPassword", env, "JDBC_PASSWORD", "outerstellar"),
                version = yaml.str("version", env, "VERSION", "1.0.0"),
                updateUrl = yaml.str("updateUrl", env, "UPDATE_URL", ""),
                devMode = yaml.bool("devMode", env, "DEV_MODE", false),
                devUsername = yaml.str("devUsername", env, "DEV_USERNAME", ""),
                devPassword = yaml.str("devPassword", env, "DEV_PASSWORD", ""),
                segmentWriteKey = yaml.str("segmentWriteKey", env, "SEGMENT_WRITEKEY", ""),
                analyticsEnabled = yaml.bool("analyticsEnabled", env, "ANALYTICS_ENABLED", false),
                analyticsFlushIntervalHours =
                    yaml.long("analyticsFlushIntervalHours", env, "ANALYTICS_FLUSH_INTERVAL_HOURS", 24L),
                analyticsMaxFileSizeKb = yaml.long("analyticsMaxFileSizeKb", env, "ANALYTICS_MAX_FILE_SIZE_KB", 2048L),
                analyticsMaxEventAgeDays =
                    yaml.long("analyticsMaxEventAgeDays", env, "ANALYTICS_MAX_EVENT_AGE_DAYS", 30L),
            )
        }
    }
}

private fun Map<String, Any>.str(key: String, env: Map<String, String>, envKey: String, default: String): String =
    env[envKey] ?: (this[key] as? String) ?: default

private fun Map<String, Any>.bool(key: String, env: Map<String, String>, envKey: String, default: Boolean): Boolean =
    env[envKey]?.toBoolean() ?: (this[key] as? Boolean) ?: default

private fun Map<String, Any>.long(key: String, env: Map<String, String>, envKey: String, default: Long): Long =
    env[envKey]?.toLong() ?: (this[key] as? Long) ?: default
