package io.github.rygel.outerstellar.platform.web

import io.github.rygel.outerstellar.platform.infra.render
import io.github.rygel.outerstellar.platform.security.ApiKeyService
import org.http4k.contract.ContractRoute
import org.http4k.contract.bindContract
import org.http4k.contract.div
import org.http4k.contract.meta
import org.http4k.core.Method.GET
import org.http4k.core.Method.POST
import org.http4k.core.Response
import org.http4k.core.Status
import org.http4k.core.body.form
import org.http4k.lens.Path
import org.http4k.lens.long
import org.http4k.template.TemplateRenderer

class ApiKeyRoutes(
    private val pageFactory: WebPageFactory,
    private val renderer: TemplateRenderer,
    private val apiKeyService: ApiKeyService,
) : ServerRoutes {
    private val apiKeyIdPath = Path.long().of("id")

    override val routes: List<ContractRoute> =
        listOf(
            "/auth/api-keys" meta
                {
                    summary = "API keys management page"
                } bindContract
                GET to
                { request: org.http4k.core.Request ->
                    val ctx = request.requestContext
                    val shellRenderer = request.shellRenderer
                    renderer.render(pageFactory.buildApiKeysPage(ctx, shellRenderer))
                },
            "/auth/api-keys/create" meta
                {
                    summary = "Create a new API key"
                } bindContract
                POST to
                { request: org.http4k.core.Request ->
                    val ctx = request.requestContext
                    val shellRenderer = request.shellRenderer
                    val user = ctx.user!!
                    val name = request.form("name").orEmpty()
                    if (name.isBlank()) {
                        renderer.render(pageFactory.buildApiKeysPage(ctx, shellRenderer))
                    } else {
                        val result = apiKeyService.createApiKey(user.id, name)
                        renderer.render(
                            pageFactory.buildApiKeysPage(
                                ctx,
                                shellRenderer,
                                newKey = result.key,
                                newKeyName = result.name,
                            )
                        )
                    }
                },
            "/auth/api-keys" / apiKeyIdPath / "delete" meta
                {
                    summary = "Delete an API key"
                } bindContract
                POST to
                { id, _ ->
                    { request: org.http4k.core.Request ->
                        val shellRenderer = request.shellRenderer
                        val user = request.requestContext.user!!
                        apiKeyService.deleteApiKey(user.id, id)
                        Response(Status.FOUND).header("location", shellRenderer.url("/auth/api-keys"))
                    }
                },
        )
}
