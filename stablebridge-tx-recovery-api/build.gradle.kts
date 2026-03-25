plugins {
    id("stablebridge-tx-recovery.library")
}

dependencies {
    // Jakarta validation for request DTOs
    implementation("jakarta.validation:jakarta.validation-api")

    // Lombok
    compileOnly(libs.lombok)
    annotationProcessor(libs.lombok)

    // Jackson annotations for serialization
    implementation("com.fasterxml.jackson.core:jackson-annotations")
}
