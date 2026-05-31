package io.github.rygel.outerstellar.platform.plugin

import io.github.rygel.outerstellar.platform.composition.PlatformMode

/**
 * Small test-facing helper for hosted-app authors.
 *
 * Use this from plugin tests when you want to verify the SPI contract without booting the full platform: routes/assets
 * must stay inside manifest ownership, contribution hooks are collected once, and diagnostics are available for
 * assertions.
 */
object HostedAppContract {
    fun collect(
        hostedApp: HostedApp,
        context: HostedAppContext,
        fallbackMode: PlatformMode = hostedApp.mode,
    ): HostedAppContribution = HostedAppContribution.from(hostedApp, fallbackMode, context)

    fun diagnostics(
        hostedApp: HostedApp,
        context: HostedAppContext,
        fallbackMode: PlatformMode = hostedApp.mode,
    ): HostedAppDiagnostics = collect(hostedApp, context, fallbackMode).diagnostics()
}
