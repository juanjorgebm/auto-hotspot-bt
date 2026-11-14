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
        // Motor de Firefox (GeckoView) para pantallas con navegador del sistema muy viejo
        maven("https://maven.mozilla.org/maven2/")
    }
}
rootProject.name = "RutaMusica"
include(":app")
