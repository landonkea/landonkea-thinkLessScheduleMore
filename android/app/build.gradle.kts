plugins {
    id("com.android.application")       // Builds an .apk (phone app)
    id("org.jetbrains.kotlin.android")  // Compiles Kotlin code
}

android {
    namespace = "com.landonkea.thinklessschedulemore"
    compileSdk = 36  // Compile against Android SDK 36.
    buildToolsVersion = "36.0.0"

    defaultConfig {
        applicationId = "com.landonkea.thinklessschedulemore"
        minSdk = 26   // Android 8.0 (Foreground Service requires this).
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"
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
    // ── AppCompat (standard Android UI components) ────────────
    // Provides AppCompatActivity, AlertDialog, and backwards-
    // compatibility for older Android versions.
    implementation("androidx.appcompat:appcompat:1.6.1")

    // ── Core KTX (Kotlin extensions for Android core APIs) ───
    implementation("androidx.core:core-ktx:1.12.0")
}
