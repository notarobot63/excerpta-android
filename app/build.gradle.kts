import java.util.Base64

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
val gitVersionCode = "git rev-list --count HEAD".runCommand()?.toIntOrNull() ?: 1

android {
    namespace = "xyz.notarobot.excerpta"
    compileSdk = 34

    defaultConfig {
        applicationId = "xyz.notarobot.excerpta"
        minSdk = 26
        targetSdk = 34
        versionCode = gitVersionCode
        versionName = "1.${gitVersionCode}"
        buildConfigField("String", "GIT_COMMIT", "\"$gitCommit\"")
        val serverUrl = System.getenv("GITEA_SERVER_URL") ?: ""
        val repository = System.getenv("GITEA_REPOSITORY") ?: ""
        val releasesUrl = if (serverUrl.isNotBlank() && repository.isNotBlank())
            "$serverUrl/api/v1/repos/$repository/releases/tags/latest" else ""
        val apkUrl = if (serverUrl.isNotBlank() && repository.isNotBlank())
            "$serverUrl/$repository/releases/download/latest/excerpta-android.apk" else ""
        buildConfigField("String", "RELEASES_URL", "\"$releasesUrl\"")
        buildConfigField("String", "APK_URL", "\"$apkUrl\"")
    }

    buildFeatures {
        buildConfig = true
    }

    signingConfigs {
        create("fixed") {
            val b64 = System.getenv("KEYSTORE_B64") ?: ""
            storeFile = if (b64.isNotBlank()) {
                val f = rootProject.file("excerpta-ci.jks")
                f.writeBytes(Base64.getDecoder().decode(b64))
                f
            } else {
                rootProject.file("excerpta-debug.jks") // dev local uniquement
            }
            storePassword = System.getenv("KEYSTORE_PASS") ?: "excerpta_debug"
            keyAlias = System.getenv("KEY_ALIAS") ?: "excerpta"
            keyPassword = System.getenv("KEY_PASS") ?: "excerpta_debug"
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
    implementation("androidx.security:security-crypto:1.0.0")
    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")
}
