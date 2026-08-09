// Kotlin pur : aucune dependance Android. Les modeles doivent pouvoir etre
// testes en JVM simple, sans instrumentation ni emulateur.
plugins {
    id("org.jetbrains.kotlin.jvm")
    alias(libs.plugins.kotlin.serialization)
}

// On cible le bytecode 17 sans passer par une toolchain : le JDK de build
// (21) compile deja pour cette cible, et D8 ne digere pas du class file 21.
java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(libs.kotlinx.serialization.json)
    // StateFlow apparait dans les ports declares ici : `api` pour que les
    // modules consommateurs le voient sans le redeclarer.
    api(libs.kotlinx.coroutines.core)
    testImplementation(libs.junit)
}
