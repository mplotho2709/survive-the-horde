pluginManagement {
    repositories {
        google()
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

rootProject.name = "TriathlonPlanner"

include(
    ":app",
    ":core:model",
    ":core:database",
    ":core:designsystem",
    ":domain:zones",
    ":domain:planengine",
    ":data:healthconnect",
    ":data:repository",
    ":feature:onboarding",
    ":feature:today",
    ":feature:plan",
    ":feature:progress",
    ":feature:profile",
)
