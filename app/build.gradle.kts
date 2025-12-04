plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.gms.google-services") // CRÍTICO PARA FIREBASE
}

android {
    namespace = "com.borcele.pestillointeligente"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.borcele.pestillointeligente"
        minSdk = 24
        targetSdk = 33
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
}

dependencies {
    // --- ANDROID CORE (Versiones Estándar Hardcodeadas) ---
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.activity:activity-ktx:1.8.2")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")

    // --- FIREBASE (MODO SEGURO - TEXTO PLANO) ---
    implementation(platform("com.google.firebase:firebase-bom:32.7.4")) // BOM estable
    implementation("com.google.firebase:firebase-analytics")
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-firestore")

    // --- MQTT (LA SOLUCIÓN DE HANNESA2) ---
    implementation("com.github.hannesa2:paho.mqtt.android:3.6.4")
    implementation("androidx.legacy:legacy-support-v4:1.0.0")

    // --- TESTING ---
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
}
configurations.all {
    exclude(group = "org.eclipse.paho", module = "org.eclipse.paho.android.service")
}