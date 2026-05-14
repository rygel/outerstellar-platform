package io.github.rygel.outerstellar.platform.web

import io.github.rygel.outerstellar.platform.export.ExportProvider
import org.http4k.contract.bindContract
import org.http4k.contract.meta
import org.http4k.core.Method.GET
import org.http4k.core.Response
import org.http4k.core.Status

class ExportRoutes(private val providers: List<ExportProvider>) : ServerRoutes {

    override val routes = providers.flatMap { provider ->
        listOf(
            "/api/v1/export/${provider.entityType}/csv" meta
                {
                    summary = "Export ${provider.displayName} as CSV"
                } bindContract
                GET to
                { _: org.http4k.core.Request ->
                    Response(Status.OK)
                        .header("Content-Type", "text/csv; charset=utf-8")
                        .header("Content-Disposition", "attachment; filename=\"${provider.entityType}.csv\"")
                        .body(provider.exportCsv())
                }
        )
    }
}
