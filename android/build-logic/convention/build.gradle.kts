plugins {
    `kotlin-dsl`
}

dependencies {
    compileOnly(libs.android.gradle.plugin)
    compileOnly(libs.kotlin.gradle.plugin)
}

gradlePlugin {
    plugins {
        register("androidLibrary") {
            id = "streamtrack.android.library"
            implementationClass = "com.apptiary.streamtracktv.buildlogic.AndroidLibraryConventionPlugin"
        }
        register("jvmLibrary") {
            id = "streamtrack.jvm.library"
            implementationClass = "com.apptiary.streamtracktv.buildlogic.JvmLibraryConventionPlugin"
        }
    }
}
