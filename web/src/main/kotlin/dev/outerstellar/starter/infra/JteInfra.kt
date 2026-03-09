package dev.outerstellar.starter.infra

import gg.jte.ContentType
import gg.jte.TemplateEngine
import gg.jte.output.StringOutput
import gg.jte.resolve.DirectoryCodeResolver
import gg.jte.resolve.ResourceCodeResolver
import java.nio.file.Files
import java.nio.file.Path
import org.http4k.template.TemplateRenderer
import org.http4k.template.ViewModel
import org.http4k.template.ViewNotFound

fun createRenderer(): TemplateRenderer {
  val isProduction = System.getProperty("jte.production") == "true" || System.getenv("JTE_PRODUCTION") == "true"
  val applicationClassLoader = Thread.currentThread().contextClassLoader

  val templateEngine = if (isProduction) {
    TemplateEngine.createPrecompiled(ContentType.Html)
  } else {
    val projectDirectory = Path.of(System.getProperty("user.dir"))
    val sourceTemplates = projectDirectory.resolve(Path.of("web", "src", "main", "jte"))
    val generatedTemplateClasses = projectDirectory.resolve(Path.of("web", "target", "jte-classes"))

    if (Files.isDirectory(sourceTemplates)) {
      TemplateEngine.create(
        DirectoryCodeResolver(sourceTemplates),
        generatedTemplateClasses,
        ContentType.Html,
        applicationClassLoader,
      )
    } else {
      TemplateEngine.create(
        ResourceCodeResolver("."),
        generatedTemplateClasses,
        ContentType.Html,
        applicationClassLoader,
      )
    }
  }

  return renderUsing { templateEngine }
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
