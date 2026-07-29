# ───────────────────────────────────────────────────────────────────
# Project settings — tells Gradle what modules to build
# ───────────────────────────────────────────────────────────────────
pluginManagement {
    repositories {
        google()            // Google's Maven repo (Android SDK)
        mavenCentral()      // Standard Java/Kotlin libraries
        gradlePluginPortal() // Gradle plugins
    }
}

dependencyResolution {
    repositories {
        google()
        mavenCentral()
    }
}

// The app module (the actual phone app)
rootProject.name = "ThinkLessScheduleMore"
include(":app")
