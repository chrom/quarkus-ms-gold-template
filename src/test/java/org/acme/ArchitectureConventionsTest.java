package org.acme;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Project-wide architectural conventions that are not tied to a single bounded context.
 *
 * <p>Rules that are scoped to the {@code catalog} slice live in
 * {@link org.acme.catalog.CatalogDomainArchitectureTest}. This file holds invariants that
 * apply to <em>every</em> class in {@code org.acme} (plus test code, when relevant), so they
 * are best expressed here rather than per-slice.
 *
 * <p>The rules are written with the imperative {@link ClassFileImporter} API instead of the
 * {@link com.tngtech.archunit.junit.AnalyzeClasses @AnalyzeClasses} annotation because the
 * latter couples every rule in the class to the same import scope, and the "no H2 anywhere"
 * rule must also scan test classes (which are legitimately allowed to reach for obscure
 * technologies for unrelated reasons, but never for H2 — see ADR 0011).
 */
class ArchitectureConventionsTest {

    private static final JavaClasses MAIN_AND_TESTS = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_JARS)
            .importPackages("org.acme");

    /**
     * The integration test contract (ADR 0011) says: tests run against the same PostgreSQL
     * major version we ship to production, via Testcontainers. H2 is the easy slip — a
     * developer adds {@code quarkus-jdbc-h2} to chase a flaky CI job and suddenly the suite
     * validates against semantics that prod PostgreSQL does not share (identifier casing,
     * array types, {@code information_schema} gaps). This rule fails the build the moment
     * that drift starts, long before it reaches a reviewer.
     */
    @Test
    void noH2AnywhereInProductionOrTestCode() {
        ArchRule rule = noClasses()
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage("org.h2..")
                .as("no class in org.acme may depend on org.h2.. (see ADR 0011)")
                .because("integration tests must run on the same PostgreSQL major version as stage/prod");

        rule.check(MAIN_AND_TESTS);
    }
}
