plugins {
    // https://github.com/NordicSemiconductor/Android-Gradle-Plugins/blob/main/plugins/src/main/kotlin/AndroidLibraryConventionPlugin.kt
    alias(libs.plugins.nordic.library)
    // https://github.com/NordicSemiconductor/Android-Gradle-Plugins/blob/main/plugins/src/main/kotlin/AndroidKotlinConventionPlugin.kt
    alias(libs.plugins.nordic.kotlin.android)
}

android {
    namespace = "no.nordicsemi.android.dfu.profile.navigation"
}

dependencies {
    implementation(project(":profile:main"))
    implementation(project(":profile:scanner"))
    implementation(project(":profile:settings"))
    implementation(project(":profile:welcome"))

    implementation(libs.nordic.navigation)
}