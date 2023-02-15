plugins {
    // https://github.com/NordicSemiconductor/Android-Gradle-Plugins/blob/main/plugins/src/main/kotlin/AndroidLibraryConventionPlugin.kt
    alias(libs.plugins.nordic.library)
    // https://github.com/NordicSemiconductor/Android-Gradle-Plugins/blob/main/plugins/src/main/kotlin/AndroidHiltConventionPlugin.kt
    alias(libs.plugins.nordic.hilt)
    id("kotlin-parcelize")
}

android {
    namespace = "no.nordicsemi.android.dfu.settings"
}

dependencies {
    implementation(project(":lib:storage"))
    // Datastore
    implementation(libs.androidx.dataStore.preferences)
}
