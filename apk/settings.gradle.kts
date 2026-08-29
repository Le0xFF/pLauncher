import java.io.File
import java.util.Properties

pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

// Generate a project-local local.properties from the shared one in $HOME/ANDROID,
// so that sdk.dir (and any other needed keys) are available to AGP without a
// committed local.properties in the repo.
val home = System.getenv("HOME") ?: ""
val externalPropsFile = File(home, "ANDROID/local.properties")
val projectLocalProps = File(rootDir, "local.properties")
if (externalPropsFile.exists() && !projectLocalProps.exists()) {
    val props = Properties().apply { externalPropsFile.inputStream().use { load(it) } }
    projectLocalProps.writeText(
        props.stringPropertyNames().joinToString("\n") { key ->
            "${key}=${props.getProperty(key)?.replace("\${HOME}", home)}"
        }.trimEnd() + "\n"
    )
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven("https://jitpack.io")
        maven("https://oss.sonatype.org/content/repositories/snapshots/")
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
}
include(":app")
