package dev.outerstellar.starter.di

import dev.outerstellar.starter.swing.SwingAppConfig
import org.koin.dsl.module

val desktopModule = module {
    single { SwingAppConfig.fromEnvironment() }
    single { get<SwingAppConfig>().jdbcUrl }
    single { get<SwingAppConfig>().serverBaseUrl }
}
