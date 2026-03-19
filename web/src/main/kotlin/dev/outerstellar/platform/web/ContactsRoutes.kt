package dev.outerstellar.platform.web

import dev.outerstellar.platform.infra.render
import dev.outerstellar.platform.service.ContactService
import org.http4k.contract.bindContract
import org.http4k.contract.div
import org.http4k.contract.meta
import org.http4k.core.Method.GET
import org.http4k.core.Method.POST
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status
import org.http4k.lens.Path
import org.http4k.lens.string
import org.http4k.template.TemplateRenderer

class ContactsRoutes(
    private val pageFactory: WebPageFactory,
    private val renderer: TemplateRenderer,
    private val contactService: ContactService? = null,
) : ServerRoutes {

    private val syncIdPath = Path.string().of("syncId")

    override val routes =
        listOf(
            "/contacts" meta
                {
                    summary = "Contacts page"
                } bindContract
                GET to
                { request: Request ->
                    val ctx = request.webContext
                    val query = request.query("q")
                    val limit = request.query("limit")?.toIntOrNull()?.coerceIn(1, 50) ?: 12
                    val offset = request.query("offset")?.toIntOrNull()?.coerceAtLeast(0) ?: 0
                    renderer.render(pageFactory.buildContactsPage(ctx, query, limit, offset))
                },
            "/contacts/new" meta
                {
                    summary = "Contact create form (HTMX fragment)"
                } bindContract
                GET to
                { request: Request ->
                    renderer.render(pageFactory.buildContactForm(request.webContext))
                },
            "/contacts" meta
                {
                    summary = "Create contact"
                } bindContract
                POST to
                { request: Request ->
                    val ctx = request.webContext
                    val params = request.bodyAsForm()
                    val name =
                        params.findSingle("name")
                            ?: return@to Response(Status.BAD_REQUEST).body("name required")
                    contactService?.createContact(
                        name = name,
                        emails = params.findSingle("emails").orEmpty().splitTrimmed(),
                        phones = params.findSingle("phones").orEmpty().splitTrimmed(),
                        socialMedia = params.findSingle("socialMedia").orEmpty().splitTrimmed(),
                        company = params.findSingle("company").orEmpty(),
                        companyAddress = params.findSingle("companyAddress").orEmpty(),
                        department = params.findSingle("department").orEmpty(),
                    )
                    renderer.render(pageFactory.buildContactsPage(ctx))
                },
            "/contacts" / syncIdPath / "edit" meta
                {
                    summary = "Contact edit form (HTMX fragment)"
                } bindContract
                GET to
                { syncId: String, _ ->
                    { request: Request ->
                        renderer.render(pageFactory.buildContactForm(request.webContext, syncId))
                    }
                },
            "/contacts" / syncIdPath / "update" meta
                {
                    summary = "Update contact"
                } bindContract
                POST to
                { syncId: String, _ ->
                    { request: Request ->
                        val ctx = request.webContext
                        val params = request.bodyAsForm()
                        val existing =
                            contactService?.getContactBySyncId(syncId)
                                ?: return@to Response(Status.NOT_FOUND)
                                    .body("Contact not found: $syncId")
                        val name = params.findSingle("name") ?: existing.name
                        contactService.updateContact(
                            existing.copy(
                                name = name,
                                emails = params.findSingle("emails").orEmpty().splitTrimmed(),
                                phones = params.findSingle("phones").orEmpty().splitTrimmed(),
                                socialMedia =
                                    params.findSingle("socialMedia").orEmpty().splitTrimmed(),
                                company = params.findSingle("company").orEmpty(),
                                companyAddress = params.findSingle("companyAddress").orEmpty(),
                                department = params.findSingle("department").orEmpty(),
                                dirty = true,
                            )
                        )
                        renderer.render(pageFactory.buildContactsPage(ctx))
                    }
                },
            "/contacts" / syncIdPath / "delete" meta
                {
                    summary = "Delete contact"
                } bindContract
                POST to
                { syncId: String, _ ->
                    { _: Request ->
                        contactService?.deleteContact(syncId)
                        Response(Status.OK).body("")
                    }
                },
        )

    private fun Request.bodyAsForm(): List<Pair<String, String>> =
        bodyString().split("&").mapNotNull { pair ->
            val idx = pair.indexOf('=')
            if (idx < 0) null
            else
                java.net.URLDecoder.decode(pair.substring(0, idx), "UTF-8") to
                    java.net.URLDecoder.decode(pair.substring(idx + 1), "UTF-8")
        }

    private fun List<Pair<String, String>>.findSingle(key: String): String? =
        firstOrNull { it.first == key }?.second?.takeIf { it.isNotBlank() }

    private fun String.splitTrimmed(): List<String> =
        split(",").map { it.trim() }.filter { it.isNotEmpty() }
}
