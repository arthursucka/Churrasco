plugins {
    id("com.android.application") version "8.7.3" apply false
    id("org.jetbrains.kotlin.android") version "2.0.0" apply false
    alias(libs.plugins.kotlin.compose) apply false
    id("com.google.gms.google-services") version "4.4.2" apply false // Plugin do Google Services
    id("com.google.firebase.crashlytics") version "3.0.2" apply false
}

tasks.register<Delete>("clean") {
    delete(layout.buildDirectory)
}

