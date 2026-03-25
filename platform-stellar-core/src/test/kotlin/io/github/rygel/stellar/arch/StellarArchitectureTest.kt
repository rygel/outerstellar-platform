package io.github.rygel.stellar.arch

import com.tngtech.archunit.core.importer.ClassFileImporter
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses
import com.tngtech.archunit.library.dependencies.SlicesRuleDefinition
import io.github.rygel.stellar.model.StellarException
import org.junit.jupiter.api.Test

class StellarArchitectureTest {

    // NOTE: The core module test classpath only contains classes from the `stellar-core` module itself.
    // Other modules (persistence-jdbi, web, seed) are NOT on this classpath.
    // All rules use allowEmptyShould(true) to avoid false failures for absent packages.

    private val importedClasses = ClassFileImporter().importPackages("io.github.rygel.stellar")

    @Test
    fun `model should not depend on service, web, persistence, or seed`() {
        val rule =
            noClasses()
                .that()
                .resideInAPackage("..model..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage("..service..", "..web..", "..persistence..", "..seed..")
                .allowEmptyShould(true)

        rule.check(importedClasses)
    }

    @Test
    fun `services should not depend on web or seed`() {
        val rule =
            noClasses()
                .that()
                .resideInAPackage("..service..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage("..web..", "..seed..")
                .allowEmptyShould(true)

        rule.check(importedClasses)
    }

    @Test
    fun `services should not depend on JDBI persistence implementations`() {
        val rule =
            noClasses()
                .that()
                .resideInAPackage("..service..")
                .should()
                .dependOnClassesThat()
                .haveNameMatching(".*\\.Jdbi.+Repository")
                .allowEmptyShould(true)

        rule.check(importedClasses)
    }

    @Test
    fun `repository interfaces should reside in stellar persistence package`() {
        val rule =
            classes()
                .that()
                .areInterfaces()
                .and()
                .haveSimpleNameEndingWith("Repository")
                .should()
                .resideInAPackage("io.github.rygel.stellar.persistence")
                .allowEmptyShould(true)

        rule.check(importedClasses)
    }

    @Test
    fun `JDBI repository implementations should not reside in core`() {
        // JDBI implementations are in the persistence module, not on core's classpath.
        // This rule verifies no implementation class leaked into core.
        val rule =
            noClasses()
                .that()
                .areNotInterfaces()
                .and()
                .haveNameMatching(".*\\.Jdbi.+Repository")
                .should()
                .resideInAPackage("..core..")
                .allowEmptyShould(true)

        rule.check(importedClasses)
    }

    @Test
    fun `seed should not depend on web`() {
        val rule =
            noClasses()
                .that()
                .resideInAPackage("..seed..")
                .should()
                .dependOnClassesThat()
                .resideInAPackage("..web..")
                .allowEmptyShould(true)

        rule.check(importedClasses)
    }

    @Test
    fun `no cycles in service layer`() {
        val rule =
            SlicesRuleDefinition.slices()
                .matching("io.github.rygel.stellar.(*)..")
                .should()
                .beFreeOfCycles()
                .allowEmptyShould(true)

        rule.check(importedClasses)
    }

    @Test
    fun `models should not depend on external frameworks`() {
        val rule =
            noClasses()
                .that()
                .resideInAPackage("..model..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage("org.http4k..", "org.jdbi..", "org.koin..")
                .allowEmptyShould(true)

        rule.check(importedClasses)
    }

    @Test
    fun `exceptions should extend StellarException`() {
        val rule =
            classes()
                .that()
                .resideInAPackage("..model..")
                .and()
                .areAssignableTo(RuntimeException::class.java)
                .and()
                .doNotHaveFullyQualifiedName(StellarException::class.java.name)
                .should()
                .beAssignableTo(StellarException::class.java)
                .allowEmptyShould(true)

        rule.check(importedClasses)
    }
}
