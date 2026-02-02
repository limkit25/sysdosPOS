plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)

    id ("kotlin-parcelize")
    id("com.google.devtools.ksp")

    alias(libs.plugins.google.gms.google.services)
}

android {
    namespace = "com.sysdos.kasirpintar"
    compileSdk = 36
    buildFeatures {
        buildConfig = true
    }

    defaultConfig {
        applicationId = "com.sysdos.kasirpintar"
        minSdk = 24
        targetSdk = 36
        versionCode = 20
        versionName = "1.6.7"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    
    packaging {
        resources {
            excludes += "META-INF/DEPENDENCIES"
            excludes += "META-INF/LICENSE"
            excludes += "META-INF/LICENSE.txt"
            excludes += "META-INF/license.txt"
            excludes += "META-INF/NOTICE"
            excludes += "META-INF/NOTICE.txt"
            excludes += "META-INF/notice.txt"
            excludes += "META-INF/ASL2.0"
        }
    }

    // 🔥 LOGIKA GANTI NAMA APK JADI sysdosKasir.apk 🔥
    applicationVariants.all {
        outputs.all {
            val output = this as? com.android.build.gradle.internal.api.ApkVariantOutputImpl
            // Hasilnya nanti: sysdosKasir.apk
            output?.outputFileName = "sysdosPOS-V1-6-7.apk"
        }
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.firebase.auth)

    implementation("com.google.android.gms:play-services-auth:20.7.0")

    // 🔥 GOOGLE DRIVE API 🔥
    implementation("com.google.http-client:google-http-client-gson:1.43.3")
    implementation("com.google.http-client:google-http-client-android:1.43.3") // 🔥 TAMBAHAN PENTING
    implementation("com.google.api-client:google-api-client-android:2.2.0")
    implementation("com.google.apis:google-api-services-drive:v3-rev20220815-2.0.0")
    
    // Exclude Guava to prevent conflict (common issue with Drive API)
    configurations.all {
         exclude(group = "com.google.guava", module = "listenablefuture")
    }
    implementation(libs.androidx.credentials.play.services.auth)
    implementation(libs.googleid)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    // 1. ROOM DATABASE (Untuk simpan data offline)
    val room_version = "2.6.1"
    implementation("androidx.room:room-runtime:$room_version")
    implementation("androidx.room:room-ktx:$room_version") // Kotlin Extensions
    ksp("androidx.room:room-compiler:$room_version") // Ganti kapt dengan ksp jika perlu, atau pakai kapt

    // 2. COROUTINES (Untuk proses background biar HP gak nge-lag)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    // 🔥 SLIDER DASHBOARD dependency
    implementation("androidx.viewpager2:viewpager2:1.0.0")

    // 3. LIFECYCLE (Untuk MVVM)
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-livedata-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")

    // 4. GSON (Untuk convert data object ke String JSON jika perlu)
    implementation("com.google.code.gson:gson:2.10.1")

    implementation("com.journeyapps:zxing-android-embedded:4.3.0")
    implementation("com.google.zxing:core:3.4.1")
    implementation("com.github.PhilJay:MPAndroidChart:v3.1.0")
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    implementation("com.github.bumptech.glide:glide:4.16.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0") // Opsional, buat cek log
    implementation("org.mindrot:jbcrypt:0.4") // 🔥 Untuk Cek Password dari Web
}