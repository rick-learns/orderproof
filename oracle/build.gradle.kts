plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

// The oracle deliberately depends on neither the backend nor the app: it reads raw rows with
// its own SQL and judges them against hand-written expectations.
dependencies {
    implementation(libs.sqlite.jdbc)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.junit)
    testImplementation(libs.truth)
    // Tests only: produce real persisted ledgers to judge. Production code never links the backend.
    testImplementation(project(":backend"))
    testImplementation(project(":api-contract"))
}
