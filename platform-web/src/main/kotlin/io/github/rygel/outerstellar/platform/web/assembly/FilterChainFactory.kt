package io.github.rygel.outerstellar.platform.web.assembly

import io.github.rygel.outerstellar.platform.AppConfig
import io.github.rygel.outerstellar.platform.di.PlatformPersistence
import io.github.rygel.outerstellar.platform.di.WebComponents
import io.github.rygel.outerstellar.platform.plugin.HostedAppContribution
import io.github.rygel.outerstellar.platform.security.SecurityComponents
import io.github.rygel.outerstellar.platform.web.Filters
import io.github.rygel.outerstellar.platform.web.analyticsPageViewFilter
import io.github.rygel.outerstellar.platform.web.etagCachingFilter
import io.github.rygel.outerstellar.platform.web.rateLimitFilter
import io.github.rygel.outerstellar.platform.web.staticCacheControlFilter
import org.http4k.core.Filter
import org.http4k.core.then

internal class FilterChainFactory(
    private val config: AppConfig,
    private val persistence: PlatformPersistence,
    private val security: SecurityComponents,
    private val web: WebComponents,
    private val pluginContribution: HostedAppContribution,
) {
    fun build(): Filter {
        val userRepository = persistence.userRepository
        val jwtService = security.jwtService
        val pageFactory = web.pageFactory
        val jteRenderer = web.templateRenderer
        val analytics = web.analyticsService
        var chain =
            Filters.correlationId
                .then(Filters.cors(config.corsOrigins))
                .then(etagCachingFilter)
                .then(staticCacheControlFilter)
                .then(Filters.securityHeaders(config.cspPolicy))
                .then(Filters.telemetry)
                .then(
                    rateLimitFilter(
                        trustedProxies = config.trustedProxies.split(",").map { it.trim() }.filter { it.isNotBlank() }
                    )
                )
                .then(Filters.csrfProtection(config.sessionCookieSecure, config.csrfEnabled))
                .then(
                    Filters.devAutoLogin(
                        config.devMode,
                        userRepository,
                        security.sessionService,
                        config.sessionCookieSecure,
                    )
                )
                .then(
                    Filters.stateFilter(
                        config.devDashboardEnabled,
                        userRepository,
                        config.version,
                        jwtService,
                        pluginContribution.options,
                        cookieSecure = config.sessionCookieSecure,
                        appBaseUrl = config.appBaseUrl,
                        bannerProviders = pluginContribution.bannerProviders,
                        sessionService = security.sessionService,
                    )
                )

        for (filter in pluginContribution.filters) {
            chain = chain.then(filter)
        }

        return chain
            .then(analyticsPageViewFilter(analytics))
            .then(Filters.sessionTimeout(config.sessionCookieSecure))
            .then(Filters.securityFilter)
            .then(Filters.requestLogging)
            .then(Filters.serverMetrics)
            .then(Filters.globalErrorHandler(pageFactory, jteRenderer))
    }
}
