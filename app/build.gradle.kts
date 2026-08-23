plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "io.github.jiangjinyue2333.actiongrid"
    compileSdk = 37
    buildToolsVersion = "37.0.0"

    defaultConfig {
        applicationId = "io.github.jiangjinyue2333.actiongrid"
        minSdk = 34
        targetSdk = 37
        versionCode = 15
        versionName = "0.3"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    buildFeatures {
        buildConfig = true
    }

    packaging {
        resources {
            merges += "META-INF/xposed/*"
        }
    }

    lint {
        abortOnError = true
        checkReleaseBuilds = true
    }
}

dependencies {
    implementation(project(":hook"))
    implementation(libs.libxposed.service)
    compileOnly(libs.libxposed.api)
    testImplementation(libs.junit)
    testImplementation(libs.json)
}
