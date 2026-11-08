plugins {
    alias(libs.plugins.streamtrack.android.library)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.apptiary.streamtracktv.ui"
    buildFeatures {
        compose = true
    }
}

dependencies {
    api(project(":collector"))
    api(project(":sync"))
    api(platform(libs.compose.bom))
    api(libs.androidx.tv.material)
    implementation(libs.androidx.activity.compose)
    implementation(libs.compose.foundation)
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.qrcodegen)
    debugImplementation(libs.compose.ui.tooling)

    testImplementation(libs.kotlin.test)
    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.compose.ui.test.junit4)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.espresso)
    debugImplementation(libs.compose.ui.test.manifest)
}
