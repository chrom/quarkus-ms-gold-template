package org.acme.catalog;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

/**
 * Guards hexagonal boundaries: the domain model must only depend on JDK and sibling domain packages.
 */
@AnalyzeClasses(packages = "org.acme.catalog..", importOptions = ImportOption.DoNotIncludeTests.class)
class CatalogDomainArchitectureTest {

    /**
     * In ArchUnit, {@code org.acme.catalog..} imports that package and all subpackages; {@code org.acme.catalog}
     * alone would match no classes (only nested packages).
     */
    @ArchTest
    static final ArchRule domainOnlyDependsOnJdkAndSelf =
            classes()
                    .that()
                    .resideInAnyPackage("org.acme.catalog.domain..")
                    .should()
                    .onlyDependOnClassesThat()
                    .resideInAnyPackage("java..", "org.acme.catalog.domain..");
}
