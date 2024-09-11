plugins {
    // https://github.com/NordicSemiconductor/Android-Gradle-Plugins/blob/main/plugins/src/main/kotlin/AndroidFeatureConventionPlugin.kt
    alias(libs.plugins.nordic.feature)
    alias(libs.plugins.kotlin.parcelize)
}

android {
    namespace = "no.nordicsemi.android.dfu.profile.main"
}

dependencies {
    implementation(project(":lib:dfu"))
    implementation(project(":lib:analytics"))
    implementation(project(":lib:storage"))
    implementation(project(":lib:settings"))
    implementation(project(":profile:scanner"))
    implementation(project(":profile:settings"))
    implementation(project(":profile:welcome"))

    implementation(libs.nordic.ui)
    implementation(libs.nordic.core)
    implementation(libs.nordic.logger)
    implementation(libs.nordic.analytics)
    implementation(libs.nordic.permissions.ble)
    implementation(libs.nordic.navigation)

    // Extended Icons
    implementation(libs.androidx.compose.material.iconsExtended)
}