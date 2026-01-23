plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)

    id("com.google.gms.google-services")

    id("kotlin-parcelize")
}

android {
    namespace = "com.example.mobiliyum"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.mobiliyum"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

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
    buildFeatures {
        viewBinding = true
        compose = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.15"
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.swiperefreshlayout)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)

    implementation("com.google.android.material:material:1.11.0")

    implementation("com.github.bumptech.glide:glide:4.16.0")

    implementation(platform("com.google.firebase:firebase-bom:34.6.0"))

    implementation("com.google.firebase:firebase-analytics")

    implementation("com.google.firebase:firebase-auth") // Giriş işlemleri için
    implementation("com.google.firebase:firebase-firestore") // Veritabanı için

    implementation("com.google.code.gson:gson:2.10.1")
// Glide (Resim yüklemek için, reklamda lazım olacak)
    implementation("com.github.bumptech.glide:glide:4.16.0")

    // SceneView CORE (ZORUNLU)
    implementation("io.github.sceneview:sceneview:2.3.3")

    // SceneView AR (ZORUNLU)
    implementation("io.github.sceneview:arsceneview:2.3.3")

    // ARCore
    implementation("com.google.ar:core:1.41.0")

    // Compose core
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.compose.ui:ui:1.6.3")
    implementation("androidx.compose.material:material:1.6.3")
    implementation("androidx.compose.ui:ui-tooling-preview:1.6.3")

    debugImplementation("androidx.compose.ui:ui-tooling:1.6.3")

    implementation(platform("org.jetbrains.kotlin:kotlin-bom:2.2.21"))
}