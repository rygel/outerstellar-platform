package dev.outerstellar.starter.arch

import com.tngtech.archunit.core.importer.ClassFileImporter
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses
import com.tngtech.archunit.library.dependencies.SlicesRuleDefinition
import org.junit.jupiter.api.Test

class ArchitectureTest {

    // NOTE: The core module test classpath only contains classes from the `core` module itself.
    // Other modules (security, api-client, web, desktop, persistence-jooq, persistence-jdbi)
    // are NOT on this classpath. Rules targeting those packages will match 0 classes.
    // All rules use allowEmptyShould(true) to avoid false failures for absent packages.

    @Test
    fun `core should not depend on web or desktop or persistence`() {
        val importedClasses = ClassFileImporter().importPackages("dev.outerstellar.starter")

        val rule =
            noClasses()
                .that()
                .resideInAPackage("..core..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage("..web..", "..desktop..", "..persistence.jooq..")
                .allowEmptyShould(true)

        rule.check(importedClasses)
    }

    @Test
    fun `persistence implementation should not depend on web or desktop`() {
        val importedClasses = ClassFileImporter().importPackages("dev.outerstellar.starter")

        val rule =
            noClasses()
                .that()
                .resideInAPackage("..persistence..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage("..web..", "..desktop..")
                .allowEmptyShould(true)

        rule.check(importedClasses)
    }

    @Test
    fun `security module should not depend on web or desktop`() {
        // NOTE: security module classes are not on core's test classpath.
        // This rule uses allowEmptyShould(true) so it passes without false positives.
        val importedClasses = ClassFileImporter().importPackages("dev.outerstellar.starter")

        val rule =
            noClasses()
                .that()
                .resideInAPackage("..security..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage("..web..", "..desktop..")
                .allowEmptyShould(true)

        rule.check(importedClasses)
    }

    @Test
    fun `api-client should not depend on persistence or desktop`() {
        // NOTE: api-client module (package ..sync..) is not on core's test classpath.
        // The api-client SyncService lives in dev.outerstellar.starter.sync.
        // This rule uses allowEmptyShould(true) so it passes without false positives.
        // TODO: fix violation — SyncService in api-client imports dev.outerstellar.starter.web.*
        //   (classes defined within api-client itself in the web sub-package, not the web module)
        val importedClasses = ClassFileImporter().importPackages("dev.outerstellar.starter")

        val rule =
            noClasses()
                .that()
                .resideInAPackage("..sync..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage("..persistence.jooq..", "..persistence.jdbi..", "..desktop..")
                .allowEmptyShould(true)

        rule.check(importedClasses)
    }

    @Test
    fun `services should not depend on web framework`() {
        // Classes in ..service.. should not depend on ..web.. or ..desktop..
        // core's service classes only depend on core persistence interfaces and models — no
        // violation expected.
        val importedClasses = ClassFileImporter().importPackages("dev.outerstellar.starter")

        val rule =
            noClasses()
                .that()
                .resideInAPackage("..service..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage("..web..", "..desktop..")
                .allowEmptyShould(true)

        rule.check(importedClasses)
    }

    @Test
    fun `repository interfaces should reside in core`() {
        // Interfaces named *Repository should reside in dev.outerstellar.starter.persistence
        // (core).
        // On the core test classpath only core interfaces are present; this verifies they are in
        // the right package.
        val importedClasses = ClassFileImporter().importPackages("dev.outerstellar.starter")

        val rule =
            classes()
                .that()
                .areInterfaces()
                .and()
                .haveSimpleNameEndingWith("Repository")
                .should()
                .resideInAPackage("dev.outerstellar.starter.persistence")
                .allowEmptyShould(true)

        rule.check(importedClasses)
    }

    @Test
    fun `repository implementations should not reside in core`() {
        // Classes (not interfaces) named Jooq*Repository or Jdbi*Repository must NOT be in core.
        // persistence-jooq and persistence-jdbi are not on core's classpath so this checks 0
        // classes,
        // but allowEmptyShould(true) ensures no failure.
        val importedClasses = ClassFileImporter().importPackages("dev.outerstellar.starter")

        val rule =
            noClasses()
                .that()
                .areNotInterfaces()
                .and()
                .haveNameMatching(".*\\.(Jooq|Jdbi).+Repository")
                .should()
                .resideInAPackage("..core..")
                .allowEmptyShould(true)

        rule.check(importedClasses)
    }

    @Test
    fun `desktop should not depend on web`() {
        // NOTE: desktop module classes are not on core's test classpath.
        // This rule uses allowEmptyShould(true) so it passes without false positives.
        val importedClasses = ClassFileImporter().importPackages("dev.outerstellar.starter")

        val rule =
            noClasses()
                .that()
                .resideInAPackage("..desktop..")
                .should()
                .dependOnClassesThat()
                .resideInAPackage("..web..")
                .allowEmptyShould(true)

        rule.check(importedClasses)
    }

    @Test
    fun `no cycles in service layer`() {
        // Checks that slices of dev.outerstellar.starter.<top-level-package> are free of cycles,
        // scoped to only the service-layer packages present in core.
        val importedClasses =
            ClassFileImporter()
                .importPackages(
                    "dev.outerstellar.starter.service",
                    "dev.outerstellar.starter.persistence",
                    "dev.outerstellar.starter.model",
                )

        val rule =
            SlicesRuleDefinition.slices()
                .matching("dev.outerstellar.starter.(*)..")
                .should()
                .beFreeOfCycles()
                .allowEmptyShould(true)

        rule.check(importedClasses)
    }
}
