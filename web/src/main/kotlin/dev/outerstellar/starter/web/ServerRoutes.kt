package dev.outerstellar.starter.web

import org.http4k.contract.ContractRoute
import org.http4k.routing.RoutingHttpHandler

interface ServerRoutes {
    val routes: List<ContractRoute>
}
