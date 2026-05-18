package io.github.rygel.outerstellar.platform.di

import io.github.rygel.outerstellar.i18n.I18nService
import io.github.rygel.outerstellar.platform.analytics.AnalyticsService
import io.github.rygel.outerstellar.platform.analytics.NoOpAnalyticsService
import io.github.rygel.outerstellar.platform.swing.SwingAppConfig
import io.github.rygel.outerstellar.platform.swing.SystemTrayNotifier
import io.github.rygel.outerstellar.platform.swing.analytics.PersistentBatchingAnalyticsService
import io.github.rygel.outerstellar.platform.swing.viewmodel.SyncViewModel
import io.github.rygel.outerstellar.platform.sync.engine.DesktopAppConfig
import io.github.rygel.outerstellar.platform.sync.engine.DesktopSyncEngine
import io.github.rygel.outerstellar.platform.sync.engine.SyncEngine
import java.nio.file.Path
import org.koin.core.qualifier.named
import org.koin.dsl.module

val desktopModule
    get() = module {
        single { DesktopAppConfig.fromEnvironment() }
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
        single<SyncEngine> { DesktopSyncEngine(get(), get(), getOrNull(), get(), getOrNull(), getOrNull()) }
        single { SyncViewModel(get(), get(), getOrNull()) }
        single { SystemTrayNotifier(get()) }
        single<I18nService> { I18nService.create("messages") }
    }
