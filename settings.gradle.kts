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

rootProject.name = "DracApps"

// El dominio es un módulo JVM puro a propósito: al no depender del SDK, no puede
// importar Android ni por descuido. La regla del plan deja de ser un acuerdo y pasa
// a ser algo que el compilador impide romper.
include(":dominio")
include(":app")
