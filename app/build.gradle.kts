import java.util.Properties
import java.io.FileInputStream

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.jetbrains.kotlin.android)
    kotlin("plugin.serialization") version "2.1.0"
}

// Загружаем local.properties
val localProperties = Properties()
val localPropertiesFile = rootProject.file("local.properties")
if (localPropertiesFile.exists()) {
    localProperties.load(FileInputStream(localPropertiesFile))
}

android {
    namespace = "lv.zakon.tv.animevost"
    //noinspection GradleDependency, android 11 compatability
    compileSdk = 35

    defaultConfig {
        applicationId = "lv.zakon.tv.animevost"
        minSdk = 30
        targetSdk = 34
        versionCode = 1
        versionName = "0.641"
        resValue("string", "app_version", "\"v$versionName\"")
    }

    signingConfigs {
        create("release") {
            // Берем значения из local.properties
            val keystoreFile = localProperties.getProperty("release.keystore")
            if (keystoreFile != null) {
                storeFile = file(keystoreFile)
                storePassword = localProperties.getProperty("release.keystore.password")
                keyAlias = localProperties.getProperty("release.key.alias")
                keyPassword = localProperties.getProperty("release.key.password")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            // Подключаем конфиг подписи только если данные найдены
            if (localProperties.getProperty("release.keystore") != null) {
                signingConfig = signingConfigs.getByName("release")
            }
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.leanback)
    implementation(libs.glide)
    implementation(libs.jsoup)
    implementation(libs.coroutines)
    implementation(libs.lifecycle)
    implementation(libs.ktorcore)
    implementation(libs.ktorcio)
    implementation(libs.kotds)
    implementation(libs.kotdsinit)
    implementation(libs.constraintlayout)
    implementation(libs.exoplayer)
    implementation(libs.media3.ui)
    implementation("com.google.android.gms:play-services-auth:21.2.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
}
