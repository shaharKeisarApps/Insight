pluginManagement {
    includeBuild("build-logic")
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

rootProject.name = "Insight"

// Application
include(":app")

// Core modules
include(":core:common")
include(":core:model")
include(":core:database")
include(":core:data")
include(":core:designsystem")
include(":core:ui")
include(":core:ai")

// Feature modules
include(":feature:expenses")
include(":feature:income")
include(":feature:reports")
include(":feature:settings")
include(":feature:ai-chat")
 