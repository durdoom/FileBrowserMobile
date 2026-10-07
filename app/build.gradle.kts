import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "ru.filebrowser.mobile"
    compileSdk = 35

    defaultConfig {
        applicationId = "ru.filebrowser.mobile"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    signingConfigs {
        create("release") {
            val keystorePropsFile = rootProject.file("keystore.properties")
            if (keystorePropsFile.exists()) {
                val props = Properties()
                props.load(keystorePropsFile.inputStream())
                val storeFilePath = props.getProperty("storeFile") ?: "filebrowser-mobile-release.jks"
                storeFile = rootProject.file(storeFilePath)
                storePassword = props.getProperty("storePassword") ?: ""
                keyAlias = props.getProperty("keyAlias") ?: "filebrowser-mobile"
                keyPassword = props.getProperty("keyPassword") ?: ""
                println("? Signing: using keystore ${storeFile?.absolutePath}")
            } else {
                println("?? Signing: keystore.properties NOT FOUND at ${keystorePropsFile.absolutePath}")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            val hasKeystore = rootProject.file("keystore.properties").exists()
            println("?? Build type release: hasKeystore=$hasKeystore")
            signingConfig = if (hasKeystore) {
                signingConfigs.getByName("release")
            } else {
                println("?? Falling back to debug signing")
                signingConfigs.getByName("debug")
            }
        }
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        viewBinding = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.google.material)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.swiperefreshlayout)
    implementation(libs.androidx.webkit)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.kotlinx.coroutines.android)
}