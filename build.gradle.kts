plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.kapt) apply false
    alias(libs.plugins.hilt) apply false

    // Nordic plugins are defined in https://github.com/NordicSemiconductor/Android-Gradle-Plugins
    alias(libs.plugins.nordic.application) apply false
    alias(libs.plugins.nordic.application.compose) apply false
    alias(libs.plugins.nordic.library) apply false
    alias(libs.plugins.nordic.library.compose) apply false
    alias(libs.plugins.nordic.feature) apply false
    alias(libs.plugins.nordic.kotlin) apply false
    alias(libs.plugins.nordic.hilt) apply false
    alias(libs.plugins.nordic.nexus) apply false

    id("com.google.gms.google-services") version "4.3.14" apply false
    id("com.google.firebase.crashlytics") version "2.9.2" apply false
}