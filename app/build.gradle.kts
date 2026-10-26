plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}
android {
    namespace = "dev.nurislam.tv"
    compileSdk = 35
    defaultConfig {
        applicationId = "dev.nurislam.tv"
        minSdk = 26
        targetSdk = 35
        versionCode = providers.gradleProperty("nurIslamVersionCode").orNull?.let { value ->
            requireNotNull(value.toIntOrNull()?.takeIf { it in 3..2_100_000_000 }) { "Invalid nurIslamVersionCode" }
        } ?: 3
        versionName = "0.3.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("boolean", "PHONE_PREVIEW", "false")
    }
    buildTypes {
        debug { applicationIdSuffix = ".debug"; versionNameSuffix = "-demo" }
        release { isMinifyEnabled = false }
        create("phoneDebug") {
            initWith(getByName("debug"))
            applicationIdSuffix = ".phone.debug"
            versionNameSuffix = "-phone-preview"
            buildConfigField("boolean", "PHONE_PREVIEW", "true")
            matchingFallbacks += "debug"
        }
    }
    buildFeatures { compose = true; buildConfig = true }
    sourceSets.getByName("androidTest").java.srcDir("src/sharedTest/java")
    sourceSets.getByName("androidTest").assets.srcDir("src/test/resources")
    sourceSets.getByName("testDebug").java.srcDir("src/sharedTest/java")
    sourceSets.getByName("testDebug").java.srcDir("src/acceptanceTest/java")
    sourceSets.getByName("testRelease").java.srcDir("src/acceptanceTest/java")
    listOf("testDebug", "testRelease", "testPhoneDebug").forEach { sourceSets.getByName(it).java.srcDir("src/iqamahUiTest/java"); sourceSets.getByName(it).java.srcDir("src/importUiTest/java"); sourceSets.getByName(it).java.srcDir("src/ramadanUiTest/java") }
    testOptions {
        unitTests.isIncludeAndroidResources = true
        unitTests.all {
            it.systemProperty("nurIslam.screenshotDir", layout.buildDirectory.dir("screenshots").get().asFile.absolutePath)
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}
dependencies {
    implementation(platform("androidx.compose:compose-bom:2025.04.01"))
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.0")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
    implementation("com.google.code.gson:gson:2.11.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.0")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
    androidTestImplementation(platform("androidx.compose:compose-bom:2025.04.01"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    // Run the same Compose checks on JVM when hardware emulation is unavailable.
    add("testDebugImplementation", platform("androidx.compose:compose-bom:2025.04.01"))
    add("testDebugImplementation", "androidx.compose.ui:ui-test-junit4")
    add("testDebugImplementation", "androidx.test.ext:junit:1.2.1")
    add("testDebugImplementation", "org.robolectric:robolectric:4.15.1")
    add("testReleaseImplementation", platform("androidx.compose:compose-bom:2025.04.01"))
    add("testReleaseImplementation", "androidx.compose.ui:ui-test-junit4")
    add("testReleaseImplementation", "androidx.test.ext:junit:1.2.1")
    add("testReleaseImplementation", "org.robolectric:robolectric:4.15.1")
    add("testPhoneDebugImplementation", platform("androidx.compose:compose-bom:2025.04.01"))
    add("testPhoneDebugImplementation", "androidx.compose.ui:ui-test-junit4")
    add("testPhoneDebugImplementation", "androidx.test.ext:junit:1.2.1")
    add("testPhoneDebugImplementation", "org.robolectric:robolectric:4.15.1")
}
