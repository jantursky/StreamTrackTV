plugins {
    base
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.room) apply false
    alias(libs.plugins.kover)
    alias(libs.plugins.spotless)
    alias(libs.plugins.detekt)
}

// Coverage gate over the modules that hold logic. UI, the app shell and code that only talks to the
// Android framework are covered by UI tests and manual checks, not by this gate.
dependencies {
    kover(project(":core"))
    kover(project(":data"))
    kover(project(":sync"))
    kover(project(":collector"))
}

kover {
    reports {
        filters {
            excludes {
                classes(
                    "*.BuildConfig",
                    "*_Impl",
                    "*_Impl$*",
                    "*\$\$serializer",
                    "*.KeystoreSecretCipher",
                    "*.KeyPressService*",
                    "*.BootReceiver*",
                    "*.CollectionWorker*",
                    "*.CollectorScheduler*",
                    "*.AndroidPermissions*",
                    "*.PackageManagerAppCatalog*",
                    "*.SyncScheduler*",
                )
            }
        }
        verify {
            rule("Line coverage of core, data, sync and collector logic") {
                minBound(85)
            }
        }
    }
}

// Targets name the source folders instead of walking the whole tree, so the check does not read
// build output that a parallel `clean` is deleting.
spotless {
    kotlin {
        target("*/src/**/*.kt", "build-logic/*/src/**/*.kt")
        ktlint(libs.versions.ktlint.get()).setEditorConfigPath(rootProject.file(".editorconfig"))
    }
    kotlinGradle {
        target("*.gradle.kts", "*/*.gradle.kts", "build-logic/*.gradle.kts", "build-logic/*/*.gradle.kts")
        ktlint(libs.versions.ktlint.get()).setEditorConfigPath(rootProject.file(".editorconfig"))
    }
}

detekt {
    buildUponDefaultConfig = true
    parallel = true
    config.setFrom(file("config/detekt/detekt.yml"))
    source.setFrom(
        listOf("app", "core", "data", "collector", "sync", "ui").flatMap { module ->
            listOf(
                "src/main/kotlin",
                "src/test/kotlin",
                "src/androidTest/kotlin",
                "src/debug/kotlin",
                "src/release/kotlin",
            ).map { file("$module/$it") }
        },
    )
}

tasks.named("check") {
    dependsOn("detekt", "spotlessCheck", "koverVerify")
}
