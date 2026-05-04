package io.github.rygel.outerstellar.platform.di

import io.github.rygel.outerstellar.platform.AppConfig
import io.github.rygel.outerstellar.platform.security.securityModule
import kotlin.test.Test
import org.koin.core.annotation.KoinExperimentalAPI
import org.koin.dsl.module
import org.koin.test.KoinTest
import org.koin.test.check.checkModules
import org.testcontainers.containers.PostgreSQLContainer

@OptIn(KoinExperimentalAPI::class)
class KoinModuleTest : KoinTest {

    companion object {
        private val container =
            PostgreSQLContainer<Nothing>("postgres:18").apply {
                withDatabaseName("outerstellar")
                withUsername("outerstellar")
                withPassword("outerstellar")
                start()
            }
    }

    @Test
    fun `web application modules should be valid`() {
        checkModules {
            modules(
                module {
                    single {
                        AppConfig(
                            jdbcUrl = container.jdbcUrl,
                            jdbcUser = container.username,
                            jdbcPassword = container.password,
                        )
                    }
                },
                persistenceModule,
                coreModule,
                securityModule,
                webModule,
            )
        }
    }
}
