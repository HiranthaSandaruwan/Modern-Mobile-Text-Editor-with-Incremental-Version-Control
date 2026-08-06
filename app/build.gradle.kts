// Build configuration for the app module.
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp") // needed by Room to generate database code
}

android {
    namespace = "com.example.texteditor"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.texteditor"
        minSdk = 26          // Android 8.0 and above
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
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
    // --- Standard Android UI libraries ---
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.recyclerview:recyclerview:1.3.2")

    // --- Coroutines helpers (lifecycleScope) for background work ---
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")

    // --- Room: SQLite database used to store version-control information ---
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    // --- java-diff-utils: computes and applies text diffs (deltas) ---
    implementation("io.github.java-diff-utils:java-diff-utils:4.12")

    // --- Markwon: renders Markdown text for the preview panel ---
    implementation("io.noties.markwon:core:4.6.2")
}
