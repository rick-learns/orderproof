plugins {
    alias(libs.plugins.android.test)
    alias(libs.plugins.kotlin.serialization)
}

// A self-instrumenting test APK: it runs in its own process and drives the app from outside,
// so scenarios can kill and relaunch the app. The target package is an instrumentation
// argument, which means the corrected and faulty candidates are judged by the same binary.
android {
    namespace = "dev.orderproof.e2e"
    compileSdk = 37

    defaultConfig {
        minSdk = 26
        targetSdk = 37
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        missingDimensionStrategy("candidate", "corrected")
    }

    targetProjectPath = ":app"
    experimentalProperties["android.experimental.self-instrumenting"] = true

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(project(":control-contract"))
    implementation(libs.okhttp)
    implementation(libs.androidx.test.runner)
    implementation(libs.androidx.test.ext.junit)
    implementation(libs.androidx.test.uiautomator)
    implementation(libs.junit)
}
