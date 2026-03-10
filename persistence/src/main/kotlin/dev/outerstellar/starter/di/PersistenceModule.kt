package dev.outerstellar.starter.di

import dev.outerstellar.starter.infra.createDataSource
import dev.outerstellar.starter.persistence.*
import io.micrometer.core.instrument.Metrics
import org.jooq.DSLContext
import org.jooq.SQLDialect
import org.jooq.impl.DSL
import org.koin.core.qualifier.named
import org.koin.dsl.module
import javax.sql.DataSource

val persistenceModule = module {
    single<MessageCache> { CaffeineMessageCache(Metrics.globalRegistry) }
    single<DataSource> {
        createDataSource(get(named("jdbcUrl")), "sa", "")
    }
    single<DSLContext>(named("primaryDsl")) { DSL.using(get<DataSource>(), SQLDialect.POSTGRES) }
    single<DSLContext>(named("replicaDsl")) { DSL.using(get<DataSource>(), SQLDialect.POSTGRES) }
    
    single<MessageRepository> { 
        JooqMessageRepository(
            primaryDsl = get(named("primaryDsl")), 
            replicaDsl = get(named("replicaDsl"))
        ) 
    }
    single<OutboxRepository> { 
        JooqOutboxRepository(
            primaryDsl = get(named("primaryDsl")), 
            replicaDsl = get(named("replicaDsl"))
        ) 
    }
    single<TransactionManager> { JooqTransactionManager(get(named("primaryDsl"))) }
}
