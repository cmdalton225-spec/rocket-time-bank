plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.cmdalton.rocket"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.cmdalton.rocket"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"
    }

    // The org's one permanent debug key (same keystore as adhd-app's keystore/debug.keystore),
    // so every debug build is signed identically and installs as an update. Debug only.
    signingConfigs {
        getByName("debug") {
            storeFile = rootProject.file("keystore/debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        getByName("debug") {
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
        unitTests.all {
            it.jvmArgs(
                "--add-exports=java.base/jdk.internal.access=ALL-UNNAMED",
                "--add-opens=java.base/jdk.internal.access=ALL-UNNAMED",
                "--add-opens=java.base/java.io=ALL-UNNAMED",
            )
            // Optional: where Robolectric downloads its Android runtime (for networks that
            // rate-limit Maven Central). Unset = Robolectric's normal default.
            System.getenv("ROBOLECTRIC_REPO_URL")?.let { url ->
                it.systemProperty("robolectric.dependency.repo.url", url)
            }
        }
    }

    lint {
        abortOnError = true
        checkReleaseBuilds = false
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2026.09.00"))
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.13.0")

    testImplementation("junit:junit:4.13.2")
    // Real org.json so the save format is tested off the tablet (Android's copy is a stub there).
    testImplementation("org.json:json:20250517")
    // Device-level checks without an emulator: real activities, service, widget, intents.
    testImplementation("org.robolectric:robolectric:4.17")
    testImplementation("androidx.test:core:1.7.0")
    testImplementation("androidx.compose.ui:ui-test-junit4")
}
