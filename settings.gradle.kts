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

rootProject.name = "Relayo"

include(":app")
include(":core:mesh")
include(":core:transport")
include(":core:crypto")
include(":domain")
include(":data")
include(":feature:meshstatus")
include(":feature:newsfeed")
include(":feature:alerts")
include(":feature:messages")
include(":feature:voicenotes")
include(":feature:qrboards")
