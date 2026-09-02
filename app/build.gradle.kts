import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
}

// Each tester keeps their backend computer's LAN IPv4 address in this ignored file.
// With no override, the project remains usable on an emulator without extra setup.
val localBuildProperties = Properties().apply {
    val propertiesFile = rootProject.file("local.properties")
    if (propertiesFile.exists()) propertiesFile.inputStream().use { load(it) }
}
val devServerIp = localBuildProperties.getProperty("journify.devServerIp")?.trim() ?: "10.0.2.2"
require(
    Regex("[0-9]{1,3}(\\.[0-9]{1,3}){3}").matches(devServerIp)
        && devServerIp.split('.').all { it.toInt() in 0..255 }
) {
    "journify.devServerIp in local.properties must be an IPv4 address only, e.g. 192.168.1.25 (no http:// or port)."
}

android {
    namespace = "com.example.finalproject"
    compileSdk = 36

    buildFeatures {
        buildConfig = true
    }

    defaultConfig {
        applicationId = "com.example.finalproject"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
        // Development configuration only; a distributed release still needs a public HTTPS backend.
        buildConfigField("String", "PHONE_BASE_URL", "\"http://$devServerIp:8000/\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
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
}

dependencies {
    implementation(libs.activity.ktx)
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.recyclerview)
    implementation(libs.cardview)

    // Room
    implementation(libs.room.runtime)
    annotationProcessor(libs.room.compiler)

    // Lifecycle
    implementation(libs.lifecycle.viewmodel)
    implementation(libs.lifecycle.livedata)

    // Test
    testImplementation(libs.junit)
}
