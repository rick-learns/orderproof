pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "orderproof"

// Public API shapes shared by the app and the backend.
include(":api-contract")
// Test-only control/ledger shapes. Never a dependency of the shipped app.
include(":control-contract")
include(":backend")
// Independent business-invariant checks used by the release gate.
include(":oracle")
include(":verifier")
include(":app")
// Out-of-process UI Automator release scenarios: one APK drives either candidate.
include(":e2e")
