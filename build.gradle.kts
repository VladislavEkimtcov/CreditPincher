import org.jetbrains.intellij.platform.gradle.TestFrameworkType

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.intellij.platform")
    id("org.jetbrains.changelog")
}

dependencies {
    testImplementation("junit:junit:4.13.2")

    // IntelliJ Platform Gradle Plugin Dependencies Extension - read more: https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-dependencies-extension.html
    intellijPlatform {
        intellijIdea("2025.2.6.2")
        testFramework(TestFrameworkType.Platform)
    }
}

// CI names its artifacts after the workflow run number so they line up with the
// v1.0.<run> tags the release workflow creates. Locally the version comes from
// gradle.properties, which build-plugin-macos.sh / build-plugin-windows.ps1
// increment on every run - overriding it here would make those scripts look for
// an archive that was never produced.
providers.environmentVariable("GITHUB_RUN_NUMBER").orNull?.let { runNumber ->
    version = "1.0.$runNumber"
}