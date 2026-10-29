pluginManagement {
    plugins {
        // Версия совпадает с agp в gradle/libs.versions.toml.
        id("com.android.settings") version "9.4.1" apply false
    }
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
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
    id("com.android.settings")
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "clock"
include(":app")

android {
    execution {
        profiles {
            create("local") {
                r8 {
                    runInSeparateProcess = true
                    jvmOptions += listOf(
                        "-Xms256m",
                        "-Xmx2048m",
                        "-XX:+HeapDumpOnOutOfMemoryError",
                    )
                }
            }
        }
        defaultProfile = "local"
    }
}
