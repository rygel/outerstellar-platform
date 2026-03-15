package dev.outerstellar.starter.di

import com.outerstellar.i18n.I18nService
import dev.outerstellar.starter.analytics.AnalyticsService
import dev.outerstellar.starter.analytics.NoOpAnalyticsService
import dev.outerstellar.starter.swing.SwingAppConfig
import dev.outerstellar.starter.swing.SystemTrayNotifier
import dev.outerstellar.starter.swing.analytics.PersistentBatchingAnalyticsService
import dev.outerstellar.starter.swing.viewmodel.SyncViewModel
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
