package dev.outerstellar.starter.persistence

import org.jdbi.v3.core.Jdbi

class JdbiTransactionManager(private val jdbi: Jdbi) : TransactionManager {
    override fun <T> inTransaction(block: () -> T): T {
        return jdbi.inTransaction<T, Exception> { _ -> block() }
    }
}
