package dev.outerstellar.platform.persistence

import org.jooq.DSLContext

class JooqTransactionManager(private val dsl: DSLContext) : TransactionManager {
    override fun <T> inTransaction(block: () -> T): T {
        return dsl.transactionResult { _ -> block() }
    }
}
