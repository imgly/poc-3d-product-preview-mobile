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
        maven {
            name = "IMG.LY Artifactory"
            url = uri("https://maven.img.ly/maven")
            mavenContent { includeGroup("ly.img") }
        }
    }
}

rootProject.name = "product-preview"
include(":app")
