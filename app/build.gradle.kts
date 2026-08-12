plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {kotlin {
    jvmToolchain(17)
}
    namespace = "com.rjnx.ai"
    compileSdk = 35

    buildFeatures {
        buildConfig = true
    }

    defaultConfig {
        applicationId = "com.rjnx.ai"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
        // Put your API key in ~/.gradle/gradle.properties as OPENAI_API_KEY=...
        buildConfigField("String", "OPENAI_API_KEY", "\"${project.findProperty("OPENAI_API_KEY") ?: ""}\"")
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.activity:activity-ktx:1.10.0")
}
