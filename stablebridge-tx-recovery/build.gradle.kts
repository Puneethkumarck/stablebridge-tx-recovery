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

    // Test dependencies
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-flyway")
    testImplementation(libs.archunit)
    testImplementation(libs.testcontainers.junit)
    testImplementation(libs.testcontainers.postgresql)
    testImplementation(libs.testcontainers.kafka)
    testImplementation(libs.wiremock)
    testImplementation(libs.instancio)

    // Lombok for tests
    testCompileOnly(libs.lombok)
    testAnnotationProcessor(libs.lombok)
}
