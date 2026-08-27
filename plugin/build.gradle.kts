plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.winter.muplayer.plugin"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        minSdk = 24

        consumerProguardFiles("consumer-rules.pro")
    }

    buildTypes {
        release {
            isMinifyEnabled = true
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
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(project(":core"))
    implementation(project(":model"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation(libs.okhttp)
    implementation(libs.luaj.jse)

    // 单元测试：JUnit + org.json（JVM 版，替代 Android stub 的 org.json）
    testImplementation(libs.junit)
    testImplementation("org.json:json:20240303")
}
