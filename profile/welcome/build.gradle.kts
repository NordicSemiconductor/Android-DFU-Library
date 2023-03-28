plugins {
    // https://github.com/NordicSemiconductor/Android-Gradle-Plugins/blob/main/plugins/src/main/kotlin/AndroidFeatureConventionPlugin.kt
    alias(libs.plugins.nordic.feature)
}

android {
    namespace = "no.nordicsemi.android.dfu.profile.welcome"
}

dependencies {
    implementation(project(":lib:settings"))

    implementation(libs.nordic.theme)
    implementation(libs.nordic.navigation)
}