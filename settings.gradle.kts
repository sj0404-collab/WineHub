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

rootProject.name = "WineNativeHub"
include(
    ":app",
    ":core",
    ":runtime",
    ":wine-layer",
    ":box64-layer",
    ":container-layer",
    ":graphics-vulkan",
    ":dxvk-system",
    ":ai-engine",
    ":native-bridge",
    ":downloader",
    ":ui-compose"
)
