package dev.outerstellar.starter

data class AppConfig(
  val port: Int,
  val jdbcUrl: String,
  val jdbcUser: String,
  val jdbcPassword: String,
) {
  companion object {
    private const val defaultPort = 8080
    private const val defaultJdbcUrl =
      "jdbc:h2:file:./data/outerstellar-starter;MODE=PostgreSQL;AUTO_SERVER=TRUE"

    fun fromEnvironment(environment: Map<String, String> = System.getenv()): AppConfig =
      AppConfig(
        port = environment["PORT"]?.toIntOrNull() ?: defaultPort,
        jdbcUrl = environment["JDBC_URL"] ?: defaultJdbcUrl,
        jdbcUser = environment["JDBC_USER"] ?: "sa",
        jdbcPassword = environment["JDBC_PASSWORD"] ?: "",
      )
  }
}
