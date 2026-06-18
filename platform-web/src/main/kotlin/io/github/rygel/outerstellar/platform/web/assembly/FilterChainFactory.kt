package io.github.rygel.outerstellar.platform.web.assembly

import io.github.rygel.outerstellar.platform.AppConfig
import io.github.rygel.outerstellar.platform.di.PlatformPersistence
import io.github.rygel.outerstellar.platform.di.WebComponents
import io.github.rygel.outerstellar.platform.extension.ExtensionContribution
import io.github.rygel.outerstellar.platform.security.SecurityComponents
import io.github.rygel.outerstellar.platform.web.Filters
import io.github.rygel.outerstellar.platform.web.ShellConfig
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
    private val extensionContribution: ExtensionContribution,
) {
    fun build(): Filter {
        val userRepository = persistence.userRepository
        val jwtService = security.jwtService
        val jteRenderer = web.runtime.templateRenderer
        val analytics = web.runtime.analyticsService
        var chain =
            Filters.correlationId
                .then(Filters.cors(config.corsOrigins, config.securityHeaders))
                .then(Filters.maxBodySize(config.maxRequestBodyBytes))
                .then(etagCachingFilter)
                .then(staticCacheControlFilter)
                .then(Filters.securityHeaders(config.cspPolicy, config.securityHeaders))
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
                        config.sessionTimeoutMinutes,
                    )
                )
                .then(
                    Filters.stateFilter(
                        config.devDashboardEnabled,
                        userRepository,
                        config.version,
                        jwtService,
                        ShellConfig.from(
                            extensionContribution,
                            appBaseUrl = config.appBaseUrl,
                            sidebarFactory = web.pages.sidebarFactory,
                        ),
                        cookieSecure = config.sessionCookieSecure,
                        sessionService = security.sessionService,
                    )
                )

        for (filter in extensionContribution.filters) {
            chain = chain.then(filter)
        }

        return chain
            .then(analyticsPageViewFilter(analytics))
            .then(Filters.sessionTimeout(config.sessionCookieSecure))
            .then(Filters.securityFilter)
            .then(Filters.requestLogging)
            .then(Filters.serverMetrics)
            .then(Filters.globalErrorHandler(web.pages.errorPageFactory, jteRenderer))
    }
}
