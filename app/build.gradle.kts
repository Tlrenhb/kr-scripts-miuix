// Copyright 2026, kr-scripts-miuix contributors
// SPDX-License-Identifier: GPL-3.0

import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    // Code namespace keeps com.projectkr.shell so the Kotlin sources stay
    // untouched; the applicationId is the install identity.
    namespace = "com.projectkr.shell"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.tlrenhb.krscripts.miuix"
        minSdk = 24
        targetSdk = 37
        versionCode = 5000000
        versionName = "5.0.0-miuix"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_21)
    }
}

dependencies {
    implementation(project(":core"))

    // Compose stack pinned to the exact versions Miuix 0.9.4-rc01 was compiled
    // against: upstream pairs JB Compose Multiplatform 1.12.0-rc01, whose
    // foundation maps to androidx.compose 1.12.0-rc01 (see upstream
    // gradle/libs.versions.toml). Older runtimes crash on interactive paths.
    val composeVersion = "1.12.0-rc01"
    implementation("androidx.compose.ui:ui:$composeVersion")
    implementation("androidx.compose.ui:ui-tooling-preview:$composeVersion")
    implementation("androidx.compose.foundation:foundation:$composeVersion")
    debugImplementation("androidx.compose.ui:ui-tooling:$composeVersion")

    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("org.jetbrains.kotlinx:kotlinx-collections-immutable:0.5.1")

    // Miuix (HyperOS design) — https://github.com/compose-miuix-ui/miuix
    implementation("top.yukonga.miuix.kmp:miuix-ui-android:0.9.4-rc01")
    implementation("top.yukonga.miuix.kmp:miuix-preference-android:0.9.4-rc01")
    implementation("top.yukonga.miuix.kmp:miuix-icons-android:0.9.4-rc01")
    implementation("top.yukonga.miuix.kmp:miuix-blur-android:0.9.4-rc01")
    implementation("top.yukonga.miuix.kmp:miuix-squircle-android:0.9.4-rc01")
    implementation("top.yukonga.miuix.kmp:miuix-nav-android:0.9.4-rc01")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
}
