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
val devServerIp = localBuildProperties.getProperty("journify.devServerIp")?.trim()
val customServerUrl = localBuildProperties.getProperty("journify.serverUrl")?.trim()

// Where a release build talks to. A shipped APK must never point at a private 192.168.x.x address.
val releaseBaseUrl = localBuildProperties.getProperty("journify.releaseBaseUrl")?.trim()
    ?: "https://journify-backend-hiky.onrender.com/"

val defaultBaseUrl = when {
    !customServerUrl.isNullOrEmpty() -> customServerUrl
    !devServerIp.isNullOrEmpty() -> "http://$devServerIp:8000/"
    else -> releaseBaseUrl
}

// Signing material also lives in the ignored local.properties. When it is absent the release
// build still assembles unsigned, so a teammate without the keystore is not blocked.
val releaseStoreFile = localBuildProperties.getProperty("journify.storeFile")?.trim()
val hasReleaseSigning = releaseStoreFile != null && file(releaseStoreFile).exists()
if (!devServerIp.isNullOrEmpty()) {
    require(
        Regex("[0-9]{1,3}(\\.[0-9]{1,3}){3}").matches(devServerIp)
            && devServerIp.split('.').all { it.toInt() in 0..255 }
    ) {
        "journify.devServerIp in local.properties must be an IPv4 address only, e.g. 192.168.1.25 (no http:// or port)."
    }
}

android {
    namespace = "com.example.finalproject"
    compileSdk = 36

    buildFeatures {
        buildConfig = true
    }

    defaultConfig {
        // Distinct from the `namespace` above: namespace is the Java package that R and
        // BuildConfig are generated into, applicationId is the identity Android installs under.
        // Only the latter needs to leave the com.example.* sample space.
        applicationId = "com.journify.app"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
        // Defaults to the deployed cloud backend unless a custom server/devServerIp is configured.
        buildConfigField("String", "PHONE_BASE_URL", "\"$defaultBaseUrl\"")
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = file(releaseStoreFile!!)
                storePassword = localBuildProperties.getProperty("journify.storePassword")
                keyAlias = localBuildProperties.getProperty("journify.keyAlias")
                keyPassword = localBuildProperties.getProperty("journify.keyPassword")
            }
        }
    }

    buildTypes {
        release {
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
            // Overrides the debug default so the shipped APK does not carry a LAN address.
            buildConfigField("String", "PHONE_BASE_URL", "\"$releaseBaseUrl\"")
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
    implementation(libs.viewpager2)
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
