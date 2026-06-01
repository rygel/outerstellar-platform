package io.github.rygel.outerstellar.platform

import kotlin.test.Test
import kotlin.test.assertEquals

class ExtensionMigrationsTest {
    @Test
    fun `migration names are defensively copied`() {
        val names = mutableListOf("V1__init")
        val migrations = ExtensionMigrations(location = "classpath:db/migration/reports", migrationNames = names)

        names += "V2__seed"

        assertEquals(listOf("V1__init"), migrations.migrationNames)
    }
}
