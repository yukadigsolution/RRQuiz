plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.yuka.rrquiz"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.yuka.rrquiz"
        // minSdk 26: Apache POI 5.2.5 butuh MethodHandle (Android O / API 26).
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
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
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        viewBinding = true
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    // Parse file Excel (.xlsx/.xls) untuk import bank soal di Settings.
    implementation("org.apache.poi:poi-ooxml:5.2.5")
}
