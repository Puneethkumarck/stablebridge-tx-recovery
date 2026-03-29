plugins {
    java
    `java-test-fixtures`
    id("org.springframework.boot")
    id("io.spring.dependency-management")
    id("com.diffplug.spotless")
}

group = "com.stablebridge"
version = "0.1.0-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

repositories {
    mavenCentral()
}

spotless {
    java {
        removeUnusedImports()
        importOrder("java|javax", "jakarta", "org", "com", "")
        trimTrailingWhitespace()
        endWithNewline()
    }
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
    options.compilerArgs.addAll(listOf(
        "-parameters",
        "-Amapstruct.defaultComponentModel=spring",
        "-Amapstruct.defaultInjectionStrategy=constructor",
        "-Amapstruct.unmappedTargetPolicy=ERROR"
    ))
}

tasks.withType<Test> {
    useJUnitPlatform()
}

val integrationTest by sourceSets.creating {
    java.srcDir("src/integration-test/java")
    resources.srcDir("src/integration-test/resources")
    resources.srcDir("src/test/resources")
    compileClasspath += sourceSets.main.get().output + sourceSets.testFixtures.get().output
    runtimeClasspath += sourceSets.main.get().output + sourceSets.testFixtures.get().output
}

configurations[integrationTest.implementationConfigurationName].extendsFrom(configurations.testImplementation.get())
configurations[integrationTest.runtimeOnlyConfigurationName].extendsFrom(configurations.testRuntimeOnly.get())
configurations[integrationTest.compileOnlyConfigurationName].extendsFrom(configurations.testCompileOnly.get())
configurations[integrationTest.annotationProcessorConfigurationName].extendsFrom(configurations.testAnnotationProcessor.get())

val integrationTestTask = tasks.register<Test>("integrationTest") {
    description = "Runs integration tests."
    group = "verification"
    useJUnitPlatform()
    testClassesDirs = integrationTest.output.classesDirs
    classpath = integrationTest.runtimeClasspath
    shouldRunAfter(tasks.test)
}

val businessTest by sourceSets.creating {
    java.srcDir("src/business-test/java")
    resources.srcDir("src/business-test/resources")
    resources.srcDir("src/test/resources")
    compileClasspath += sourceSets.main.get().output + sourceSets.testFixtures.get().output
    runtimeClasspath += sourceSets.main.get().output + sourceSets.testFixtures.get().output
}

configurations[businessTest.implementationConfigurationName].extendsFrom(configurations.testImplementation.get())
configurations[businessTest.runtimeOnlyConfigurationName].extendsFrom(configurations.testRuntimeOnly.get())
configurations[businessTest.compileOnlyConfigurationName].extendsFrom(configurations.testCompileOnly.get())
configurations[businessTest.annotationProcessorConfigurationName].extendsFrom(configurations.testAnnotationProcessor.get())

val businessTestTask = tasks.register<Test>("businessTest") {
    description = "Runs business tests."
    group = "verification"
    useJUnitPlatform()
    testClassesDirs = businessTest.output.classesDirs
    classpath = businessTest.runtimeClasspath
    shouldRunAfter(integrationTestTask)
}

tasks.check {
    dependsOn(integrationTestTask, businessTestTask)
}
