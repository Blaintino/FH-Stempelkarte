plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// Release-Signatur kommt ausschließlich aus Umgebungsvariablen (lokal oder
// GitHub-Secrets im CI) – der Schlüssel selbst liegt nie im Repo.
val keystorePath: String? = System.getenv("ANDROID_KEYSTORE_PATH")

android {
    namespace = "de.fhstempelkarte"
    compileSdk = 37

    defaultConfig {
        applicationId = "de.fhstempelkarte"
        minSdk = 26
        targetSdk = 37
        // Muss bei jedem Update steigen, sonst verweigert Android die Installation.
        // Im CI kommt sie aus der laufenden Build-Nummer.
        versionCode = System.getenv("VERSION_CODE")?.toIntOrNull() ?: 1
        versionName = "1.0.${System.getenv("VERSION_CODE") ?: "0"}"
        manifestPlaceholders["appLabel"] = "FH Stempelkarte"
    }

    signingConfigs {
        if (keystorePath != null) {
            create("release") {
                storeFile = file(keystorePath)
                storePassword = System.getenv("ANDROID_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("ANDROID_KEY_ALIAS")
                keyPassword = System.getenv("ANDROID_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        // Eigene App-ID für Debug-Builds, damit eine per USB installierte
        // Debug-Version neben der Obtainium-Version existieren kann.
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            manifestPlaceholders["appLabel"] = "FH Stempelkarte (Debug)"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            signingConfig = signingConfigs.findByName("release")
        }
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
}
