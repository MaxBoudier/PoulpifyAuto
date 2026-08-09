plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "fr.maxboudier.poulpifyauto.core.data"
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
    implementation(project(":core:model"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.coroutines.android)
    // QR d'invitation genere a la volee, partage par les surfaces voiture
    // (templates et navigation media) : il vit donc ici et non dans :car.
    implementation(libs.zxing.core)

    testImplementation(libs.junit)
}
