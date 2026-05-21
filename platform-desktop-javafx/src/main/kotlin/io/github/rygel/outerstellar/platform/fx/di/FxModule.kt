package io.github.rygel.outerstellar.platform.fx.di

import io.github.rygel.outerstellar.i18n.I18nService
import io.github.rygel.outerstellar.platform.AppConfig
import io.github.rygel.outerstellar.platform.analytics.AnalyticsService
import io.github.rygel.outerstellar.platform.analytics.NoOpAnalyticsService
import io.github.rygel.outerstellar.platform.di.apiClientModule
import io.github.rygel.outerstellar.platform.di.coreModule
import io.github.rygel.outerstellar.platform.di.persistenceModule
import io.github.rygel.outerstellar.platform.fx.app.FxAppConfig
import io.github.rygel.outerstellar.platform.fx.service.FxThemeManager
import io.github.rygel.outerstellar.platform.fx.service.FxTrayNotifier
import io.github.rygel.outerstellar.platform.fx.viewmodel.FxSyncViewModel
import io.github.rygel.outerstellar.platform.persistence.MessageCache
import io.github.rygel.outerstellar.platform.persistence.NoOpMessageCache
import io.github.rygel.outerstellar.platform.sync.engine.module.ModuleNotifier
import org.koin.core.module.Module
import org.koin.dsl.module

val fxModule
    get() = module {
        single { FxAppConfig.fromEnvironment() }
        single { FxThemeManager() }
        single<I18nService> { I18nService.create("messages") }
        single {
            val cfg = get<FxAppConfig>()
            AppConfig(jdbcUrl = cfg.jdbcUrl, jdbcUser = cfg.jdbcUser, jdbcPassword = cfg.jdbcPassword)
        }
        single<MessageCache> { NoOpMessageCache }
        single<AnalyticsService> { NoOpAnalyticsService() }
        single<ModuleNotifier> { FxTrayNotifier }
        single { FxSyncViewModel(get(), get(), get(), get(), get(), get()) }
    }

internal fun fxRuntimeModules(): List<Module> = listOf(fxModule, persistenceModule, coreModule, apiClientModule)
