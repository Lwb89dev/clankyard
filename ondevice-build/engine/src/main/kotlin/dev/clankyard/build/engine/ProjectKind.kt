package dev.clankyard.build.engine

enum class ProjectKind {
    NotGradle,
    GradleJvm,
    GradleAndroidApp,
    GradleAndroidLibrary,
    GradleUnknown,
}
