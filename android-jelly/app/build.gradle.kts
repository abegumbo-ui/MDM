/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Same numbering idea as the other apps, but always above the old Browser's numbers so it installs over it.
val buildNumber = (System.getenv("GITHUB_RUN_NUMBER") ?: "1").toInt()

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.kapt)
}

android {
    namespace = "org.lineageos.jelly"
    compileSdk = 36

    defaultConfig {
        // The family Browser's own id: installs over (replaces) the old Browser.
        applicationId = "com.familymdm.browser"
        minSdk = 26
        targetSdk = 36
        versionCode = 1_000_000 + buildNumber
        versionName = "2.0.$buildNumber"
        // The dashboard's address, built in (DASHBOARD_URL), so connecting needs only the code.
        resValue("string", "default_server", System.getenv("DASHBOARD_URL") ?: "")
    }

    // The same key as the other family apps (the Agent lets only those talk to it).
    signingConfigs {
        create("release") {
            storeFile = file("../../android/app/release.keystore")
            storePassword = System.getenv("RELEASE_KEYSTORE_PASSWORD") ?: "unset"
            keyAlias = "mdmagentrelease"
            keyPassword = System.getenv("RELEASE_KEYSTORE_PASSWORD") ?: "unset"
        }
    }

    // The dashboard's shared rules (site lists, YouTube, word filter, picture checks).
    sourceSets["main"].java.srcDir("../../android/sitepolicy/src/main/java")
    sourceSets["main"].resources.srcDir("../../android/sitepolicy/src/main/resources")

    buildFeatures {
        resValues = true
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
            // Enables code shrinking, obfuscation, and optimization.
            isMinifyEnabled = true

            // Enables resource shrinking.
            isShrinkResources = true

            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }

        debug {
            // Append .dev to package name so we won't conflict with AOSP build.
            applicationIdSuffix = ".dev"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }
}

kapt {
    arguments {
        arg("room.schemaLocation", "$projectDir/schemas")
    }
}

dependencies {
    implementation(libs.androidx.annotation)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.cardview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.palette)
    implementation(libs.androidx.preference)
    implementation(libs.androidx.recyclerview)
    annotationProcessor(libs.androidx.room.compiler)
    kapt(libs.androidx.room.compiler)
    implementation(libs.androidx.room.ktx)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.transition)
    implementation(libs.material)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.tensorflow.lite)
    implementation(libs.androidx.webkit)
    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
}
