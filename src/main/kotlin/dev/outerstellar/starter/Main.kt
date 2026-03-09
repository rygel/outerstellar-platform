package dev.outerstellar.starter

import dev.outerstellar.starter.persistence.JooqMessageRepository
import gg.jte.ContentType
import gg.jte.TemplateEngine
import gg.jte.output.StringOutput
import gg.jte.resolve.DirectoryCodeResolver
import gg.jte.resolve.ResourceCodeResolver
import java.nio.file.Files
import java.nio.file.Path
import javax.sql.DataSource
import org.flywaydb.core.Flyway
import org.h2.jdbcx.JdbcDataSource
import org.http4k.server.Jetty
import org.http4k.server.asServer
import org.http4k.template.TemplateRenderer
import org.http4k.template.ViewModel
import org.http4k.template.ViewNotFound
import org.jooq.SQLDialect
import org.jooq.impl.DSL
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("dev.outerstellar.starter.Main")

fun main() {
  val config = AppConfig.fromEnvironment()
  val dataSource = createDataSource(config)

  migrate(dataSource)

  val repository = JooqMessageRepository(DSL.using(dataSource, SQLDialect.H2))
  repository.seedStarterMessages()

  val server = app(repository, createRenderer()).asServer(Jetty(config.port)).start()
  logger.info("Outerstellar starter running on http://localhost:{}", server.port())
}

internal fun createDataSource(config: AppConfig): DataSource =
  createDataSource(config.jdbcUrl, config.jdbcUser, config.jdbcPassword)

internal fun createDataSource(jdbcUrl: String, jdbcUser: String, jdbcPassword: String): DataSource =
  JdbcDataSource().apply {
    setURL(jdbcUrl)
    user = jdbcUser
    password = jdbcPassword
  }

internal fun migrate(dataSource: DataSource) {
  Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate()
}

internal fun createRenderer(): TemplateRenderer {
  val projectDirectory = Path.of(System.getProperty("user.dir"))
  val sourceTemplates = projectDirectory.resolve(Path.of("src", "main", "jte"))
  val generatedTemplateClasses = projectDirectory.resolve(Path.of("target", "jte-classes"))
  val applicationClassLoader = Thread.currentThread().contextClassLoader

  return if (Files.isDirectory(sourceTemplates)) {
    renderUsing {
      TemplateEngine.create(
        DirectoryCodeResolver(sourceTemplates),
        generatedTemplateClasses,
        ContentType.Html,
        applicationClassLoader,
      )
    }
  } else {
    val classpathEngine =
      TemplateEngine.create(
        ResourceCodeResolver("."),
        generatedTemplateClasses,
        ContentType.Html,
        applicationClassLoader,
      )
    renderUsing { classpathEngine }
  }
}

private fun renderUsing(engineProvider: () -> TemplateEngine): TemplateRenderer =
  { viewModel: ViewModel ->
    val templateName = "${viewModel.template()}.kte"
    val templateEngine = engineProvider()

    if (templateEngine.hasTemplate(templateName)) {
      StringOutput().also { templateEngine.render(templateName, viewModel, it) }.toString()
    } else {
      throw ViewNotFound(viewModel)
    }
  }
