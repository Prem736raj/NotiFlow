plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.premraj.notiflow"
    compileSdk {
        version = release(37) {
            minorApiLevel = 0
        }
    }

    defaultConfig {
        applicationId = "com.premraj.notiflow"
        minSdk = 24
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            val keystorePath = System.getenv("KEYSTORE_PATH") ?: (project.findProperty("KEYSTORE_PATH") as? String)
            val keystorePassword = System.getenv("KEYSTORE_PASSWORD") ?: (project.findProperty("KEYSTORE_PASSWORD") as? String)
            val keyAlias = System.getenv("KEY_ALIAS") ?: (project.findProperty("KEY_ALIAS") as? String)
            val keyPassword = System.getenv("KEY_PASSWORD") ?: (project.findProperty("KEY_PASSWORD") as? String)

            if (!keystorePath.isNullOrBlank() && file(keystorePath).exists()) {
                storeFile = file(keystorePath)
                storePassword = keystorePassword
                this.keyAlias = keyAlias
                this.keyPassword = keyPassword
            } else {
                // Fallback to debug keystore so bundleRelease and assembleRelease produce a valid signed release artifact in local / CI builds
                val debugConfig = getByName("debug")
                storeFile = debugConfig.storeFile
                storePassword = debugConfig.storePassword
                this.keyAlias = debugConfig.keyAlias
                this.keyPassword = debugConfig.keyPassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2026.09.00")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.core:core-ktx:1.19.0")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.11.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.11.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.11.0")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.work:work-runtime-ktx:2.12.0")

    // MediaPipe tasks-genai native dependency removed to reduce APK download size from ~69 MB to ~15 MB.
    // GemmaClassifier provides a clean offline rules-first fallback stub.

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
    androidTestImplementation("androidx.test:core-ktx:1.6.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
}
