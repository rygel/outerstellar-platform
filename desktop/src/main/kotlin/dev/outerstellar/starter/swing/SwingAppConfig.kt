package dev.outerstellar.starter.swing

import com.sksamuel.hoplite.ConfigLoaderBuilder
import com.sksamuel.hoplite.addEnvironmentSource
import com.sksamuel.hoplite.addMapSource

data class SwingAppConfig(
  val serverBaseUrl: String = "http://localhost:8080",
  val jdbcUrl: String = "jdbc:h2:file:./data/outerstellar-swing-client;MODE=PostgreSQL;AUTO_SERVER=TRUE",
  val jdbcUser: String = "sa",
  val jdbcPassword: String = "",
  val version: String = "1.0.0",
  val updateUrl: String = ""
) {
  companion object {
    fun fromEnvironment(environment: Map<String, String> = System.getenv()): SwingAppConfig =
      ConfigLoaderBuilder.default()
        .addMapSource(environment)
        .addEnvironmentSource()
        .build()
        .loadConfigOrThrow<SwingAppConfig>()
  }
}
