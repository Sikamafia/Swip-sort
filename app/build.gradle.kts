plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.example.swipesort"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.swipesort"
        minSdk = 29
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }

    sourceSets["main"].jniLibs.srcDirs("src/main/jniLibs")
}


dependencies {
    implementation("androidx.activity:activity-ktx:1.10.0")
}
