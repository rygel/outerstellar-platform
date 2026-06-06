package io.github.rygel.outerstellar.platform.arch

import com.tngtech.archunit.core.importer.ClassFileImporter
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses
import com.tngtech.archunit.library.dependencies.SlicesRuleDefinition
import java.nio.file.Paths
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ArchitectureTest {

    // This test lives in platform-web, which depends on:
    //   platform-core, platform-security, platform-persistence-jdbi, platform-sync-client
    // All rules targeting those modules are PROPERLY ENFORCED (no allowEmptyShould).
    //
    // Rules targeting desktop, seed, or javafx still use allowEmptyShould(true)
    // because those modules are NOT on the classpath.

    private val productionClassPaths =
        listOf(
            Paths.get("..", "platform-core", "target", "classes"),
            Paths.get("..", "platform-security", "target", "classes"),
            Paths.get("..", "platform-persistence-jdbi", "target", "classes"),
            Paths.get("..", "platform-sync-client", "target", "classes"),
            Paths.get("target", "classes"),
        )
    private val allClasses = ClassFileImporter().importPaths(productionClassPaths)
    private val webProductionClasses = ClassFileImporter().importPaths(Paths.get("target", "classes"))

    @Test
    fun `core model and services should not depend on web or desktop`() {
        val corePackages = listOf("..model..", "..service..", "..analytics..", "..export..", "..search..")
        for (pkg in corePackages) {
            noClasses()
                .that()
                .resideInAPackage(pkg)
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage("..web..", "..desktop..")
                .check(allClasses)
        }
    }

    @Test
    fun `persistence implementation should not depend on web or desktop`() {
        val rule =
            noClasses()
                .that()
                .resideInAPackage("..persistence..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage("..web..", "..desktop..")

        rule.check(allClasses)
    }

    @Test
    fun `security module production code should not depend on web or desktop`() {
        val rule =
            noClasses()
                .that()
                .resideInAPackage("..security..")
                .and()
                .doNotHaveSimpleName("SecurityIntegrationTest")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage("..web..", "..desktop..")

        rule.check(allClasses)
    }

    @Test
    fun `sync client should not depend on persistence, desktop, or web`() {
        val rule =
            noClasses()
                .that()
                .resideInAPackage("..sync..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage("..persistence.jdbi..", "..desktop..", "..web..")

        rule.check(allClasses)
    }

    @Test
    fun `web production code should not depend on persistence jdbi implementation`() {
        val rule = noClasses().should().dependOnClassesThat().resideInAPackage("..persistence.jdbi..")

        rule.check(webProductionClasses)
    }

    @Test
    fun `services should not depend on web framework`() {
        val rule =
            noClasses()
                .that()
                .resideInAPackage("..service..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage("..web..", "..desktop..")

        rule.check(allClasses)
    }

    @Test
    fun `repository interfaces should reside in core`() {
        val rule =
            classes()
                .that()
                .areInterfaces()
                .and()
                .haveSimpleNameEndingWith("Repository")
                .and()
                .doNotHaveSimpleName("OAuthRepository")
                .should()
                .resideInAPackage("io.github.rygel.outerstellar.platform.persistence")

        rule.check(allClasses)
    }

    @Test
    fun `repository implementations should not reside in core`() {
        val rule =
            noClasses()
                .that()
                .areNotInterfaces()
                .and()
                .haveNameMatching(".*\\.Jdbi.+Repository")
                .should()
                .resideInAPackage("..core..")

        rule.check(allClasses)
    }

    @Test
    fun `desktop should not depend on web`() {
        val rule =
            noClasses()
                .that()
                .resideInAPackage("..desktop..")
                .should()
                .dependOnClassesThat()
                .resideInAPackage("..web..")
                .allowEmptyShould(true)

        rule.check(allClasses)
    }

    @Test
    fun `no cycles in service layer`() {
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

        rule.check(importedClasses)
    }
}
