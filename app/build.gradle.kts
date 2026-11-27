plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

// Secrets are read from files at build time; never hard-coded or logged.
val keysDir = File(System.getenv("MAPLEDGER_KEYS") ?: "/workspace/mapledger-keys")
fun secret(name: String): String? = File(keysDir, name).takeIf { it.isFile }?.readText()?.trim()
// Amap key lookup order: -PAMAP_KEY / ~/.gradle/gradle.properties, env AMAP_KEY, then $keysDir/amap.key
val amapKey: String = (findProperty("AMAP_KEY") as String?) ?: System.getenv("AMAP_KEY") ?: secret("amap.key") ?: ""
val releasePass: String? = (findProperty("RELEASE_STORE_PASSWORD") as String?) ?: secret("release.pass")

android {
    namespace = "com.mapledger.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.mapledger.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
        manifestPlaceholders["AMAP_KEY"] = amapKey
        // Amap ships ARM-only native libs. arm64-v8a covers every current phone; pass -PwithArmv7 to also
        // package 32-bit ARM for old devices. x86_64 emulators run the arm64 libs via ARM translation.
        ndk {
            abiFilters += "arm64-v8a"
            if (project.hasProperty("withArmv7")) abiFilters += "armeabi-v7a"
        }
    }

    signingConfigs {
        getByName("debug") {
            storeFile = File(System.getProperty("user.home"), ".android/debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
        create("release") {
            storeFile = File(keysDir, "release.jks")
            storePassword = releasePass
            keyAlias = "mapledger"
            keyPassword = releasePass
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("debug")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true; buildConfig = true }
    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
        // Compress native libs in the APK: the Amap map lib is ~34 MB raw and ~1/3 of that compressed.
        jniLibs.useLegacyPackaging = true
    }
}

ksp { arg("room.schemaLocation", "$projectDir/schemas") }

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.animation:animation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    // Amap (高德) 3D map + location + search, combined artifact
    implementation("com.amap.api:3dmap-location-search:11.3.100_loc11.3.101_sea9.8.1")
}
