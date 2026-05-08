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

rootProject.name = "YourTodo"

include(":app")
include(":core:model")
include(":core:ui")
include(":core:designsystem")
include(":core:network")
include(":core:database")
include(":core:datastore")
include(":core:data")
include(":core:domain")
include(":core:testing")
include(":feature:todo:api")
include(":feature:todo:impl")
include(":feature:todo:entry")
include(":feature:auth:api")
include(":feature:auth:impl")
include(":feature:auth:entry")
include(":feature:calendar:api")
include(":feature:calendar:impl")
include(":feature:calendar:entry")
include(":feature:calendar:widget")
