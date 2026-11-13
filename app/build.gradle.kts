import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// API keys and the release signing details live in a .env file at the project root, which is never committed
// (.gitignore). Copy .env.template to .env and fill in your own values; see the template for where to get each one.
// A real environment variable with the same name (for example in CI) is used when .env doesn't set it, and the
// old local.properties names still work so existing setups keep building.
val dotEnv: Map<String, String> = rootProject.file(".env").takeIf { it.exists() }?.readLines().orEmpty()
    .map { it.trim() }
    .filter { it.isNotEmpty() && !it.startsWith("#") && it.contains('=') }
    .associate { line ->
        val key = line.substringBefore('=').removePrefix("export ").trim()
        val value = line.substringAfter('=').trim().removeSurrounding("\"").removeSurrounding("'")
        key to value
    }
val localProps = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

/** A secret from .env, then the environment, then the legacy local.properties name. Empty if none is set. */
fun secret(name: String, legacyName: String): String =
    dotEnv[name]?.takeIf { it.isNotBlank() }
        ?: System.getenv(name)?.takeIf { it.isNotBlank() }
        ?: localProps.getProperty(legacyName, "").trim()

android {
    namespace = "com.ssethhyy.journey"
    compileSdk {
        // 37.1: required by the Essentials Island SDK modules.
        version = release(37) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.ssethhyy.journey"
        minSdk = 26
        targetSdk = 37
        versionCode = 4
        versionName = "1.2.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "PTV_DEV_ID", "\"${secret("PTV_DEV_ID", "ptv.devId")}\"")
        buildConfigField("String", "PTV_API_KEY", "\"${secret("PTV_API_KEY", "ptv.apiKey")}\"")
        // Optional: Transport Victoria Open Data key for the crowding feeds.
        buildConfigField("String", "OPENDATA_KEY", "\"${secret("VIC_OPENDATA_KEY", "vic.opendata.key")}\"")
        resValue("string", "mapbox_access_token", secret("MAPBOX_ACCESS_TOKEN", "mapbox.accessToken").ifEmpty { "YOUR_MAPBOX_ACCESS_TOKEN" })
    }

    // Your release key, from .env (git-ignored), so it never reaches the repository:
    //   RELEASE_STORE_FILE, RELEASE_STORE_PASSWORD, RELEASE_KEY_ALIAS, RELEASE_KEY_PASSWORD
    // Without them (for example in a fresh clone or CI) release builds fall back to the debug key.
    val releaseStore = secret("RELEASE_STORE_FILE", "release.storeFile").takeIf { it.isNotEmpty() }?.let { file(it) }?.takeIf { it.exists() }
    signingConfigs {
        if (releaseStore != null) {
            create("release") {
                storeFile = releaseStore
                storePassword = secret("RELEASE_STORE_PASSWORD", "release.storePassword")
                keyAlias = secret("RELEASE_KEY_ALIAS", "release.keyAlias")
                keyPassword = secret("RELEASE_KEY_PASSWORD", "release.keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            // Installs next to a release build, so testing never replaces (or wipes) the copy you use day to day.
            applicationIdSuffix = ".dev"
        }
        release {
            // R8 shrinks and optimises the code; Compose apps run noticeably smoother with it.
            optimization {
                enable = true
            }
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
        resValues = true
    }
    testOptions {
        unitTests.isReturnDefaultValues = true
        unitTests.isIncludeAndroidResources = true
        unitTests.all { it.maxHeapSize = "2g" }
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
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.profileinstaller)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.viewmodel.navigation3)
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.glance.appwidget)
    implementation(libs.androidx.glance.material3)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(project(":island-sdk-compose"))
    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.play.services)
    implementation(libs.play.services.location)
    implementation(libs.mapbox.compose)
    implementation(libs.mapbox.maps)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.robolectric)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
