package dev.outerstellar.starter.di

import org.junit.jupiter.api.Test
import org.koin.core.qualifier.named
import org.koin.dsl.module
import org.koin.test.KoinTest
import org.koin.test.check.checkModules

class KoinModuleTest : KoinTest {

    @Test
    fun `persistence modules should be valid`() {
        checkModules {
            modules(persistenceModule, module {
                single(named("jdbcUrl")) { "jdbc:h2:mem:test;MODE=PostgreSQL" }
            })
        }
    }
}
