package com.jantursky.streamtracktv.buildlogic

import com.android.build.api.variant.AndroidComponentsExtension
import com.android.build.api.variant.HasHostTestsBuilder
import com.android.build.api.variant.HostTestBuilder
import org.gradle.api.Project
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.withType

/**
 * Host (JVM) tests run against the debug variant only. Running them for release too doubles the build
 * time and adds nothing, since both variants compile the same sources.
 */
internal fun Project.configureHostTestsForDebugOnly() {
    extensions.getByType(AndroidComponentsExtension::class.java).beforeVariants { variant ->
        if (variant.buildType == "release" && variant is HasHostTestsBuilder) {
            variant.hostTests[HostTestBuilder.UNIT_TEST_TYPE]?.enable = false
        }
    }
}

/** Robolectric reaches into `FileDescriptor` internals, which newer JDKs only allow when exported. */
internal fun Project.configureRobolectricJvm() {
    tasks.withType<Test>().configureEach {
        jvmArgs("--add-exports=java.base/jdk.internal.access=ALL-UNNAMED")
    }
}
