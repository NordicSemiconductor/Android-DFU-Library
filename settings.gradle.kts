@Suppress("UnstableApiUsage")
pluginManagement {
    repositories {
        mavenLocal()
        google {
            content {
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
                includeGroupAndSubgroups("androidx")
            }
        }
        gradlePluginPortal {
            content {
                includeGroupAndSubgroups("com.gradle")
                includeGroupAndSubgroups("no.nordicsemi")
                includeGroupAndSubgroups("org.jetbrains")
            }
        }
        mavenCentral()
    }
}

@Suppress("UnstableApiUsage")
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenLocal()
        google {
            content {
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
                includeGroupAndSubgroups("androidx")
            }
        }
        mavenCentral()
    }
    versionCatalogs {
        // Use Nordic Gradle Version Catalog with common external libraries versions.
        create("libs") {
            from("no.nordicsemi.android.gradle:version-catalog:2.14-1")
        }
        // Fixed versions for Nordic libraries.
        create("nordic") {
            from(files("gradle/nordic.versions.toml"))
        }
        // Nordic Version Catalog is released after library releases, so cannot be used internally in libs.
        // create("nordic") {
        //    from("no.nordicsemi.android:version-catalog:2025.10.00")
        // }
    }
}

rootProject.name = "Device Firmware Update"

include(":app")
include(":lib:analytics")
include(":lib:dfu")
include(":lib:storage")
include(":lib:settings")
include(":profile:main")
include(":profile:scanner")
include(":profile:settings")
include(":profile:welcome")
include(":profile:navigation")
