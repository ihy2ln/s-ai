plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.sai.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.sai.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 33
        versionName = "0.34.0"
    }

    // Release signing is driven entirely by Gradle properties (-P flags), never
    // committed values, so the same build.gradle.kts works locally (unsigned)
    // and in CI (signed from repo secrets). See docs/RELEASING.md.
    val releaseStoreFile = findProperty("SAI_RELEASE_STORE_FILE") as String?

    signingConfigs {
        if (releaseStoreFile != null) {
            create("release") {
                storeFile = file(releaseStoreFile)
                storePassword = findProperty("SAI_RELEASE_STORE_PASSWORD") as String?
                keyAlias = findProperty("SAI_RELEASE_KEY_ALIAS") as String?
                keyPassword = findProperty("SAI_RELEASE_KEY_PASSWORD") as String?
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            if (releaseStoreFile != null) {
                signingConfig = signingConfigs.getByName("release")
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
}

dependencies {
    implementation(project(":core"))
    implementation("androidx.activity:activity:1.9.3")
    implementation("androidx.core:core:1.13.1")
}
