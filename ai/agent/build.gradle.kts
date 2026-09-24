import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

dependencies {
    api(project(":core:model"))
    api(project(":workspace"))
    api(project(":ai:provider-api"))
    api(project(":ai:context"))
    api(project(":ai:tools"))
    api(project(":ai:secret"))
    api(project(":ai:patch"))
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
    testImplementation(project(":ai:providers:fake"))
    testImplementation(project(":search"))
    testImplementation(project(":diff"))
    testImplementation(project(":workspace"))
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
