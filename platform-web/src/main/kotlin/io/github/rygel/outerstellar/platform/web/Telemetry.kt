package io.github.rygel.outerstellar.platform.web

import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.api.baggage.propagation.W3CBaggagePropagator
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator
import io.opentelemetry.context.propagation.ContextPropagators
import io.opentelemetry.context.propagation.TextMapPropagator
import io.opentelemetry.exporter.logging.LoggingSpanExporter
import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter
import io.opentelemetry.sdk.OpenTelemetrySdk
import io.opentelemetry.sdk.resources.Resource
import io.opentelemetry.sdk.trace.SdkTracerProvider
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor
import io.opentelemetry.sdk.trace.export.SpanExporter
import io.opentelemetry.semconv.ServiceAttributes

object Telemetry {
    val openTelemetry: OpenTelemetry by lazy { buildOpenTelemetry() }

    private fun buildOpenTelemetry(): OpenTelemetry {
        val exporter = createExporter()
        val tracerProvider =
            SdkTracerProvider.builder()
                .setResource(Resource.builder().put(ServiceAttributes.SERVICE_NAME, "outerstellar-platform").build())
                .addSpanProcessor(
                    if (isOtlpConfigured()) {
                        BatchSpanProcessor.builder(exporter).build()
                    } else {
                        SimpleSpanProcessor.create(exporter)
                    }
                )
                .build()
        return OpenTelemetrySdk.builder()
            .setTracerProvider(tracerProvider)
            .setPropagators(
                ContextPropagators.create(
                    TextMapPropagator.composite(
                        W3CTraceContextPropagator.getInstance(),
                        W3CBaggagePropagator.getInstance(),
                    )
                )
            )
            .build()
    }

    private fun createExporter(): SpanExporter {
        val endpoint = System.getenv("OTEL_EXPORTER_OTLP_ENDPOINT")
        if (!endpoint.isNullOrBlank()) {
            return OtlpGrpcSpanExporter.builder().setEndpoint(endpoint).build()
        }
        return LoggingSpanExporter.create()
    }

    private fun isOtlpConfigured(): Boolean = !System.getenv("OTEL_EXPORTER_OTLP_ENDPOINT").isNullOrBlank()
}
