import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
}

// Keep publishing credentials in the ignored, local-only configuration file.
val releaseSigningFile = rootProject.file("keystore.properties")
val releaseSigning = if (releaseSigningFile.isFile) {
    Properties().apply {
        releaseSigningFile.reader(Charsets.UTF_8).use { load(it) }
    }
} else null

fun releaseSigningProperty(name: String): String =
    releaseSigning?.getProperty(name)?.takeIf { it.isNotBlank() }
        ?: throw GradleException("Missing '$name' in local keystore.properties.")

android {
    namespace = "com.changyu.ochat"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.changyu.ochat"
        minSdk = 24
        targetSdk = 37
        versionCode = 3
        versionName = "1.1.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    if (releaseSigning != null) {
        signingConfigs {
            create("release") {
                val keystore = rootProject.file(releaseSigningProperty("storeFile"))
                if (!keystore.isFile) {
                    throw GradleException("Release keystore not found. Check storeFile in local keystore.properties.")
                }
                storeFile = keystore
                storePassword = releaseSigningProperty("storePassword")
                keyAlias = releaseSigningProperty("keyAlias")
                keyPassword = releaseSigningProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            if (releaseSigning != null) {
                signingConfig = signingConfigs.getByName("release")
            }
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

// Keep versioned release artifacts outside build/, which Gradle cleans during packaging.
val archiveVersionName = android.defaultConfig.versionName
tasks.register<Copy>("archiveReleaseApk") {
    dependsOn("assembleRelease")
    from(layout.buildDirectory.file("outputs/apk/release/app-release.apk"))
    into(rootProject.layout.projectDirectory.dir("release-artifacts"))
    rename { "OChat-$archiveVersionName.apk" }
    doFirst {
        if (releaseSigning == null) {
            throw GradleException("Local release signing is required to archive a release APK.")
        }
    }
}