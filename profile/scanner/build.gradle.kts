plugins {
    // https://github.com/NordicSemiconductor/Android-Gradle-Plugins/blob/main/plugins/src/main/kotlin/AndroidFeatureConventionPlugin.kt
    alias(libs.plugins.nordic.feature)
    id("kotlin-parcelize")
}

android {
    namespace = "no.nordicsemi.android.dfu.profile.scanner"
}

dependencies {
    implementation(libs.nordic.scanner)
    implementation(libs.nordic.navigation)
}
