// build.gradle.kts (módulo :app)
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.autohotspot"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.autohotspot"
        minSdk = 26          // Android 8.0
        targetSdk = 35       // Android 15
        versionCode = 1
        versionName = "1.0.0"
    }

    buildTypes {
        release {
            // Se deja sin ofuscar a propósito: la app usa reflexión sobre APIs ocultas del
            // framework y dexmaker genera clases en tiempo de ejecución. Si activas R8,
            // revisa proguard-rules.pro.
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    // AndroidX base
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")

    // Jetpack Compose (versiones alineadas por el BOM)
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    debugImplementation("androidx.compose.ui:ui-tooling")

    // Corrutinas
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    // Permite invocar APIs ocultas (@SystemApi) del framework en Android 9+.
    // Necesario para TetheringManager / ConnectivityManager.startTethering.
    implementation("org.lsposed.hiddenapibypass:hiddenapibypass:4.3")

    // Genera en tiempo de ejecución una subclase de la clase abstracta oculta
    // ConnectivityManager.OnStartTetheringCallback (solo Android 8.0 – 10).
    implementation("com.linkedin.dexmaker:dexmaker:2.28.3")

    // Shizuku: llamadas a servicios del sistema con identidad "shell" (sin root).
    // Necesario en móviles (p. ej. Xiaomi/HyperOS) que rechazan WRITE_SETTINGS para tethering.
    implementation("dev.rikka.shizuku:api:13.1.5")
    implementation("dev.rikka.shizuku:provider:13.1.5")
}
