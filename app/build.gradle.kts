import java.io.File
import java.net.URI

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.galeriva.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.galeriva.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"
    }

    signingConfigs {
        create("release") {
            // Set via CI secrets or local env vars; falls back to debug signing when absent.
            val keystorePath = System.getenv("GALERIVA_KEYSTORE_FILE")
            if (keystorePath != null) {
                storeFile = file(keystorePath)
                storePassword = System.getenv("GALERIVA_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("GALERIVA_KEY_ALIAS")
                keyPassword = System.getenv("GALERIVA_KEY_PASSWORD")
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
            signingConfig =
                if (System.getenv("GALERIVA_KEYSTORE_FILE") != null)
                    signingConfigs.getByName("release")
                else
                    signingConfigs.getByName("debug")
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
        compose = true
    }
    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
    androidResources {
        noCompress += "onnx"
    }
}

// Large binary assets (CLIP models, brand font) are fetched at build time
// into git-ignored assets instead of being committed to the repo.
val assetsDir = file("src/main/assets")
val assetDownloads = mapOf(
    "models/clip_vision_q8.onnx" to
        "https://huggingface.co/Xenova/clip-vit-base-patch32/resolve/main/onnx/vision_model_quantized.onnx",
    "models/clip_text_q8.onnx" to
        "https://huggingface.co/Xenova/clip-vit-base-patch32/resolve/main/onnx/text_model_quantized.onnx",
    "models/clip_vocab.json" to
        "https://huggingface.co/openai/clip-vit-base-patch32/resolve/main/vocab.json",
    "models/clip_merges.txt" to
        "https://huggingface.co/openai/clip-vit-base-patch32/resolve/main/merges.txt",
    // Plus Jakarta Sans (OFL) — variable font, all weights in one file
    "fonts/plus_jakarta.ttf" to
        "https://raw.githubusercontent.com/google/fonts/main/ofl/plusjakartasans/PlusJakartaSans%5Bwght%5D.ttf"
)

val downloadAssets by tasks.registering {
    outputs.dir(assetsDir)
    doLast {
        assetDownloads.forEach { (relativePath, url) ->
            val target = assetsDir.resolve(relativePath)
            if (!target.exists() || target.length() == 0L) {
                logger.lifecycle("Downloading $relativePath ...")
                target.parentFile.mkdirs()
                val tmp = File(target.parentFile, target.name + ".part")
                URI(url).toURL().openStream().use { input ->
                    tmp.outputStream().use { output -> input.copyTo(output) }
                }
                if (!tmp.renameTo(target)) {
                    tmp.copyTo(target, overwrite = true)
                    tmp.delete()
                }
            }
        }
    }
}

tasks.named("preBuild") {
    dependsOn(downloadAssets)
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.09.03")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.core:core-splashscreen:1.0.1")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.6")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")
    implementation("androidx.navigation:navigation-compose:2.8.2")

    // Image loading
    implementation("io.coil-kt:coil-compose:2.7.0")

    // Local database (photo labels, favorites)
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    // Background indexing
    implementation("androidx.work:work-runtime-ktx:2.9.1")

    // Vault authentication (biometric / device credential)
    implementation("androidx.biometric:biometric:1.1.0")

    // On-device semantic search: CLIP via ONNX Runtime (works offline)
    implementation("com.microsoft.onnxruntime:onnxruntime-android:1.19.2")
    // On-device Indonesian -> English query translation
    implementation("com.google.mlkit:translate:17.0.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.8.1")

    testImplementation("junit:junit:4.13.2")
}
