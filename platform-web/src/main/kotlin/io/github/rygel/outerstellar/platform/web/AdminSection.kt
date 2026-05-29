package io.github.rygel.outerstellar.platform.web

import org.http4k.contract.ContractRoute

data class AdminSection(
    val id: String,
    val navLabel: String,
    val navIcon: String,
    val summaryCard: AdminSummaryCard,
    val route: ContractRoute,
)
