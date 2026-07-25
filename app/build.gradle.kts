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

/**
 * versionCode : nombre de commits. Doit rester stritement croissant, sinon
 * Android refuse la mise a jour des installations existantes. On le conserve
 * tel quel, decouple du nom de version, precisement pour cette garantie.
 */
val gitVersionCode = "git rev-list --count HEAD".runCommand()?.toIntOrNull() ?: 1

/**
 * versionName : le tag SemVer quand le build vient d'un tag (v1.2.0 -> "1.2.0"),
 * sinon un nom de developpement portant le compte de commits et le commit, pour
 * qu'une build hors release reste identifiable sans se faire passer pour une version.
 */
val releaseTag = (System.getenv("GITHUB_REF_NAME") ?: "")
    .takeIf { Regex("""^v\d+\.\d+\.\d+""").matches(it) }
    ?: "git describe --tags --exact-match".runCommand()?.takeIf { it.startsWith("v") }
val appVersionName = releaseTag?.removePrefix("v") ?: "$gitVersionCode-dev+$gitCommit"

android {
    namespace = "xyz.notarobot.excerpta"
    compileSdk = 34

    defaultConfig {
        applicationId = "xyz.notarobot.excerpta"
        minSdk = 26
        targetSdk = 34
        versionCode = gitVersionCode
        versionName = appVersionName
        buildConfigField("String", "GIT_COMMIT", "\"$gitCommit\"")
        // Maj in-app : URLs complètes injectées par la CI (GitLab ou GitHub), qui connaît
        // son propre schéma de "release la plus récente". Vides en build local -> updater désactivé.
        // GitLab ex: RELEASES_URL=.../api/v4/projects/ID/releases/permalink/latest
        //            APK_URL=.../-/releases/permalink/latest/downloads/excerpta-android.apk
        // GitHub ex: RELEASES_URL=https://api.github.com/repos/OWNER/REPO/releases/latest
        //            APK_URL=https://github.com/OWNER/REPO/releases/latest/download/excerpta-android.apk
        val releasesUrl = System.getenv("RELEASES_URL") ?: ""
        val apkUrl = System.getenv("APK_URL") ?: ""
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
            // `?:` ne rattrape que null : un secret CI declare mais vide (cas
            // d'un secret GitHub non renseigne, qui vaut "") passait au travers
            // et produisait un mot de passe vide au lieu du repli local.
            fun env(name: String, fallback: String) =
                System.getenv(name)?.takeIf { it.isNotBlank() } ?: fallback
            storePassword = env("KEYSTORE_PASS", "excerpta_debug")
            keyAlias = env("KEY_ALIAS", "excerpta")
            keyPassword = env("KEY_PASS", "excerpta_debug")
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
