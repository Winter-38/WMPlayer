plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.winter.muplayer"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.winter.muplayer"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "0.6.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            storeFile = rootProject.file("wmplayer-key.jks")
            val localFile = rootProject.file("local.properties")
            fun localProp(key: String): String = localFile.takeIf { it.exists() }
                ?.readLines()
                ?.firstOrNull { it.startsWith("$key=") }
                ?.substringAfter("=")
                ?.trim()
                ?: error("$key not set in local.properties")
            storePassword = localProp("keystore.storePassword")
            keyAlias = localProp("keystore.keyAlias")
            keyPassword = localProp("keystore.keyPassword")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }

}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation("androidx.profileinstaller:profileinstaller:1.4.0")
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(project(":model"))
    implementation(project(":ui"))
    implementation(project(":config"))
    implementation(project(":core"))
    implementation(project(":plugin"))
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}

afterEvaluate {
    tasks.named("assembleRelease") {
        doLast {
            val sourceDir = layout.buildDirectory.dir("outputs/apk/release").get().asFile
            val outputDir = rootProject.projectDir.resolve("release")
            outputDir.mkdirs()

            val signedApk = sourceDir.listFiles { f ->
                f.name.endsWith(".apk") && !f.name.contains("-unsigned") && !f.name.contains("-unaligned")
            }?.maxByOrNull { f -> f.lastModified() }

            val unsignedApk = sourceDir.listFiles { f ->
                f.name.endsWith("-unsigned.apk")
            }?.maxByOrNull { f -> f.lastModified() }

            val apkFile = signedApk ?: unsignedApk

            if (apkFile != null) {
                val versionName = android.defaultConfig.versionName ?: "unknown"
                val isUnsigned = apkFile.name.contains("-unsigned")
                val suffix = if (isUnsigned) "-unsigned" else ""
                val destName = "WinterMuPlayer-${versionName}${suffix}.apk"
                apkFile.copyTo(outputDir.resolve(destName), overwrite = true)
                if (isUnsigned) {
                    logger.warn("⚠️ 未找到 signed APK，复制了 unsigned 版本（此 APK 无法直接安装到设备）")
                } else {
                    logger.lifecycle("✅ Release APK 已分发到: ${outputDir.resolve(destName)}")
                }
            }
        }
    }
}
