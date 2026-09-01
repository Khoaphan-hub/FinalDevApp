import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
}

// The development machine's LAN address changes whenever the Wi-Fi network changes, so it is
// read from local.properties (git-ignored, one value per developer) instead of being written
// into the Java source. Falls back to the emulator address when the key is absent.
val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}
val devServerIp: String = localProperties.getProperty("journify.devServerIp") ?: "10.0.2.2"

android {
    namespace = "com.example.finalproject"
    compileSdk = 36

    buildFeatures {
        // Required from AGP 8 onwards before buildConfigField values are generated.
        buildConfig = true
    }

    defaultConfig {
        applicationId = "com.example.finalproject"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Debug default: emulators reach the host through 10.0.2.2, a real phone through the LAN.
        buildConfigField("String", "EMULATOR_BASE_URL", "\"http://10.0.2.2:8000/\"")
        buildConfigField("String", "PHONE_BASE_URL", "\"http://$devServerIp:8000/\"")
    }

    buildTypes {
        release {
            // A shipped APK must never point at a private 192.168.x.x address. Replace this
            // with the deployed HTTPS origin before building the submission APK.
            buildConfigField("String", "EMULATOR_BASE_URL", "\"https://journify.example.com/\"")
            buildConfigField("String", "PHONE_BASE_URL", "\"https://journify.example.com/\"")
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
    implementation(libs.constraintlayout)
    implementation(libs.material)
    implementation(libs.recyclerview)
    implementation(libs.cardview)

    // Room
    implementation(libs.room.runtime)
    annotationProcessor(libs.room.compiler)

    // Lifecycle
    implementation(libs.lifecycle.viewmodel)
    implementation(libs.lifecycle.livedata)

    // Navigation
    implementation(libs.navigation.fragment)
    implementation(libs.navigation.ui)

    // Test
    testImplementation(libs.junit)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(libs.ext.junit)
}