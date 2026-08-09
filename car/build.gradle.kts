plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "fr.maxboudier.poulpifyauto.car"
    compileSdk = 36

    defaultConfig {
        minSdk = 28
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
    api(project(":core:session"))

    api(libs.androidx.car.app)
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.android)
    // QR genere a la volee : l'ancienne app embarquait un PNG fige qui ne
    // pouvait pas suivre un changement d'URL de serveur.
    implementation(libs.zxing.core)

    testImplementation(libs.junit)
}
