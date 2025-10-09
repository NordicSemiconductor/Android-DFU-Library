plugins {
    // https://github.com/NordicSemiconductor/Android-Gradle-Plugins/blob/main/plugins/src/main/kotlin/AndroidFeatureConventionPlugin.kt
    alias(libs.plugins.nordic.feature)
    alias(libs.plugins.kotlin.parcelize)
}

android {
    namespace = "no.nordicsemi.android.dfu.profile.scanner"
}

dependencies {
    implementation(libs.nordic.scanner.ble)
    implementation(libs.nordic.navigation)
}
