plugins {
    id("stablebridge-tx-recovery.library")
}

dependencies {
    implementation("jakarta.validation:jakarta.validation-api")

    compileOnly(libs.lombok)
    annotationProcessor(libs.lombok)

    implementation("com.fasterxml.jackson.core:jackson-annotations")
}
