plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.hilt.android)
    alias(libs.plugins.kotlin.kapt)
}

val serverBaseUrlProperty = providers.gradleProperty("yourtodo.serverBaseUrl")
val aiServerBaseUrlProperty = providers.gradleProperty("yourtodo.aiServerBaseUrl")
val placeholderDevBaseUrl = "https://yourtodo-dev.example.invalid/"

fun quotedBuildConfigString(value: String) = "\"$value\""

android {
    namespace = "com.neo.yourtodo.core.network"
    compileSdk = 36

    defaultConfig {
        minSdk = 24
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        debug {
            buildConfigField(
                "String",
                "YOURTODO_SERVER_BASE_URL",
                quotedBuildConfigString(
                    serverBaseUrlProperty.orElse(placeholderDevBaseUrl).get()
                )
            )
            buildConfigField(
                "String",
                "YOURTODO_AI_SERVER_BASE_URL",
                quotedBuildConfigString(
                    aiServerBaseUrlProperty.orElse(serverBaseUrlProperty).orElse(placeholderDevBaseUrl).get()
                )
            )
        }
        release {
            buildConfigField(
                "String",
                "YOURTODO_SERVER_BASE_URL",
                quotedBuildConfigString(serverBaseUrlProperty.orElse("").get())
            )
            buildConfigField(
                "String",
                "YOURTODO_AI_SERVER_BASE_URL",
                quotedBuildConfigString(
                    aiServerBaseUrlProperty.orElse(serverBaseUrlProperty).orElse("").get()
                )
            )
        }
    }

    buildFeatures {
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging.interceptor)
    implementation(libs.retrofit)
    implementation(libs.retrofit.kotlinx.serialization)
    implementation(libs.hilt.android)
    kapt(libs.hilt.compiler)

    testImplementation(libs.junit)
    testImplementation(libs.truth)
    testImplementation(libs.kotlinx.coroutines.test)

    androidTestImplementation(libs.androidx.test.runner)
}

kapt {
    correctErrorTypes = true
}

tasks.matching { it.name == "preReleaseBuild" }.configureEach {
    doFirst {
        check(serverBaseUrlProperty.isPresent) {
            "Release builds require -Pyourtodo.serverBaseUrl=https://<server-host>/"
        }
    }
}
