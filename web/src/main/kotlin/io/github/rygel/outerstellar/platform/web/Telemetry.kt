package io.github.rygel.outerstellar.platform.web

import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.sdk.autoconfigure.AutoConfiguredOpenTelemetrySdk

object Telemetry {
    val openTelemetry: OpenTelemetry by lazy {
        AutoConfiguredOpenTelemetrySdk.initialize().openTelemetrySdk
    }
}
