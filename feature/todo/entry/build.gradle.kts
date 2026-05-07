plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.hilt.android)
    alias(libs.plugins.kotlin.kapt)
}

android {
    namespace = "com.neo.yourtodo.feature.todo.entry"
    compileSdk = 36

    defaultConfig {
        minSdk = 24
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation(project(":core:ui"))
    implementation(project(":feature:todo:api"))
    implementation(project(":feature:todo:impl"))

    implementation(libs.hilt.android)
    kapt(libs.hilt.compiler)
    coreLibraryDesugaring(libs.desugar.jdk.libs)

    androidTestImplementation(libs.androidx.test.runner)
}

kapt {
    correctErrorTypes = true
}
