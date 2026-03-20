package io.github.rygel.outerstellar.platform.web

import org.http4k.contract.ContractRoute

interface ServerRoutes {
    val routes: List<ContractRoute>
}
