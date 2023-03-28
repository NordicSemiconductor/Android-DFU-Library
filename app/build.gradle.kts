plugins {
    // https://github.com/NordicSemiconductor/Android-Gradle-Plugins/blob/main/plugins/src/main/kotlin/AndroidApplicationComposeConventionPlugin.kt
    alias(libs.plugins.nordic.application.compose)
    // https://github.com/NordicSemiconductor/Android-Gradle-Plugins/blob/main/plugins/src/main/kotlin/AndroidHiltConventionPlugin.kt
    alias(libs.plugins.nordic.hilt)
}

//if (gradle.startParameter.taskRequests.toString().contains("Release")) {
//    apply(plugin = "com.google.gms.google-services")
//    apply(plugin = "com.google.firebase.crashlytics")
//}

android {
    namespace = "no.nordicsemi.android.dfu.app"
    defaultConfig {
        applicationId = "no.nordicsemi.android.dfu"
        resourceConfigurations.add("en")
    }
    signingConfigs {
        getByName("release") {
            keyAlias = "alias"
            keyPassword = "password"
            storeFile = file("jks file")
            storePassword = "store_password"
        }
    }
}

dependencies {
    implementation(project(":lib:analytics"))
    implementation(project(":lib:storage")) // Deep link support
    implementation(project(":profile:navigation"))

    implementation(libs.nordic.theme)
    implementation(libs.nordic.navigation)

    implementation(libs.androidx.activity.compose)
}
