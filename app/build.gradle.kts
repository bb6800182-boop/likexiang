plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.c520.likexiang"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.c520.likexiang"
        // Android 13 起才有 USE_EXACT_ALARM 與通知權限，從 33 起跳可省掉舊版相容分支
        minSdk = 33
        targetSdk = 36
        versionCode = 6
        versionName = "1.1.0"
    }

    // 正式版簽章：金鑰路徑與密碼放在使用者的 ~/.gradle/gradle.properties（calendarAlarm.*），不進版控。
    // 沒有設定的電腦照樣能編 debug 版，只是編不出簽好章的正式版。
    val releaseStore = providers.gradleProperty("calendarAlarm.storeFile").orNull
    signingConfigs {
        if (releaseStore != null) {
            create("release") {
                storeFile = file(releaseStore)
                storePassword = providers.gradleProperty("calendarAlarm.storePassword").get()
                keyAlias = providers.gradleProperty("calendarAlarm.keyAlias").get()
                keyPassword = providers.gradleProperty("calendarAlarm.keyPassword").get()
            }
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.findByName("release")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
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

    lint {
        // 資安相關的 lint 一律當錯誤
        error += listOf(
            "ExportedReceiver", "ExportedService", "UnsafeImplicitIntentLaunch",
            "MutableImplicitPendingIntent", "UnspecifiedRegisterReceiverFlag",
        )
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.glance.appwidget)

    testImplementation(libs.junit)
}
