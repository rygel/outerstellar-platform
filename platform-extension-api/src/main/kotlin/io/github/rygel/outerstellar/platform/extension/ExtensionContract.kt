package io.github.rygel.outerstellar.platform.extension

import io.github.rygel.outerstellar.platform.composition.PlatformMode

/**
 * Small test-facing helper for extension authors.
 *
 * Use this from extension tests when you want to verify the SPI contract without booting the full platform:
 * routes/assets must stay inside manifest ownership, contribution hooks are collected once, and diagnostics are
 * available for assertions.
 */
object ExtensionContract {
    fun collect(
        hostedApp: PlatformExtension,
        context: ExtensionHostContext,
        fallbackMode: PlatformMode = hostedApp.mode,
    ): ExtensionContribution = ExtensionContribution.from(hostedApp, fallbackMode, context)

    fun diagnostics(
        hostedApp: PlatformExtension,
        context: ExtensionHostContext,
        fallbackMode: PlatformMode = hostedApp.mode,
    ): ExtensionDiagnostics = collect(hostedApp, context, fallbackMode).diagnostics()
}
