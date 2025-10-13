plugins {
    // https://github.com/NordicSemiconductor/Android-Gradle-Plugins/blob/main/plugins/src/main/kotlin/AndroidApplicationComposeConventionPlugin.kt
    alias(libs.plugins.nordic.application.compose)
    // https://github.com/NordicSemiconductor/Android-Gradle-Plugins/blob/main/plugins/src/main/kotlin/AndroidHiltConventionPlugin.kt
    alias(libs.plugins.nordic.hilt)
}

if (gradle.startParameter.taskRequests.toString().contains("Release")) {
    apply(plugin = "com.google.gms.google-services")
    apply(plugin = "com.google.firebase.crashlytics")
}

android {
    namespace = "no.nordicsemi.android.dfu.app"
    defaultConfig {
        applicationId = "no.nordicsemi.android.dfu"
    }
    androidResources {
        localeFilters += setOf("en")
    }
}

dependencies {
    implementation(project(":lib:analytics"))
    implementation(project(":lib:storage")) // Deep link support
    implementation(project(":profile:navigation"))

    implementation(nordic.theme)
    implementation(nordic.navigation)

    // Use native Android BLE client.
    // This can be switched to mock client for testing purposes (not implemented yet).
    // See CentralManagerModule.kt in :app module.
    implementation(nordic.blek.client.android)

    implementation(libs.androidx.activity.compose)
}
