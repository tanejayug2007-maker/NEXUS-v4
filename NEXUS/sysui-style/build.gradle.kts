plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

// Layer B proof of concept. Plain Android Drawables only (no Compose, no launcher code), because
// Nothing OS's SystemUI Quick Settings tiles and notification rows are classic Views. A privileged
// hook would install these as backgrounds. Nothing here modifies the system.
android {
    namespace = "com.nexus.sysui"
    compileSdk = 35
    defaultConfig { minSdk = 29 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    testOptions { unitTests.isReturnDefaultValues = true }
}

dependencies {
    testImplementation(libs.junit)
}
