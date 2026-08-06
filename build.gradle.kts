// Root build file: declares the plugin versions used by the whole project.
// The actual configuration of the app lives in app/build.gradle.kts.
plugins {
    id("com.android.application") version "8.13.2" apply false
    id("org.jetbrains.kotlin.android") version "2.0.21" apply false
    // KSP = Kotlin Symbol Processing. Room uses it to generate database code at compile time.
    id("com.google.devtools.ksp") version "2.0.21-1.0.28" apply false
}
