import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

// Load release signing configuration securely from local.properties or environment
val localProps = Properties().apply {
    val localPropertiesFile = rootProject.file("local.properties")
    if (localPropertiesFile.exists()) {
        load(localPropertiesFile.inputStream())
    }
}

val releaseStoreFilePath = localProps.getProperty("RELEASE_STORE_FILE") ?: System.getenv("RELEASE_STORE_FILE")
val releaseStorePassword = localProps.getProperty("RELEASE_STORE_PASSWORD") ?: System.getenv("RELEASE_STORE_PASSWORD")
val releaseKeyAlias = localProps.getProperty("RELEASE_KEY_ALIAS") ?: System.getenv("RELEASE_KEY_ALIAS")
val releaseKeyPassword = localProps.getProperty("RELEASE_KEY_PASSWORD") ?: System.getenv("RELEASE_KEY_PASSWORD")

android {
    namespace = "com.example.atak2drone"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.atak2drone" // overridden by flavors below
        minSdk = 26
        targetSdk = 36
        versionCode = 8
        versionName = "2.1.5"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            if (!releaseStoreFilePath.isNullOrBlank()) {
                val keystoreFile = file(releaseStoreFilePath)
                if (keystoreFile.exists()) {
                    storeFile = keystoreFile
                    storePassword = releaseStorePassword
                    keyAlias = releaseKeyAlias
                    keyPassword = releaseKeyPassword
                }
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            val releaseSigning = signingConfigs.getByName("release")
            signingConfig = if (releaseSigning.storeFile != null) releaseSigning else signingConfigs.getByName("debug")
        }
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true   // needed for flavor buildConfigField
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlinOptions {
        jvmTarget = "11"
    }

    // Product flavors so multiple aircraft builds can coexist
    flavorDimensions += "drone"

    productFlavors {
        create("mavic3t") {
            dimension = "drone"
            applicationId = "com.taksolutions.atak2drone.m3t"
            versionNameSuffix = "-m3t"
            resValue("string", "app_name", "ATAK2M3T")

            buildConfigField("int", "DRONE_ENUM", "77")
            buildConfigField("int", "DRONE_SUB_ENUM", "0")
            buildConfigField("int", "PAYLOAD_ENUM", "67")
        }

        create("matrice300m350") {
            dimension = "drone"
            applicationId = "com.taksolutions.atak2drone.m300m350"
            versionNameSuffix = "-m300m350"
            resValue("string", "app_name", "ATAK2M300/M350")

            buildConfigField("int", "DRONE_ENUM", "999")
            buildConfigField("int", "DRONE_SUB_ENUM", "0")
            buildConfigField("int", "PAYLOAD_ENUM", "998")
        }

        // M4T flavor
        create("m4t") {
            dimension = "drone"
            applicationId = "com.taksolutions.atak2drone.m4t"
            versionNameSuffix = "-m4t"
            resValue("string", "app_name", "ATAK2M4T")

            // TODO: replace with real DJI enum values for M4T when known
            buildConfigField("int", "DRONE_ENUM", "1001")
            buildConfigField("int", "DRONE_SUB_ENUM", "0")
            buildConfigField("int", "PAYLOAD_ENUM", "1000")
        }

        // Matrice 30 / 30T flavor
        create("matrice30") {
            dimension = "drone"
            applicationId = "com.taksolutions.atak2drone.m30"
            versionNameSuffix = "-m30"
            resValue("string", "app_name", "ATAK2M30")

            // DJI M30/M30T enums (DRONE_ENUM = 67, DRONE_SUB_ENUM = 1 for M30T, PAYLOAD_ENUM = 53)
            buildConfigField("int", "DRONE_ENUM", "67")
            buildConfigField("int", "DRONE_SUB_ENUM", "1")
            buildConfigField("int", "PAYLOAD_ENUM", "53")
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}