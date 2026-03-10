package dev.outerstellar.starter.web

import dev.outerstellar.starter.infra.render
import com.outerstellar.i18n.I18nService
import dev.outerstellar.starter.persistence.MessageRepository
import dev.outerstellar.starter.service.MessageService
import org.http4k.contract.ContractRoute
import org.http4k.contract.bindContract
import org.http4k.contract.meta
import org.http4k.contract.div
import org.http4k.core.Body
import org.http4k.core.ContentType
import org.http4k.core.Method.GET
import org.http4k.core.Method.POST
import org.http4k.core.Method.DELETE
import org.http4k.core.Response
import org.http4k.core.Status
import org.http4k.core.body.form
import org.http4k.core.getFirst
import org.http4k.core.toParametersMap
import org.http4k.lens.Query
import org.http4k.lens.int
import org.http4k.lens.string
import org.http4k.lens.Path
import org.http4k.template.TemplateRenderer

import org.http4k.lens.FormField
import org.http4k.lens.Validator
import org.http4k.lens.webForm

class HomeRoutes(
    private val messageService: MessageService,
    private val repository: MessageRepository,
    private val pageFactory: WebPageFactory,
    private val renderer: TemplateRenderer,
    private val i18nService: I18nService
) : ServerRoutes {
    private val queryLens = Query.string().optional("q")
    private val yearLens = Query.int().optional("year")
    private val limitLens = Query.int().defaulted("limit", 10)
    private val offsetLens = Query.int().defaulted("offset", 0)
    private val syncIdPath = Path.string().of("syncId")

    // Form Lenses
    private val authorField = FormField.string().optional("author")
    private val contentField = FormField.string().required("content")
    private val messageFormLens = Body.webForm(Validator.Strict, authorField, contentField).toLens()

    private val defaultAuthor = i18nService.translate("web.author.default")
    private val contentRequiredMessage = i18nService.translate("web.validation.contentRequired")

    override val routes = listOf(
        "/" meta {
            summary = "Home page"
            queries += queryLens
            queries += yearLens
            queries += limitLens
            queries += offsetLens
        } bindContract GET to { request: org.http4k.core.Request ->
            val query = queryLens(request)
            val year = yearLens(request)
            val limit = limitLens(request)
            val offset = offsetLens(request)
            renderer.render(pageFactory.buildHomePage(request.webContext, query, limit, offset, year))
        },
        "/messages/trash" meta {
            summary = "Trash page"
            queries += queryLens
            queries += limitLens
            queries += offsetLens
        } bindContract GET to { request: org.http4k.core.Request ->
            val query = queryLens(request)
            val limit = limitLens(request)
            val offset = offsetLens(request)
            val ctx = request.webContext
            renderer.render(pageFactory.buildDevDashboardPage(ctx, "", emptyMap(), 0, 0, 0, "Fake for logic check"))
            // Proper fix: Render the trash view using the existing factory pattern
            val shell = ctx.shell("Trash", "/messages/trash")
            val messageList = pageFactory.buildMessageList(ctx, query, limit, offset, null, true)
            renderer.render(Page(shell, messageList))
        },
        "/messages" meta {
            summary = "Create message"
            receiving(messageFormLens)
        } bindContract POST to { request: org.http4k.core.Request ->
            val form = messageFormLens(request)
            val author = authorField(form).takeUnless { it.isNullOrBlank() } ?: defaultAuthor
            val content = contentField(form).trim()

            messageService.createServerMessage(author, content)
            Response(Status.FOUND).header("location", request.webContext.url("/"))
        },
        "/messages" / syncIdPath meta {
            summary = "Delete message"
        } bindContract DELETE to { syncId ->
            { request: org.http4k.core.Request ->
                val ctx = request.webContext
                repository.softDelete(syncId)
                renderer.render(pageFactory.buildMessageList(ctx))
            }
        },
        "/messages/restore" / syncIdPath meta {
            summary = "Restore message"
        } bindContract POST to { syncId ->
            { request: org.http4k.core.Request ->
                val ctx = request.webContext
                repository.restore(syncId)
                renderer.render(pageFactory.buildMessageList(ctx))
            }
        }
    )
}
