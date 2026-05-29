package io.github.rygel.outerstellar.platform.persistence.jdbi

import io.github.rygel.outerstellar.platform.AppConfig
import io.github.rygel.outerstellar.platform.PluginMigrationSource
import io.github.rygel.outerstellar.platform.di.PersistenceBootstrap
import io.github.rygel.outerstellar.platform.di.PlatformPersistence
import io.github.rygel.outerstellar.platform.di.createPersistenceComponents

class JdbiPersistenceBootstrap : PersistenceBootstrap {
    override fun create(config: AppConfig, pluginMigrationSource: PluginMigrationSource?): PlatformPersistence =
        createPersistenceComponents(config = config, pluginMigrationSource = pluginMigrationSource)
}
