plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "io.github.jiangjinyue2333.actiongrid.hook"
    compileSdk = 37
    buildToolsVersion = "37.0.0"

    defaultConfig {
        minSdk = 34
        consumerProguardFiles("consumer-rules.pro")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    lint {
        abortOnError = true
        checkReleaseBuilds = true
    }
}

dependencies {
    compileOnly(libs.libxposed.api)
    testImplementation(libs.junit)
}
