package com.jantursky.streamtracktv.buildlogic

import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

class AndroidLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("com.android.library")
            extensions.configure<LibraryExtension> {
                compileSdk = libs.intVersion("compileSdk")
                defaultConfig {
                    minSdk = libs.intVersion("minSdk")
                    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
                }
                compileOptions {
                    sourceCompatibility = JavaVersion.VERSION_17
                    targetCompatibility = JavaVersion.VERSION_17
                }
                testOptions {
                    unitTests.isIncludeAndroidResources = true
                }
                lint {
                    abortOnError = true
                    warningsAsErrors = true
                    // Dependency updates arrive through Dependabot, not as lint failures.
                    disable += setOf("GradleDependency", "AndroidGradlePluginVersion", "NewerVersionAvailable")
                    checkDependencies = false
                }
            }
            configureHostTestsForDebugOnly()
            configureRobolectricJvm()
        }
    }
}
