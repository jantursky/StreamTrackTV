plugins {
    alias(libs.plugins.streamtrack.android.library)
    alias(libs.plugins.kover)
}

android {
    namespace = "com.apptiary.streamtracktv.collector"
}

dependencies {
    api(project(":data"))
    api(libs.androidx.work.runtime)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.kotlin.test)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.room.runtime)
}
