// Copyright 2026, kr-scripts-miuix contributors
// SPDX-License-Identifier: GPL-3.0

import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "com.projectkr.shell"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.projectkr.shell"
        minSdk = 24
        targetSdk = 37
        versionCode = 5000000
        versionName = "5.0.0-miuix"
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(project(":core"))

    // androidx Compose 1.12.x：与 miuix-ui-android:0.9.4 传递依赖的
    // org.jetbrains.compose.*:1.12.0（其 Android 变体即 androidx.compose 1.12.0）对齐
    implementation(platform("androidx.compose:compose-bom:2026.09.00"))
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    debugImplementation("androidx.compose.ui:ui-tooling")

    // Miuix 0.9.4 稳定版（https://github.com/compose-miuix-ui/miuix）
    implementation("top.yukonga.miuix.kmp:miuix-ui-android:0.9.4")
    implementation("top.yukonga.miuix.kmp:miuix-preference-android:0.9.4")
    implementation("top.yukonga.miuix.kmp:miuix-icons-android:0.9.4")
    implementation("top.yukonga.miuix.kmp:miuix-nav-android:0.9.4")

    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.12.0")
}
