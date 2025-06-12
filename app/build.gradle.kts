plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.hhst.youtubelite"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.hhst.litube"
        minSdk = 26
        targetSdk = 35
        versionCode = 17
        versionName = "1.5.2"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        """
            ndk { 
            abiFilters.addAll(listOf("arm64-v8a"));
        }
        """.trimIndent()

        ndk {
            splits {
                abi {
                    isEnable = true
                    reset()
                    include("arm64-v8a", "armeabi-v7a", "x86", "x86_64")
                    isUniversalApk = true
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
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

}
    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
    }

    dependencies {
    compileOnly(libs.lombok)
    annotationProcessor(libs.lombok)
    implementation(libs.library)
    implementation(libs.ffmpeg)
    implementation(libs.mmkv)
    implementation(libs.gson)
    implementation(libs.commons.io)
    implementation(libs.picasso)
    implementation(libs.media)
    implementation(libs.photoview)
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.constraintlayout)
    implementation(libs.swiperefreshlayout)
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
}}