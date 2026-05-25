package io.github.rygel.outerstellar.platform.fx

import io.github.rygel.outerstellar.i18n.I18nService
import io.github.rygel.outerstellar.platform.fx.app.FxAppConfig
import io.github.rygel.outerstellar.platform.fx.service.FxThemeManager
import io.github.rygel.outerstellar.platform.fx.viewmodel.FxSyncViewModel
import io.github.rygel.outerstellar.platform.sync.engine.module.AuthModule

object FxAppContext {
    lateinit var viewModel: FxSyncViewModel
    lateinit var themeManager: FxThemeManager
    lateinit var i18nService: I18nService
    lateinit var appConfig: FxAppConfig
    lateinit var authModule: AuthModule
}
