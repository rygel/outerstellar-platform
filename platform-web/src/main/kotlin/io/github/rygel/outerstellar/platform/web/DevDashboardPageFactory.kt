package io.github.rygel.outerstellar.platform.web

class DevDashboardPageFactory {

    fun buildDevDashboardPage(
        ctx: WebContext,
        metrics: String,
        cacheStats: Map<String, Any>,
        outboxStats: OutboxStatsViewModel,
        telemetryStatus: String,
    ): Page<DevDashboardPage> {
        val i18n = ctx.i18n
        val shell = ctx.shell(i18n.translate("web.nav.dev"), "/admin/dev")

        return Page(
            shell = shell,
            data =
                DevDashboardPage(
                    metrics = metrics,
                    cacheStats = cacheStats,
                    outboxStats = outboxStats,
                    telemetryStatus = telemetryStatus,
                    badge = i18n.translate("web.dev.badge"),
                    heading = i18n.translate("web.dev.heading"),
                    description = i18n.translate("web.dev.description"),
                    outboxLabel = i18n.translate("web.dev.outbox"),
                    pendingLabel = i18n.translate("web.dev.outbox.pending"),
                    processedLabel = i18n.translate("web.dev.outbox.processed"),
                    failedLabel = i18n.translate("web.dev.outbox.failed"),
                    cacheLabel = i18n.translate("web.dev.cache"),
                    protocolLabel = i18n.translate("web.dev.protocol"),
                    telemetryLabel = i18n.translate("web.dev.telemetry"),
                    metricsLabel = i18n.translate("web.dev.metrics"),
                    triggerSyncLabel = i18n.translate("web.dev.trigger.sync"),
                    protocolDescription = i18n.translate("web.dev.protocol.description"),
                    searchHtmxLabel = i18n.translate("web.dev.protocol.search.htmx"),
                    searchDraculaLabel = i18n.translate("web.dev.protocol.search.dracula"),
                ),
        )
    }
}
