package io.github.rygel.stellar.persistence

interface StellarTransactionManager {
    fun <T> inTransaction(block: () -> T): T
}
