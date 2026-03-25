package io.github.rygel.stellar.persistence

import org.jdbi.v3.core.Jdbi

class JdbiStellarTransactionManager(private val jdbi: Jdbi) : StellarTransactionManager {
    override fun <T> inTransaction(block: () -> T): T = jdbi.inTransaction<T, Exception> { block() }
}
