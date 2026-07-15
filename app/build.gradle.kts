import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

val localProps = Properties()
val localPropsFile = rootProject.file("local.properties")
if (localPropsFile.exists()) {
    localPropsFile.inputStream().use { localProps.load(it) }
}

android {
    namespace = "jp.developer.bbee.featuredemo"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    // 優先順: local.properties → gradle.properties / -PMAPS_API_KEY → 環境変数
    val mapsApiKey = localProps.getProperty("MAPS_API_KEY")
        ?: providers.gradleProperty("MAPS_API_KEY").orNull
        ?: providers.environmentVariable("MAPS_API_KEY").orNull
        ?: ""
    if (mapsApiKey.isEmpty()) {
        logger.warn(
            "warning: MAPS_API_KEY is not set. Google Maps will not render. " +
                "Set it in local.properties, gradle.properties, -PMAPS_API_KEY, " +
                "or the MAPS_API_KEY environment variable."
        )
    }

    defaultConfig {
        applicationId = "jp.developer.bbee.featuredemo"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        manifestPlaceholders["MAPS_API_KEY"] = mapsApiKey
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
    // local.properties にキーストア情報が揃っている場合のみ debug 署名を上書きする。
    // 未設定の環境(CI や新規 clone)では Android SDK 標準の debug keystore が使われる
    val keystorePath = localProps.getProperty("KEYSTORE_PATH")
    val keystorePassword = localProps.getProperty("KEYSTORE_PASSWORD")
    val keystoreAlias = localProps.getProperty("KEY_ALIAS")
    val keystoreKeyPassword = localProps.getProperty("KEY_PASSWORD")
    if (
        !keystorePath.isNullOrBlank() &&
        !keystorePassword.isNullOrBlank() &&
        !keystoreAlias.isNullOrBlank() &&
        !keystoreKeyPassword.isNullOrBlank()
    ) {
        val keystoreFile = rootProject.file(keystorePath)
        if (keystoreFile.exists()) {
            signingConfigs {
                getByName("debug") {
                    storeFile = keystoreFile
                    storePassword = keystorePassword
                    keyAlias = keystoreAlias
                    keyPassword = keystoreKeyPassword
                }
            }
        } else {
            logger.warn(
                "warning: KEYSTORE_PATH points to ${keystoreFile.absolutePath}, " +
                    "which does not exist. Falling back to the default debug keystore."
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
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.androidx.lifecycle.viewmodel.navigation3)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.lifecycle.viewmodel.compose)
    implementation(libs.kotlinx.serialization.core)
    implementation(libs.androidx.biometric)
    implementation(libs.androidx.heifwriter)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(libs.androidx.camera.mlkit.vision)
    implementation(libs.mlkit.barcode.scanning)
    implementation(libs.mlkit.text.recognition)
    implementation(libs.mlkit.face.detection)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.play.services.location)
    implementation(libs.maps.compose)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}