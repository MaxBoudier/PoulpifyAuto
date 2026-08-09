plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "fr.maxboudier.poulpifyauto.core.spotify"
    compileSdk = 36

    defaultConfig {
        minSdk = 28
        consumerProguardFiles("consumer-rules.pro")
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
}

dependencies {
    // `api` : les signatures publiques du controleur exposent RemotePlayback.
    api(project(":core:model"))

    // L'AAR App Remote vit dans :app et non ici : un module bibliotheque ne
    // peut pas empaqueter un .aar local (l'AAR produit serait casse), et
    // surtout c'est le manifeste de l'AAR qui apporte le bloc <queries> sans
    // lequel isSpotifyInstalled() renvoie toujours false sur Android 11+.
    // Ici on ne veut que les classes a la compilation.
    compileOnly(files(rootProject.file("app/libs/spotify-app-remote-release-0.8.0.aar")))
    // Le SDK App Remote serialise ses messages par reflexion Gson.
    implementation(libs.gson)

    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
}
