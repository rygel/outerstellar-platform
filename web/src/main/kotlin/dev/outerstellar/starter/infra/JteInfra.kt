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
  val projectDirectory = Path.of(System.getProperty("user.dir"))
  // In a multi-module project, the templates are in the 'web' module
  val sourceTemplates = projectDirectory.resolve(Path.of("web", "src", "main", "jte"))
  val generatedTemplateClasses = projectDirectory.resolve(Path.of("web", "target", "jte-classes"))
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
