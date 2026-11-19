plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    application
}

application {
    mainClass.set("dev.orderproof.backend.MainKt")
    applicationName = "orderproof-backend"
}

dependencies {
    implementation(project(":api-contract"))
    implementation(project(":control-contract"))
    implementation(libs.sqlite.jdbc)

    testImplementation(libs.junit)
    testImplementation(libs.truth)
}
