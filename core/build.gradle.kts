// Copyright 2026, kr-scripts-miuix contributors
// SPDX-License-Identifier: GPL-3.0
//
// KrScript engine: pure JVM Kotlin module (no Android dependencies).
// Android capabilities are injected through interfaces (ShellRunner, AssetExtractor).

plugins {
    kotlin("jvm")
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

dependencies {
    // XmlPullParser implementation for the JVM (Android ships its own at runtime).
    implementation("net.sf.kxml:kxml2:2.3.0")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")

    testImplementation(kotlin("test"))
    testImplementation("junit:junit:4.13.2")
}
