plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.parcelize) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.compose.compiler) apply false

    // Nordic plugins are defined in https://github.com/NordicSemiconductor/Android-Gradle-Plugins
    alias(libs.plugins.nordic.application.compose) apply false
    alias(libs.plugins.nordic.library) apply false
    alias(libs.plugins.nordic.library.compose) apply false
    alias(libs.plugins.nordic.feature) apply false
    alias(libs.plugins.nordic.kotlin.android) apply false
    alias(libs.plugins.nordic.hilt) apply false
    alias(libs.plugins.nordic.nexus.android) apply false

    // This plugin is used to generate Dokka documentation.
    alias(libs.plugins.kotlin.dokka) apply false
    // This applies Nordic look & feel to generated Dokka documentation.
    // https://github.com/NordicSemiconductor/Android-Gradle-Plugins/blob/main/plugins/src/main/kotlin/NordicDokkaPlugin.kt
    alias(libs.plugins.nordic.dokka) apply true

    alias(libs.plugins.google.services) apply false
    alias(libs.plugins.firebase.crashlytics) apply false
}

// Configure main Dokka page
dokka {
    pluginsConfiguration.html {
        homepageLink.set("https://github.com/NordicSemiconductor/Android-DFU-Library")
    }
}