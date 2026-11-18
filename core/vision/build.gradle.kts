import java.net.URI
import java.security.MessageDigest

plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("com.google.dagger.hilt.android")
    id("com.google.devtools.ksp")
}

val modelUrl = "https://storage.googleapis.com/mediapipe-models/hand_landmarker/hand_landmarker/float16/1/hand_landmarker.task"
val modelSha256 = "fbc2a30080c3c557093b5ddfc334698132eb341044ccee322ccf8bcf3607cde1"
val modelAssets = layout.buildDirectory.dir("generated/handLandmarker/assets")
val prepareHandLandmarker by tasks.registering {
    val asset = modelAssets.map { it.file("hand_landmarker.task") }
    inputs.property("modelUrl", modelUrl)
    inputs.property("modelSha256", modelSha256)
    outputs.file(asset)

    doLast {
        fun checksum(file: File): String =
            MessageDigest.getInstance("SHA-256").digest(file.readBytes())
                .joinToString("") { "%02x".format(it) }

        val model = asset.get().asFile
        if (model.isFile && checksum(model) == modelSha256) return@doLast

        model.parentFile.mkdirs()
        val download = File.createTempFile("hand_landmarker", ".task", model.parentFile)
        try {
            val connection = URI(modelUrl).toURL().openConnection().apply {
                connectTimeout = 30_000
                readTimeout = 60_000
            }
            connection.getInputStream().use { input ->
                download.outputStream().use { output -> input.copyTo(output) }
            }
            check(checksum(download) == modelSha256) { "Hand Landmarker model checksum mismatch" }
            download.copyTo(model, overwrite = true)
        } finally {
            download.delete()
        }
    }
}

android {
    sourceSets["main"].assets.srcDir(modelAssets)

    namespace = "com.visionboard.vision"
    compileSdk = 35

    defaultConfig {
        minSdk = 28

        consumerProguardFiles(
            "consumer-rules.pro"
        )
    }

    buildTypes {
        release {
            isMinifyEnabled = false
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

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

tasks.named("preBuild").configure { dependsOn(prepareHandLandmarker) }

dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:geometry"))

    implementation("org.opencv:opencv:4.9.0")
    implementation("com.google.mediapipe:tasks-vision:0.10.18")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("androidx.core:core-ktx:1.13.1")

    implementation("com.google.dagger:hilt-android:2.51.1")
    ksp("com.google.dagger:hilt-android-compiler:2.51.1")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
}
