import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// Le client ID Spotify n'est pas un secret cryptographique, mais il n'a rien a
// faire dans le depot : il est lu de local.properties, non versionne.
val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}
val spotifyClientId: String = localProperties.getProperty("spotify.clientId") ?: ""

android {
    namespace = "fr.maxboudier.poulpifyauto"
    compileSdk = 36

    defaultConfig {
        applicationId = "fr.maxboudier.poulpifyauto"
        minSdk = 28
        targetSdk = 36
        versionCode = 2
        versionName = "2.0"

        buildConfigField("String", "SPOTIFY_CLIENT_ID", "\"$spotifyClientId\"")
        // Doit correspondre exactement a l'URI enregistree dans le dashboard
        // Spotify, sinon App Remote refuse la connexion.
        buildConfigField("String", "SPOTIFY_REDIRECT_URI", "\"poulpifyauto://callback\"")
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }

    packaging {
        resources.excludes += setOf(
            "/META-INF/{AL2.0,LGPL2.1}",
            "/META-INF/DEPENDENCIES",
        )
    }
}

dependencies {
    implementation(project(":core:session"))
    implementation(project(":car"))
    implementation(project(":media"))

    // L'AAR App Remote est declare ici et nulle part ailleurs : un module
    // bibliotheque ne peut pas empaqueter un .aar local, et c'est son
    // manifeste qui apporte le bloc <queries> requis sur Android 11+.
    implementation(files("libs/spotify-app-remote-release-0.8.0.aar"))
    implementation(libs.gson)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.car.app)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)

    testImplementation(libs.junit)
}
