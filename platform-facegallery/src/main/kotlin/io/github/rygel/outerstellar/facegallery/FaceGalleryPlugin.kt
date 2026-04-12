package io.github.rygel.outerstellar.facegallery

import io.github.rygel.outerstellar.facegallery.api.config.FaceGalleryConfig
import io.github.rygel.outerstellar.facegallery.routes.FaceGalleryRoutes
import io.github.rygel.outerstellar.platform.web.PlatformPlugin
import io.github.rygel.outerstellar.platform.web.PluginContext
import io.github.rygel.outerstellar.platform.web.PluginNavItem
import org.http4k.contract.ContractRoute
import org.http4k.core.Filter
import org.koin.core.module.Module

class FaceGalleryPlugin(private val config: FaceGalleryConfig = FaceGalleryConfig()) : PlatformPlugin {
    override val id = "facegallery"
    override val appLabel = "Face Gallery"
    override val migrationLocation: String? = "classpath:db/migration/facegallery"
    override val migrationHistoryTable = "flyway_facegallery_history"

    override val navItems: List<PluginNavItem> =
        listOf(PluginNavItem(label = "Sessions", url = "/sessions", icon = "images"))

    override fun routes(context: PluginContext): List<ContractRoute> {
        return FaceGalleryRoutes(context).routes
    }

    override fun filters(context: PluginContext): List<Filter> = emptyList()

    override fun koinModules(): List<Module> = faceGalleryModules(config)
}
