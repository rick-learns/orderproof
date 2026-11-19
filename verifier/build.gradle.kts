plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    application
}

application {
    mainClass.set("dev.orderproof.verifier.MainKt")
    applicationName = "orderproof-verifier"
}

dependencies {
    implementation(project(":oracle"))
    implementation(project(":control-contract"))
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.junit)
    testImplementation(libs.truth)
}
