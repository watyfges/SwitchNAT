plugins {
    id("com.android.application")
    // The Flutter Gradle Plugin must be applied after the Android and Kotlin Gradle plugins.
    id("dev.flutter.flutter-gradle-plugin")
}

android {
    namespace = "dev.switchnat.app"
    compileSdk = flutter.compileSdkVersion
    ndkVersion = flutter.ndkVersion

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    defaultConfig {
        applicationId = "dev.switchnat.app"
        minSdk = 30
        targetSdk = flutter.targetSdkVersion
        versionCode = flutter.versionCode
        versionName = flutter.versionName
    }

    val releaseSigningConfig = System.getenv("ANDROID_KEYSTORE_FILE")
        ?.takeIf { it.isNotBlank() }
        ?.let { keystorePath ->
            val keystoreFile = file(keystorePath)
            check(keystoreFile.isFile) {
                "Release keystore does not exist: $keystorePath"
            }
            signingConfigs.create("release") {
                storeFile = keystoreFile
                storePassword = requireNotNull(System.getenv("ANDROID_KEYSTORE_PASSWORD")) {
                    "ANDROID_KEYSTORE_PASSWORD is required for release signing"
                }
                keyAlias = requireNotNull(System.getenv("ANDROID_KEY_ALIAS")) {
                    "ANDROID_KEY_ALIAS is required for release signing"
                }
                keyPassword = requireNotNull(System.getenv("ANDROID_KEY_PASSWORD")) {
                    "ANDROID_KEY_PASSWORD is required for release signing"
                }
            }
        }

    buildTypes {
        release {
            signingConfig = releaseSigningConfig ?: signingConfigs.getByName("debug")
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.17.0")
    implementation(files("libs/libv2ray.aar"))
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20250517")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
}

kotlin {
    compilerOptions {
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17
    }
}

flutter {
    source = "../.."
}
