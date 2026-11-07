plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.changyu.ochat"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.changyu.ochat"
        minSdk = 24
        targetSdk = 37
        versionCode = 2
        versionName = "1.14.514"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(libs.androidx.appcompat)
    implementation("androidx.viewpager2:viewpager2:1.1.0")
    implementation(libs.androidx.core.ktx)
    implementation(libs.material)
    implementation(libs.okhttp)
    implementation("io.noties.markwon:core:4.6.2")
    implementation("io.noties.markwon:ext-tables:4.6.2")
    implementation("io.noties.markwon:ext-strikethrough:4.6.2")
    implementation("io.noties.markwon:ext-latex:4.6.2")
    implementation("io.noties.markwon:inline-parser:4.6.2")
    testImplementation(libs.junit)
    testImplementation("org.json:json:20260814")
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
}