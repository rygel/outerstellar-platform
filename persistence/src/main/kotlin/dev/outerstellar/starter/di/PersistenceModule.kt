package dev.outerstellar.starter.di

import dev.outerstellar.starter.infra.createDataSource
import dev.outerstellar.starter.persistence.JooqMessageRepository
import dev.outerstellar.starter.persistence.MessageRepository
import org.jooq.DSLContext
import org.jooq.SQLDialect
import org.jooq.impl.DSL
import org.koin.dsl.module
import javax.sql.DataSource

val persistenceModule = module {
    single<DataSource> {
        // jdbcUrl is expected to be provided via a property or another single
        createDataSource(get(), "sa", "")
    }
    single<DSLContext> { DSL.using(get<DataSource>(), SQLDialect.POSTGRES) }
    single<MessageRepository> { JooqMessageRepository(get()) }
}
