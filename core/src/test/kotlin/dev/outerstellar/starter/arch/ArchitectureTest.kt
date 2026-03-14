package dev.outerstellar.starter.arch

import com.tngtech.archunit.core.importer.ClassFileImporter
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses
import org.junit.jupiter.api.Test

class ArchitectureTest {

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
}
