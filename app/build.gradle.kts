import java.util.Properties
import org.gradle.api.GradleException

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    kotlin("plugin.serialization") version "1.9.20"
}

val apiKey: String = run {
    println("Gradle: Attempting to load API key...")
    val props = Properties()
    val propsFile = rootProject.file("local.properties")

    if (propsFile.exists()) {
        println("Gradle: local.properties file found.")
        propsFile.inputStream().use { input ->
            props.load(input)
        }
        val loadedApiKey = props.getProperty("OPEN_WEATHER_MAP_API_KEY")
        println("Gradle: Value of 'OPEN_WEATHER_MAP_API_KEY' from props: [${loadedApiKey}]")

        if (loadedApiKey.isNullOrEmpty()) {
            println("Gradle: Error - API key is null or empty in local.properties.")
            throw GradleException("API key 'OPEN_WEATHER_MAP_API_KEY' not found or is empty in local.properties.")
        }
        println("Gradle: API key loaded successfully: [${loadedApiKey}]")
        loadedApiKey // This is returned by the run block
    } else {
        println("Gradle: Error - local.properties file not found.")
        throw GradleException("local.properties file not found. Please create it and add OPEN_WEATHER_MAP_API_KEY.")
    }
}

println("Gradle: Final apiKey value before Android block: [${apiKey}]")

val ktorVersion = "2.3.7"

android {
    namespace = "com.example.myweatherapp"
    compileSdk = 35

    buildFeatures {
        buildConfig = true
        viewBinding = true
    }

    defaultConfig {
        applicationId = "com.example.myweatherapp"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        println("Gradle: About to set buildConfigField with API_KEY: [${apiKey}]")
        if (apiKey.isEmpty()) {
            throw GradleException("Critical Error: apiKey is empty just before buildConfigField. This should not happen.")
        }
        buildConfigField("String", "API_KEY", "\"$apiKey\"")
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
}

dependencies {
    implementation("com.airbnb.android:lottie:6.3.0")
    implementation("io.coil-kt.coil3:coil:3.0.0-alpha08")
    implementation("io.coil-kt.coil3:coil-network-ktor:3.0.0-alpha08")
    testImplementation("junit:junit:4.13.2")
    testImplementation("io.mockk:mockk:1.13.10")
    testImplementation("androidx.arch.core:core-testing:2.2.0")
    implementation("com.google.android.gms:play-services-location:21.2.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-livedata-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("io.ktor:ktor-client-core:$ktorVersion")
    implementation("io.ktor:ktor-client-android:$ktorVersion")
    implementation("io.ktor:ktor-client-content-negotiation:$ktorVersion")
    implementation("io.ktor:ktor-serialization-kotlinx-json:$ktorVersion")
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}