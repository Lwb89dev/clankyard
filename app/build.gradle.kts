plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

android {
    namespace = "dev.clankyard.app"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "dev.clankyard.app"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }

    buildFeatures {
        compose = true
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    packaging {
        resources {
            excludes += "META-INF/DEPENDENCIES"
            excludes += "META-INF/LICENSE"
            excludes += "META-INF/LICENSE.txt"
            excludes += "META-INF/NOTICE"
            excludes += "META-INF/NOTICE.txt"
        }
    }
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:common"))
    implementation(project(":core:ui"))
    implementation(project(":core:security"))
    implementation(project(":workspace"))
    implementation(project(":editor"))
    implementation(project(":search"))
    implementation(project(":diff"))
    implementation(project(":git"))
    implementation(project(":ai:patch"))
    implementation(project(":ai:tools"))
    implementation(project(":ai:agent"))
    implementation(project(":ai:secret"))
    implementation(project(":ai:provider-api"))
    implementation(project(":ai:providers:openai"))
    implementation(project(":ai:providers:anthropic"))
    implementation(project(":ai:providers:xai"))
    implementation(project(":ai:providers:openai-compatible"))
    implementation(project(":terminal:api"))
    implementation(project(":terminal:local"))
    implementation(project(":terminal:ssh"))
    implementation(project(":feature:workspace-picker"))
    implementation(project(":feature:explorer"))
    implementation(project(":feature:search"))
    implementation(project(":feature:git"))
    implementation(project(":feature:diff"))
    implementation(project(":feature:terminal"))
    implementation(project(":feature:settings"))
    implementation(project(":feature:clanker"))
    implementation(libs.okhttp)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.navigation.compose)
    coreLibraryDesugaring(libs.desugar.jdk)
    testImplementation(libs.junit)
    androidTestImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.kotlinx.coroutines.android)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}

tasks.register("licenseCheck") {
    group = "verification"
    description = "Fail if GPL Termux artifacts are declared in Gradle files."
    doLast {
        val root = rootProject.projectDir
        val needles = listOf(
            listOf("termux", "shared").joinToString("-"),
            listOf("termux", "app").joinToString("-"),
            listOf("com.termux", "termux").joinToString(":"),
        )
        val hits = ArrayList<String>()
        val scanned = ArrayList<String>()
        root.walkTopDown()
            .onEnter { dir -> dir.name != "build" && dir.name != ".gradle" && dir.name != ".git" }
            .filter { file ->
                file.isFile && (
                    file.name.endsWith(".gradle.kts") ||
                        file.name.endsWith(".gradle") ||
                        file.name == "libs.versions.toml"
                    )
            }
            .forEach { file ->
                val rel = file.relativeTo(root).path
                scanned += rel
                val text = file.readText()
                for (needle in needles) {
                    if (needle in text) hits += "$rel: $needle"
                }
            }
        check(hits.isEmpty()) { "Forbidden GPL Termux coordinates:\n${hits.joinToString("\n")}" }
        val catalog = File(root, "gradle/libs.versions.toml").readText()
        check("sora-editor" in catalog) { "sora-editor pin missing from version catalog" }
        val notice = File(root, "NOTICE").readText()
        check("sora-editor" in notice && "LGPL-2.1" in notice) { "NOTICE missing sora-editor LGPL" }
        val report = layout.buildDirectory.file("reports/license-check.txt").get().asFile
        report.parentFile.mkdirs()
        report.writeText(scanned.sorted().joinToString("\n") + "\n")
    }
}

tasks.named("check").configure { dependsOn("licenseCheck") }
