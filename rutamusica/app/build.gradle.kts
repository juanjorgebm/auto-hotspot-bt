plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.rutamusica"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.rutamusica"
        minSdk = 22          // Android 5.1 (pantalla del carro)
        targetSdk = 34
        // CI pasa -PversionCode=<número de build> para que el actualizador detecte versiones nuevas.
        val vc = (project.findProperty("versionCode") as String?)?.toIntOrNull() ?: 1
        versionCode = vc
        versionName = "1.0.$vc"
        vectorDrawables.useSupportLibrary = true
        // Solo ARM de 32 bits (funciona también en pantallas de 64 bits) para que el APK no pese el doble.
        ndk { abiFilters += listOf("armeabi-v7a") }
    }

    // Llave fija (solo de depuración) para que cada APK nuevo se pueda instalar encima del anterior.
    signingConfigs {
        getByName("debug") {
            storeFile = file("rutamusica-debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

dependencies {
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.4")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    // Botones del volante (siguiente / anterior / pausa)
    implementation("androidx.media:media:1.7.0")

    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    // Red (con certificados raíz extra para Android 5.1)
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:okhttp-tls:4.12.0")

    // Motor de Firefox: la versión 143 es la última que funciona en Android 5 (desde la 144 piden Android 8).
    implementation("org.mozilla.geckoview:geckoview:143.+")

    // Carátulas
    implementation("io.coil-kt:coil-compose:2.7.0")
}
