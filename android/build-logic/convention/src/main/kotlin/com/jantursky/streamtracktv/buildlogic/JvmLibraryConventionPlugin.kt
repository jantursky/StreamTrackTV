package com.jantursky.streamtracktv.buildlogic

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension

class JvmLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("org.jetbrains.kotlin.jvm")
            pluginManager.apply("com.android.lint")
            extensions.configure<KotlinJvmProjectExtension> {
                jvmToolchain(libs.intVersion("jvmTarget"))
                explicitApi()
            }
            tasks.withType<Test>().configureEach { useJUnit() }
        }
    }
}
