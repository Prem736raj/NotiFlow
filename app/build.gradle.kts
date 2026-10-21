plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Only explicitly supplied upload credentials may sign a release.
// Private ~/.gradle/gradle.properties can be used in place of environment variables.
val releaseSigningKeys = listOf("KEYSTORE_PATH", "KEYSTORE_PASSWORD", "KEY_ALIAS", "KEY_PASSWORD")
val releaseSigning = releaseSigningKeys.associateWith { key ->
    System.getenv(key)?.takeIf { it.isNotBlank() }
        ?: (project.findProperty(key) as? String)?.takeIf { it.isNotBlank() }
}
val releaseKeystore = releaseSigning["KEYSTORE_PATH"]?.let { file(it) }
val releaseCredentialsComplete = releaseSigningKeys.all { !releaseSigning[it].isNullOrBlank() }
val releaseKeystoreExists = releaseKeystore?.isFile == true

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
            if (releaseCredentialsComplete && releaseKeystoreExists) {
                storeFile = releaseKeystore
                storePassword = releaseSigning.getValue("KEYSTORE_PASSWORD")
                keyAlias = releaseSigning.getValue("KEY_ALIAS")
                keyPassword = releaseSigning.getValue("KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            signingConfig = if (releaseCredentialsComplete && releaseKeystoreExists) {
                signingConfigs.getByName("release")
            } else {
                null // Unconfigured release tasks are explicitly rejected below.
            }
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

val verifyReleaseSigning = tasks.register("verifyReleaseSigning") {
    group = "verification"
    description = "Reject release packaging unless an explicit non-debug upload keystore is configured."
    doLast {
        val missing = releaseSigningKeys.filter { releaseSigning[it].isNullOrBlank() }
        if (missing.isNotEmpty()) {
            throw GradleException(
                "Release signing requires explicit upload-key credentials: ${missing.joinToString()}. " +
                    "Configure these via environment variables or private Gradle properties."
            )
        }
        val keystoreFile = releaseKeystore
        if (keystoreFile == null || !keystoreFile.isFile) {
            throw GradleException("Release signing keystore is missing or not a file. Verify KEYSTORE_PATH.")
        }
        val debugKeystore = android.signingConfigs.getByName("debug").storeFile
        if (keystoreFile.name.equals("debug.keystore", ignoreCase = true) ||
            (debugKeystore != null && keystoreFile.canonicalFile == debugKeystore.canonicalFile)
        ) {
            throw GradleException("Refusing to sign a release with the Android Debug keystore.")
        }
    }
}

// Debug builds remain available without release credentials. All release packaging
// entry points enforce the signing prerequisite before producing an artifact.
tasks.matching { task ->
    task.name in setOf(
        "preReleaseBuild", "bundleRelease", "assembleRelease", "packageRelease",
        "packageReleaseBundle", "signReleaseBundle"
    )
}.configureEach {
    dependsOn(verifyReleaseSigning)
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
