pluginManagement {
    includeBuild("build-logic")
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

rootProject.name = "prismarin"

include(":api")
include(":shared")
include(":core-paper")
include(":core-velocity")
include(":lobby-paper")
