package com.stablebridge.txrecovery;

import static com.tngtech.archunit.base.DescribedPredicate.not;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAnyPackage;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.simpleNameEndingWith;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noFields;
import static com.tngtech.archunit.library.GeneralCodingRules.ACCESS_STANDARD_STREAMS;
import static com.tngtech.archunit.library.GeneralCodingRules.BE_ANNOTATED_WITH_AN_INJECTION_ANNOTATION;
import static com.tngtech.archunit.library.GeneralCodingRules.NO_CLASSES_SHOULD_THROW_GENERIC_EXCEPTIONS;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

@AnalyzeClasses(
        packages = "com.stablebridge.txrecovery",
        importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    @ArchTest
    static final ArchRule domainMustNotDependOnInfrastructure = noClasses()
            .that()
            .resideInAPackage("..domain..")
            .should()
            .dependOnClassesThat()
            .resideInAPackage("..infrastructure..");

    @ArchTest
    static final ArchRule domainMustNotDependOnApplication = noClasses()
            .that()
            .resideInAPackage("..domain..")
            .should()
            .dependOnClassesThat()
            .resideInAPackage("..application..");

    @ArchTest
    static final ArchRule domainMustNotImportSpring = noClasses()
            .that()
            .resideInAPackage("..domain..")
            .should()
            .dependOnClassesThat(
                    resideInAnyPackage("org.springframework..")
                            .and(not(resideInAnyPackage(
                                    "org.springframework.stereotype..",
                                    "org.springframework.transaction.annotation.."))));

    @ArchTest
    static final ArchRule domainMustNotImportJakartaPersistence = noClasses()
            .that()
            .resideInAPackage("..domain..")
            .should()
            .dependOnClassesThat()
            .resideInAPackage("jakarta.persistence..");

    @ArchTest
    static final ArchRule applicationMustNotDependOnInfrastructure = noClasses()
            .that()
            .resideInAPackage("..application..")
            .should()
            .dependOnClassesThat()
            .resideInAPackage("..infrastructure..");

    @ArchTest
    static final ArchRule infrastructureMustNotDependOnApplication = noClasses()
            .that()
            .resideInAPackage("..infrastructure..")
            .should()
            .dependOnClassesThat()
            .resideInAPackage("..application..");

    @ArchTest
    static final ArchRule noClassesShouldAccessStandardStreams = noClasses()
            .that()
            .doNotHaveSimpleName("KeyGenerator")
            .should(ACCESS_STANDARD_STREAMS);

    @ArchTest
    static final ArchRule noClassesShouldUseFieldInjection = noFields()
            .that()
            .areDeclaredInClassesThat(
                    resideInAnyPackage("..application..", "..domain..", "..infrastructure..")
                            .and(not(simpleNameEndingWith("Test")))
                            .and(not(simpleNameEndingWith("TestBase"))))
            .should(BE_ANNOTATED_WITH_AN_INJECTION_ANNOTATION);

    @ArchTest
    static final ArchRule noClassesShouldThrowGenericExceptions = NO_CLASSES_SHOULD_THROW_GENERIC_EXCEPTIONS;
}
