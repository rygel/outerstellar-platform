package dev.outerstellar.platform.di

import io.github.rygel.outerstellar.i18n.I18nService
import dev.outerstellar.platform.analytics.AnalyticsService
import dev.outerstellar.platform.analytics.NoOpAnalyticsService
import dev.outerstellar.platform.swing.SwingAppConfig
import dev.outerstellar.platform.swing.SystemTrayNotifier
import dev.outerstellar.platform.swing.analytics.PersistentBatchingAnalyticsService
import dev.outerstellar.platform.swing.viewmodel.SyncViewModel
import java.nio.file.Path
import org.koin.core.qualifier.named
import org.koin.dsl.module

val desktopModule
    get() = module {
        single { SwingAppConfig.fromEnvironment() }
        single(named("jdbcUrl")) { get<SwingAppConfig>().jdbcUrl }
        single(named("serverBaseUrl")) { get<SwingAppConfig>().serverBaseUrl }

        single<AnalyticsService> {
            val cfg = get<SwingAppConfig>()
            if (cfg.analyticsEnabled && cfg.segmentWriteKey.isNotBlank())
                PersistentBatchingAnalyticsService(
                    writeKey = cfg.segmentWriteKey,
                    dataDir = Path.of("./data"),
                    maxFileSizeBytes = cfg.analyticsMaxFileSizeKb * 1024,
                    maxEventAgeDays = cfg.analyticsMaxEventAgeDays,
                )
            else NoOpAnalyticsService()
        }
        single { SyncViewModel(get(), getOrNull(), get(), get(), getOrNull(), get()) }
        single { SystemTrayNotifier(get()) }
        single<I18nService> { I18nService.create("messages") }
    }
