# Module dfu

A module with an implementation of Device Firmware Update (DFU) for nRF5 SDK.

## Overview

This library is using quite old Android components, like `IntentService` and is written in Java,
but should still work on latest Android releases and with Kotlin.

Check out the nRF DFU sample app.

## Important

This implementation is compatible with nRF5 SDK only:
* Legacy DFU (SDK 4.3 - 11) 
* Secure DFU (SDK 12+)

For updating devices based on nRF Connect SDK or Zephyr, use 
[nRF Connect Device Manager](https://github.com/NordicSemiconductor/Android-nRF-Connect-Device-Manager/).

# Package no.nordicsemi.android.dfu

Set of classes and interfaces that are used to implement the Device Firmware Update feature in 
an Android application.

## Overview

The main access point is the [DfuServiceInitiator][no.nordicsemi.android.dfu.DfuServiceInitiator] 
class. Use it to initiate the DFU process.

# Package no.nordicsemi.android.error

Classes that are used to parse errors that may occur during the DFU process.