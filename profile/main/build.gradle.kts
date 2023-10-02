plugins {
    // https://github.com/NordicSemiconductor/Android-Gradle-Plugins/blob/main/plugins/src/main/kotlin/AndroidFeatureConventionPlugin.kt
    alias(libs.plugins.nordic.feature)
    id("kotlin-parcelize")
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

    implementation(libs.nordic.core)
    implementation(libs.nordic.theme)
    implementation(libs.nordic.uilogger)
    implementation(libs.nordic.analytics)
    implementation(libs.nordic.permissions.ble)
    implementation(libs.nordic.navigation)

    // Extended Icons
    implementation(libs.androidx.compose.material.iconsExtended)
}