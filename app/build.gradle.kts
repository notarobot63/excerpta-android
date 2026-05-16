plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

fun String.runCommand(): String? = try {
    ProcessBuilder(split(" "))
        .redirectErrorStream(true)
        .start()
        .inputStream.bufferedReader().readText().trim()
        .takeIf { it.isNotBlank() }
} catch (_: Exception) { null }

val gitCommit = "git rev-parse --short HEAD".runCommand() ?: "unknown"

android {
    namespace = "xyz.notarobot.linky"
    compileSdk = 34

    defaultConfig {
        applicationId = "xyz.notarobot.linky"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
        buildConfigField("String", "GIT_COMMIT", "\"$gitCommit\"")
    }

    buildFeatures {
        buildConfig = true
    }

    signingConfigs {
        create("fixed") {
            storeFile = rootProject.file("linky-debug.jks")
            storePassword = "linky_debug"
            keyAlias = "linky"
            keyPassword = "linky_debug"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("fixed")
        }
        release {
            signingConfig = signingConfigs.getByName("fixed")
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
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
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    implementation("com.journeyapps:zxing-android-embedded:4.3.0")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("io.coil-kt:coil:2.6.0")
}
