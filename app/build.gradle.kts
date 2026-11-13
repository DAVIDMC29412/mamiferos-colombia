plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

// Firebase se activa solo cuando existe app/google-services.json.
// Sin ese archivo la app compila y funciona offline; simplemente no sube nada.
if (file("google-services.json").exists()) {
    apply(plugin = "com.google.gms.google-services")
}

android {
    namespace = "co.mamiferos.colombia"
    compileSdk = 35

    defaultConfig {
        applicationId = "co.mamiferos.colombia"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
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
    androidResources {
        noCompress += "tflite"
    }
}

dependencies {
    // Interfaz (Jetpack Compose)
    implementation(platform("androidx.compose:compose-bom:2024.10.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.navigation:navigation-compose:2.8.3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("io.coil-kt:coil-compose:2.7.0")

    // Modelo de IA (TensorFlow Lite)
    implementation("org.tensorflow:tensorflow-lite:2.16.1")

    // Fotos: orientación y GPS guardados en la foto
    implementation("androidx.exifinterface:exifinterface:1.3.7")

    // Base de datos local (funciona sin internet)
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    // Subida automática cuando hay internet
    implementation("androidx.work:work-runtime-ktx:2.9.1")

    // GPS
    implementation("com.google.android.gms:play-services-location:21.3.0")

    // Base de datos en internet (Firebase)
    implementation(platform("com.google.firebase:firebase-bom:33.5.1"))
    implementation("com.google.firebase:firebase-firestore")
    implementation("com.google.firebase:firebase-auth")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.8.1")
}
