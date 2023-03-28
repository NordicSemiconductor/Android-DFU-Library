import java.io.FileInputStream
import java.util.*

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
            val keystorePropertiesFile = file("../keystore.properties")

            if (!keystorePropertiesFile.exists()) {
                logger.warn("Release builds may not work: signing config not found.")
                return@getByName
            }

            val keystoreProperties = Properties()
            keystoreProperties.load(FileInputStream(keystorePropertiesFile))

            keyAlias = keystoreProperties["keyAlias"] as String
            keyPassword = keystoreProperties["keyPassword"] as String
            storeFile = file(keystoreProperties["storeFile"] as String)
            storePassword = keystoreProperties["storePassword"] as String
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
