plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    // Capturas de la UI sin emulador: ./gradlew recordRoborazziDebug
    id("io.github.takahirom.roborazzi")
}

android {
    namespace = "com.jsalinas.miboxos"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.jsalinas.miboxos"
        // Mi Box S (1.ª gen) corre Android TV 9 = API 28
        minSdk = 28
        targetSdk = 34
        // Subir versionName publica una versión nueva en GitHub (workflow "Publicar versión")
        versionCode = 8
        versionName = "0.7.1"
        // Key de Gemini precargada SOLO en builds locales personales (variable de entorno); en GitHub
        // queda vacía y la key se carga por ADB. La app la copia a sus ajustes en el primer arranque.
        buildConfigField("String", "DEFAULT_GEMINI_KEY", "\"${System.getenv("MIBOX_GEMINI_KEY").orEmpty()}\"")
    }

    // Clave fija de MiBoxOs (signing/miboxos-release.jks). Está en el repo a propósito, por decisión del
    // dueño: así GitHub y cualquier compilación firman igual sin secrets, y Android acepta cada versión
    // nueva encima de la anterior. Riesgo asumido: otros podrían firmar un APK "como" MiBoxOs, pero la
    // Mi Box solo se actualiza desde las versiones publicadas en este repo.
    signingConfigs {
        create("release") {
            storeFile = rootProject.file("signing/miboxos-release.jks")
            storePassword = "cRHRFVWPzufL4k0OTKMPKRksioMU"
            keyAlias = "miboxos"
            keyPassword = "cRHRFVWPzufL4k0OTKMPKRksioMU"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    testOptions {
        unitTests {
            // Robolectric necesita recursos, assets (fondos) y manifest reales
            isIncludeAndroidResources = true
            all { it.maxHeapSize = "3g" }
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2025.12.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.animation:animation")
    implementation("androidx.tv:tv-material:1.0.1")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.4")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.16.1")
    testImplementation("androidx.test:core:1.7.0")
    testImplementation("androidx.test.ext:junit:1.3.0")
    testImplementation("androidx.compose.ui:ui-test-junit4")
    testImplementation("io.github.takahirom.roborazzi:roborazzi:1.77.0")
    testImplementation("io.github.takahirom.roborazzi:roborazzi-compose:1.77.0")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    testImplementation("com.squareup.okhttp3:okhttp-tls:4.12.0")
    testImplementation("org.bouncycastle:bcpkix-jdk18on:1.86")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
