plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.compose.compiler)
}

android {
    namespace = "dev.clankyard.feature.clanker"
    compileSdk = libs.versions.compileSdk.get().toInt()
    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()
        consumerProguardFiles("consumer-rules.pro")
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }
    buildFeatures { compose = true }
}

dependencies {
    api(project(":ai:agent"))
    api(project(":ai:patch"))
    api(project(":ai:context"))
    api(project(":ai:secret"))
    api(project(":ai:tools"))
    api(project(":ai:provider-api"))
    api(project(":feature:settings"))
    implementation(project(":core:ui"))
    implementation(project(":core:security"))
    implementation(project(":workspace"))
    implementation(project(":git"))
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.kotlinx.coroutines.android)
    coreLibraryDesugaring(libs.desugar.jdk)
}
