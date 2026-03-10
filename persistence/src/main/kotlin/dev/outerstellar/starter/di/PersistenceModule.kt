package dev.outerstellar.starter.di

import dev.outerstellar.starter.infra.createDataSource
import dev.outerstellar.starter.persistence.*
import dev.outerstellar.starter.security.UserRepository
import io.micrometer.core.instrument.Metrics
import org.jooq.DSLContext
import org.jooq.SQLDialect
import org.jooq.impl.DSL
import org.koin.core.qualifier.named
import org.koin.dsl.module
import javax.sql.DataSource

val persistenceModule = module {
    single<DataSource> { 
        val config = get<dev.outerstellar.starter.AppConfig>()
        createDataSource(config.jdbcUrl, "sa", "") 
    }
    
    single(named("primaryDsl")) { 
        DSL.using(get<DataSource>(), SQLDialect.H2).also {
            Metrics.globalRegistry.gauge("database.connections.active", 1)
        }
    }
    
    single(named("replicaDsl")) { get<DSLContext>(named("primaryDsl")) }

    single<MessageRepository> { 
        JooqMessageRepository(
            primaryDsl = get(named("primaryDsl")), 
            replicaDsl = get(named("replicaDsl"))
        ) 
    }
    single<UserRepository> { 
        JooqUserRepository(get(named("primaryDsl"))) 
    }
    single<OutboxRepository> { 
        JooqOutboxRepository(
            primaryDsl = get(named("primaryDsl")), 
            replicaDsl = get(named("replicaDsl"))
        ) 
    }
    single<TransactionManager> { JooqTransactionManager(get(named("primaryDsl"))) }
}
