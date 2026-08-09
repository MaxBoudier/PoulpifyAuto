// Top-level build file where you can add configuration options common to all sub-projects/modules.
//
// Depuis AGP 9, le plugin Kotlin est integre et deja sur le classpath : les
// modules referencent `org.jetbrains.kotlin.jvm` par id sans version. Les
// plugins compilateur (serialisation, Compose) ne sont eux pas embarques et
// doivent etre declares ici, epingles sur la version Kotlin qu'AGP embarque
// (2.2.10) — une version differente casserait la compilation.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.kotlin.compose) apply false
}
