// Tells Gradle which repositories to use for downloading plugins and libraries,
// and which modules belong to this project (we only have one module: "app").
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "KotlinTextEditor"
include(":app")
