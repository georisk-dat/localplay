plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "cl.localplay"
    compileSdk = 34

    defaultConfig {
        applicationId = "cl.localplay"
        minSdk = 24                // Android 7.0+
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    buildFeatures {
        viewBinding = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    kotlinOptions {
        jvmTarget = "1.8"
    }
}

dependencies {
    // Media3 / ExoPlayer — reproducción, sesión, notificación
    implementation("androidx.media3:media3-exoplayer:1.3.1")
    implementation("androidx.media3:media3-session:1.3.1")

    // Coil — carátulas de álbum (Kotlin-first, sin kapt)
    implementation("io.coil-kt:coil:2.6.0")

    // AndroidX estándar
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.3")

    // Coroutines — para la consulta a MediaStore
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // Guava — necesaria para MediaController.buildAsync()
    implementation("com.google.guava:guava:33.2.0-android")
}
