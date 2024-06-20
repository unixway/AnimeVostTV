plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.jetbrains.kotlin.android)
}

android {
    namespace = "lv.zakon.tv.animevost"
    //noinspection GradleDependency, android 11 compatability
    compileSdk = 30

    defaultConfig {
        applicationId = "lv.zakon.tv.animevost"
        minSdk = 30
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

    }

    buildTypes {
        release {
            isMinifyEnabled = false
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
    implementation(libs.eventbus)
    implementation(libs.lifecycle)
    implementation(libs.ktorcore)
    implementation(libs.ktorcio)
    implementation(libs.kotds)
    implementation(libs.kotdsinit)
    implementation(libs.constraintlayout)
}