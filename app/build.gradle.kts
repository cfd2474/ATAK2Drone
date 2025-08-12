plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.example.atak2drone"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.atak2drone" // overridden by flavors below
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("debug")
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
            buildConfigField("int", "PAYLOAD_ENUM", "67")
        }

        create("matrice300m350") {
            dimension = "drone"
            applicationId = "com.taksolutions.atak2drone.m300m350"
            versionNameSuffix = "-m300m350"
            resValue("string", "app_name", "ATAK2M300/M350")

            buildConfigField("int", "DRONE_ENUM", "999")
            buildConfigField("int", "PAYLOAD_ENUM", "998")
        }

        // NEW: M4T flavor
        create("m4t") {
            dimension = "drone"
            applicationId = "com.taksolutions.atak2drone.m4t"
            versionNameSuffix = "-m4t"
            resValue("string", "app_name", "ATAK2M4T")

            // TODO: replace with real DJI enum values for M4T when known
            buildConfigField("int", "DRONE_ENUM", "1001")
            buildConfigField("int", "PAYLOAD_ENUM", "1000")
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