plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.winehub.nativebridge"
    compileSdk = 34
    defaultConfig {
        minSdk = 26
        externalNativeBuild { cmake { cppFlags += "-std=c++20" } }
        ndk { abiFilters += listOf("arm64-v8a") }
    }
    externalNativeBuild { cmake { path = file("src/main/cpp/CMakeLists.txt") } }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation(project(":core"))
    implementation("androidx.core:core-ktx:1.13.1")
}
