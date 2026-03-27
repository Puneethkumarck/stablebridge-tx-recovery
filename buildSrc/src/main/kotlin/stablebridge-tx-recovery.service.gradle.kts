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
