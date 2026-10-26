plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// Gradle properties are non-secret endpoint configuration; do not put credentials here.
// Debug defaults to the Android emulator's host gateway (physical phones need -P... override).
val debugServerRelayUrl = providers.gradleProperty("serverRelayDebugBaseUrl")
    .orElse("http://10.0.2.2:8090/").get()
val releaseServerRelayUrl = providers.gradleProperty("serverRelayReleaseBaseUrl")
    .orElse("").get()

fun buildConfigString(value: String): String = "\"" +
        value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

if (releaseServerRelayUrl.isNotEmpty()) {
    require(releaseServerRelayUrl.startsWith("https://", ignoreCase = true)) {
        "serverRelayReleaseBaseUrl must use HTTPS"
    }
}

android {
    namespace = "mme.corp.audioshare"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "mme.corp.audioshare"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        getByName("debug") {
            buildConfigField("String", "SERVERRELAY_BASE_URL", buildConfigString(debugServerRelayUrl))
            buildConfigField(
                "long",
                "PRESENCE_HEARTBEAT_INTERVAL_MILLIS",
                "5000L"
            )
            buildConfigField(
                "long",
                "ROOM_MEMBER_POLL_INTERVAL_MILLIS",
                "5000L"
            )
            buildConfigField(
                "long",
                "ROOM_MEMBER_POLL_MAX_BACKOFF_MILLIS",
                "60000L"
            )
        }
        release {
            buildConfigField("String", "SERVERRELAY_BASE_URL", buildConfigString(releaseServerRelayUrl))
            isMinifyEnabled = false
            buildConfigField(
                "long",
                "PRESENCE_HEARTBEAT_INTERVAL_MILLIS",
                "30000L"
            )
            buildConfigField(
                "long",
                "ROOM_MEMBER_POLL_INTERVAL_MILLIS",
                "15000L"
            )
            buildConfigField(
                "long",
                "ROOM_MEMBER_POLL_MAX_BACKOFF_MILLIS",
                "120000L"
            )
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.datastore.core)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.viewmodel.savedstate)
    implementation(libs.androidx.navigation.compose)
    testImplementation(libs.junit)
    testImplementation(libs.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.navigation.testing)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)

    // Networking
    implementation(libs.retrofit)
    implementation(libs.retrofit.gson)

    implementation(libs.okhttp)

// JSON
    implementation(libs.gson)

// Coroutines
    implementation(libs.coroutines.android)

// ViewModel
    implementation(libs.androidx.lifecycle.viewmodel)
}
// A release artifact must never ship with an empty, unreviewed API origin.
// Keep normal debug builds and IDE sync independent of production hosting decisions.
tasks.configureEach {
    if (name == "preReleaseBuild") {
        doFirst {
            check(releaseServerRelayUrl.isNotBlank()) {
                "Set -PserverRelayReleaseBaseUrl=https://your-server/ before building release"
            }
        }
    }
}
