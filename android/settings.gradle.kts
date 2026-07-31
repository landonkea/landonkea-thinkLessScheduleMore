pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}

// The app module (the actual phone app)
rootProject.name = "ThinkLessScheduleMore"
include(":app")
