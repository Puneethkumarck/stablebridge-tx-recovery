plugins {
    id("stablebridge-tx-recovery.service")
}

dependencies {
    // API module
    implementation(project(":stablebridge-tx-recovery-api"))

    // Spring Boot starters
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-data-redis")
    implementation("org.apache.commons:commons-pool2")

    // Lombok
    compileOnly(libs.lombok)
    annotationProcessor(libs.lombok)

    // MapStruct
    implementation(libs.mapstruct)
    annotationProcessor(libs.mapstruct.processor)

    // Flyway
    implementation(libs.flyway.core)
    implementation(libs.flyway.postgresql)

    // Resilience4j
    implementation(libs.resilience4j.spring.boot)

    // Temporal
    implementation(libs.temporal.spring.boot.starter)

    // Kafka
    implementation("org.springframework.kafka:spring-kafka")

    // Observability
    implementation("io.micrometer:micrometer-registry-prometheus")
    implementation(libs.logstash.logback.encoder)

    // Bouncy Castle
    implementation(libs.bouncycastle)

    // Database
    runtimeOnly("org.postgresql:postgresql")

    // Test fixtures (shared across unit + integration tests)
    testFixturesImplementation("org.assertj:assertj-core")
    testFixturesImplementation("org.mockito:mockito-core")
    testFixturesImplementation("org.springframework.boot:spring-boot-starter-test")
    testFixturesImplementation("org.springframework.boot:spring-boot-starter-data-jpa")
    testFixturesImplementation(libs.testcontainers.junit)
    testFixturesImplementation(libs.testcontainers.postgresql)
    testFixturesImplementation(libs.testcontainers.kafka)
    testFixturesImplementation("org.springframework.boot:spring-boot-starter-data-redis")
    testFixturesImplementation(libs.wiremock)
    testFixturesCompileOnly(libs.lombok)
    testFixturesAnnotationProcessor(libs.lombok)

    // Test dependencies
    testImplementation(testFixtures(project))
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-flyway")
    testImplementation(libs.archunit)
    testImplementation(libs.testcontainers.junit)
    testImplementation(libs.testcontainers.postgresql)
    testImplementation(libs.testcontainers.kafka)
    testImplementation(libs.wiremock)
    testImplementation(libs.instancio)
    testImplementation(libs.temporal.testing)

    // Lombok for tests
    testCompileOnly(libs.lombok)
    testAnnotationProcessor(libs.lombok)
}
