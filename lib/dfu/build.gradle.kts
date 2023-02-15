/*
 * Copyright (c) 2022, Nordic Semiconductor
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are
 * permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this list of
 * conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list
 * of conditions and the following disclaimer in the documentation and/or other materials
 * provided with the distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of its contributors may be
 * used to endorse or promote products derived from this software without specific prior
 * written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED
 * TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A
 * PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
 * HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA,
 * OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY
 * OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE,
 * EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

plugins {
    alias(libs.plugins.nordic.library)
    alias(libs.plugins.nordic.nexus)
}

group = "no.nordicsemi.android"

nordicNexusPublishing {
    POM_ARTIFACT_ID = "dfu"
    POM_NAME = "DFU Library for Android"
    POM_DESCRIPTION = "Device Firmware Update library for Android"
    POM_URL = "https://github.com/NordicSemiconductor/Android-DFU-Library"

    POM_SCM_URL = "https://github.com/NordicSemiconductor/Android-DFU-Library"
    POM_SCM_CONNECTION = "scm:git@github.com:NordicSemiconductor/Android-DFU-Library.git"
    POM_SCM_DEV_CONNECTION = "scm:git@github.com:NordicSemiconductor/Android-DFU-Library.git"

    POM_DEVELOPER_ID = "philips77"
    POM_DEVELOPER_NAME = "Aleksander Nowakowski"
    POM_DEVELOPER_EMAIL = "aleksander.nowakowski@nordicsemi.no"
}

android {
    namespace = "no.nordicsemi.android.dfu"

    defaultConfig {
        minSdk = 18
    }
}

dependencies {
    implementation(libs.androidx.core)
    implementation(libs.androidx.annotation)
    implementation(libs.androidx.localbroadcastmanager)

    implementation("com.google.code.gson:gson:2.9.1")
}