pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "clankyard"

include(":app")
include(":core:model")
include(":core:common")
include(":core:ui")
include(":workspace")
include(":editor")
include(":search")
include(":feature:workspace-picker")
include(":feature:explorer")
include(":feature:search")
