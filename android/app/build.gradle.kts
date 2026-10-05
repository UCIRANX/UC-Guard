import java.io.File

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Version code comes from the GitHub Actions run number, so every build is newer.
val ucgVersionCode = (System.getenv("UCG_VERSION_CODE") ?: "1").toIntOrNull() ?: 1

// Release signing key, prepared by the workflow from repository secrets.
val ucgKeystore = System.getenv("UCG_KEYSTORE")?.let { File(it) }?.takeIf { it.isFile }

android {
    namespace = "ir.uciranx.ucg"
    compileSdk = 35

    defaultConfig {
        applicationId = "ir.uciranx.ucg"
        minSdk = 26
        targetSdk = 35
        versionCode = ucgVersionCode
        versionName = "1.0.$ucgVersionCode"
    }

    signingConfigs {
        create("release") {
            if (ucgKeystore != null) {
                storeFile = ucgKeystore
                storePassword = System.getenv("UCG_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("UCG_KEY_ALIAS")?.takeIf { it.isNotBlank() } ?: "ucg"
                keyPassword = System.getenv("UCG_KEY_PASSWORD")?.takeIf { it.isNotBlank() }
                    ?: System.getenv("UCG_KEYSTORE_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = if (ucgKeystore != null) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
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
        compose = true
        buildConfig = true
    }

    packaging {
        jniLibs {
            // The core, Psiphon and Tor transports are real executables shipped as lib*.so.
            // They must be extracted to disk and must not be stripped again.
            useLegacyPackaging = true
            keepDebugSymbols += "**/*.so"
        }
    }

    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
}
