plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "fr.maxboudier.poulpifyauto.media"
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

    api(libs.media3.session)
    api(libs.media3.common)
    implementation(libs.androidx.core.ktx)
    // Uniquement pour CarNotificationManager/CarAppExtender : Android Auto
    // n'affiche pas les notifications ordinaires d'une application.
    implementation(libs.androidx.car.app)
    implementation(libs.kotlinx.coroutines.android)
    // media3 attend des ListenableFuture Guava : c'est cette extension qui
    // ponte les coroutines vers ce type (celle de kotlinx.coroutines.future
    // produit un CompletableFuture, incompatible).
    implementation(libs.kotlinx.coroutines.guava)

    testImplementation(libs.junit)
}
