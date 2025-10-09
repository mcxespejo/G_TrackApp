plugins {
    alias(libs.plugins.android.application)
    id("com.google.android.libraries.mapsplatform.secrets-gradle-plugin")
    id("com.google.gms.google-services") // Firebase
}

android {
    namespace = "com.example.g_trackapp"
    compileSdk = 34

    // 🔹 Enable BuildConfig fields
    buildFeatures {
        buildConfig = true
    }

    defaultConfig {
        applicationId = "com.example.g_trackapp"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // 🔹 Reference Maps API key from secrets.properties
        buildConfigField(
            "String",
            "MAPS_API_KEY",
            "\"${project.findProperty("MAPS_API_KEY") ?: "AIzaSyBzbmtD5KeghuoTgNbuicm2s1is7wOXVnU"}\""
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
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.constraintlayout)
    implementation(libs.play.services.location)
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
    implementation("com.android.volley:volley:1.2.1")

    implementation("com.google.android.libraries.places:places:3.5.0")

    // Maps SDK for Android
    implementation("com.google.android.gms:play-services-maps:19.2.0")
    implementation("com.google.maps.android:android-maps-utils:2.3.0")

    // Firebase BoM
    implementation(platform("com.google.firebase:firebase-bom:34.2.0"))

    // Firebase services
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-firestore")
    implementation("com.google.firebase:firebase-analytics")
    implementation("com.google.firebase:firebase-storage:21.0.1")
    implementation("com.google.firebase:firebase-auth:22.3.0")

    implementation("com.squareup.okhttp3:okhttp:4.10.0")
    implementation("com.google.code.gson:gson:2.10.1")

    implementation ("com.github.bumptech.glide:glide:4.16.0")
    annotationProcessor ("com.github.bumptech.glide:compiler:4.16.0")

} 

secrets {
    propertiesFileName = "secrets.properties"
    defaultPropertiesFileName = "local.properties"
}
