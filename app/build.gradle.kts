plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

// 固定签名 release：keystore.p12（项目根目录，PKCS12）+ 三个环境变量都在时才用自己的签名；
// 否则 release 回落 debug 签名，保证任何人 clone 下来都能编过。
val releaseKeystore = rootProject.file("keystore.p12")
val signStorePassword = System.getenv("SIGNING_STORE_PASSWORD")
val signKeyAlias = System.getenv("SIGNING_KEY_ALIAS")
val signKeyPassword = System.getenv("SIGNING_KEY_PASSWORD")
val canSignRelease = releaseKeystore.exists() &&
    !signStorePassword.isNullOrBlank() &&
    !signKeyAlias.isNullOrBlank() &&
    !signKeyPassword.isNullOrBlank()

android {
    namespace = "com.cravesaver"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.cravesaver"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
    }

    signingConfigs {
        if (canSignRelease) {
            create("release") {
                storeFile = releaseKeystore
                storePassword = signStorePassword
                keyAlias = signKeyAlias
                keyPassword = signKeyPassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            // 有正式签名材料用正式签名，否则回落 debug 签名
            signingConfig = if (canSignRelease) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
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
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.okhttp)
    implementation(libs.androidx.work.runtime.ktx)
    debugImplementation(libs.androidx.compose.ui.tooling)
    testImplementation(libs.junit)
}
