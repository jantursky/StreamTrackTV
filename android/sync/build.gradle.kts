plugins {
    alias(libs.plugins.streamtrack.android.library)
    alias(libs.plugins.kover)
}

android {
    namespace = "com.apptiary.streamtracktv.sync"
}

dependencies {
    api(project(":data"))
    api(libs.ktor.client.core)
    api(libs.androidx.work.runtime)
    implementation(libs.ktor.client.android)

    testImplementation(libs.kotlin.test)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.ktor.client.mock)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.room.runtime)
    testImplementation(libs.androidx.work.testing)
}
