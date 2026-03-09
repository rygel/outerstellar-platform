package dev.outerstellar.starter.swing

data class SwingAppConfig(
  val serverBaseUrl: String,
  val jdbcUrl: String,
  val jdbcUser: String,
  val jdbcPassword: String,
) {
  companion object {
    private const val defaultServerBaseUrl = "http://localhost:8080"
    private const val defaultJdbcUrl =
      "jdbc:h2:file:./data/outerstellar-swing-client;MODE=PostgreSQL;AUTO_SERVER=TRUE"

    fun fromEnvironment(environment: Map<String, String> = System.getenv()): SwingAppConfig =
      SwingAppConfig(
        serverBaseUrl = environment["SYNC_SERVER_URL"] ?: defaultServerBaseUrl,
        jdbcUrl = environment["SWING_JDBC_URL"] ?: defaultJdbcUrl,
        jdbcUser = environment["SWING_JDBC_USER"] ?: "sa",
        jdbcPassword = environment["SWING_JDBC_PASSWORD"] ?: "",
      )
  }
}
