plugins {
    id("com.android.application")
}

android {
    namespace = "com.asearch.relay"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.asearch.relay"
        minSdk = 26
        targetSdk = 35
        versionCode = 3
        versionName = "0.3-readonly"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }
}

dependencies {
    implementation("androidx.core:core:1.15.0")
}

