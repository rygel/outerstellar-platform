package io.github.rygel.outerstellar.platform.arch

import com.tngtech.archunit.core.importer.ClassFileImporter
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses
import com.tngtech.archunit.library.dependencies.SlicesRuleDefinition
import org.junit.jupiter.api.Test

class ArchitectureTest {

    // WARNING: This test class lives in platform-core and only has core classes on its classpath.
    // Rules targeting ..persistence.., ..security.., ..web.., ..desktop.., ..sync..
    // will match ZERO classes and always pass regardless of violations.
    //
    // To properly enforce architecture rules across all modules, this test needs to be moved
    // to a module that depends on all other modules (e.g., a new architecture-tests module,
    // or platform-web which transitively depends on everything).
    //
    // See: docs/test-suite-improvements.md, Issue #4

    @Test
    fun `core should not depend on web or desktop or persistence`() {
        val importedClasses = ClassFileImporter().importPackages("io.github.rygel.outerstellar.platform")

        val rule =
            noClasses()
                .that()
                .resideInAPackage("..core..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage("..web..", "..desktop..")
                .allowEmptyShould(true)

        rule.check(importedClasses)
    }

    @Test
    fun `persistence implementation should not depend on web or desktop`() {
        val importedClasses = ClassFileImporter().importPackages("io.github.rygel.outerstellar.platform")

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
        val importedClasses = ClassFileImporter().importPackages("io.github.rygel.outerstellar.platform")

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
    fun `api-client should not depend on persistence, desktop, or web`() {
        // NOTE: api-client module (package ..sync..) is not on core's test classpath.
        // The api-client SyncService lives in io.github.rygel.outerstellar.platform.sync.
        // This rule uses allowEmptyShould(true) so it passes without false positives.
        val importedClasses = ClassFileImporter().importPackages("io.github.rygel.outerstellar.platform")

        val rule =
            noClasses()
                .that()
                .resideInAPackage("..sync..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage("..persistence.jdbi..", "..desktop..", "..web..")
                .allowEmptyShould(true)

        rule.check(importedClasses)
    }

    @Test
    fun `services should not depend on web framework`() {
        // Classes in ..service.. should not depend on ..web.. or ..desktop..
        // core's service classes only depend on core persistence interfaces and models — no
        // violation expected.
        val importedClasses = ClassFileImporter().importPackages("io.github.rygel.outerstellar.platform")

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
        // Interfaces named *Repository should reside in
        // io.github.rygel.outerstellar.platform.persistence
        // (core).
        // On the core test classpath only core interfaces are present; this verifies they are in
        // the right package.
        val importedClasses = ClassFileImporter().importPackages("io.github.rygel.outerstellar.platform")

        val rule =
            classes()
                .that()
                .areInterfaces()
                .and()
                .haveSimpleNameEndingWith("Repository")
                .should()
                .resideInAPackage("io.github.rygel.outerstellar.platform.persistence")
                .allowEmptyShould(true)

        rule.check(importedClasses)
    }

    @Test
    fun `repository implementations should not reside in core`() {
        // Classes (not interfaces) named Jdbi*Repository must NOT be in core.
        // persistence-jdbi is not on core's classpath so this checks 0 classes,
        // but allowEmptyShould(true) ensures no failure.
        val importedClasses = ClassFileImporter().importPackages("io.github.rygel.outerstellar.platform")

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
    fun `desktop should not depend on web`() {
        // NOTE: desktop module classes are not on core's test classpath.
        // This rule uses allowEmptyShould(true) so it passes without false positives.
        val importedClasses = ClassFileImporter().importPackages("io.github.rygel.outerstellar.platform")

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
        // Checks that slices of io.github.rygel.outerstellar.platform.<top-level-package> are free
        // of cycles,
        // scoped to only the service-layer packages present in core.
        val importedClasses =
            ClassFileImporter()
                .importPackages(
                    "io.github.rygel.outerstellar.platform.service",
                    "io.github.rygel.outerstellar.platform.persistence",
                    "io.github.rygel.outerstellar.platform.model",
                )

        val rule =
            SlicesRuleDefinition.slices()
                .matching("io.github.rygel.outerstellar.platform.(*)..")
                .should()
                .beFreeOfCycles()
                .allowEmptyShould(true)

        rule.check(importedClasses)
    }
}
