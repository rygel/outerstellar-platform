package dev.outerstellar.starter

import com.sksamuel.hoplite.ConfigLoaderBuilder
import com.sksamuel.hoplite.addEnvironmentSource
import com.sksamuel.hoplite.addMapSource

data class AppConfig(
  val port: Int = 8080,
  val jdbcUrl: String = "jdbc:h2:file:./data/outerstellar-starter;MODE=PostgreSQL;AUTO_SERVER=TRUE",
  val jdbcUser: String = "sa",
  val jdbcPassword: String = "",
) {
  companion object {
    fun fromEnvironment(environment: Map<String, String> = System.getenv()): AppConfig =
      ConfigLoaderBuilder.default()
        .addMapSource(environment)
        .addEnvironmentSource()
        .build()
        .loadConfigOrThrow<AppConfig>()
  }
}
