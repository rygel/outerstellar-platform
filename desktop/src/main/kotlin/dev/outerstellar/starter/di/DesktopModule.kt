package dev.outerstellar.starter.di

import com.outerstellar.i18n.I18nService
import dev.outerstellar.starter.swing.SwingAppConfig
import dev.outerstellar.starter.swing.SystemTrayNotifier
import dev.outerstellar.starter.swing.viewmodel.SyncViewModel
import org.koin.core.qualifier.named
import org.koin.dsl.module

val desktopModule = module {
    single { SwingAppConfig.fromEnvironment() }
    single(named("jdbcUrl")) { get<SwingAppConfig>().jdbcUrl }
    single(named("serverBaseUrl")) { get<SwingAppConfig>().serverBaseUrl }
    
    single { SyncViewModel(get(), get(), get(), getOrNull()) }
    single { SystemTrayNotifier(get()) }
    single { I18nService.fromResourceBundle("swing-messages") }
}
