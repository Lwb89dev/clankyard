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
include(":core:security")
include(":workspace")
include(":search")
include(":diff")
include(":git")
include(":ai:patch")
include(":feature:workspace-picker")
include(":feature:git")
include(":feature:diff")
include(":ai:provider-api")
include(":ai:providers:fake")
include(":ai:providers:openai")
include(":ai:providers:anthropic")
include(":ai:providers:xai")
include(":ai:providers:openai-compatible")
include(":ai:secret")
include(":ai:context")
include(":ai:tools")
