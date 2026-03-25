package io.github.rygel.stellar.web

import io.github.rygel.outerstellar.platform.web.PlatformPlugin
import io.github.rygel.outerstellar.platform.web.PluginContext
import io.github.rygel.outerstellar.platform.web.PluginNavItem
import io.github.rygel.stellar.di.stellarCoreModule
import io.github.rygel.stellar.di.stellarPersistenceModule
import org.http4k.contract.ContractRoute
import org.http4k.core.Filter
import org.koin.core.module.Module

class StellarPlugin : PlatformPlugin {
    override val id = "stellar"
    override val appLabel = "Stellar"
    override val migrationLocation = "classpath:db/migration/stellar"
    override val migrationHistoryTable = "flyway_stellar_history"

    override val excludeDefaultRoutes: Set<String> = setOf("/", "/contacts", "/messages")

    override val navItems: List<PluginNavItem> =
        listOf(
            PluginNavItem("Home", "/", "ri-home-5-line", "/"),
            PluginNavItem("Popular", "/popular", "ri-fire-line", "/popular"),
            PluginNavItem("Communities", "/communities", "ri-group-2-line", "/communities"),
            PluginNavItem("Search", "/search", "ri-search-line", "/search"),
        )

    override fun routes(context: PluginContext): List<ContractRoute> =
        FeedRoutes(context).routes +
            CommunityRoutes(context).routes +
            PostRoutes(context).routes +
            VoteRoutes(context).routes +
            UserProfileRoutes(context).routes +
            SearchRoutes(context).routes +
            FlairRoutes(context).routes +
            ModerationRoutes(context).routes

    override fun filters(context: PluginContext): List<Filter> = emptyList()

    override fun koinModules(): List<Module> = listOf(stellarCoreModule, stellarPersistenceModule)
}
