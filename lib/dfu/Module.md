# Module dfu

A module with an implementation of Device Firmware Update (DFU) for nRF5 SDK.

This implementation supports Legacy DFU (SDK 4.3 - 11) and Secure DFU (SDK 12+).

# Package no.nordicsemi.android.dfu

Set of classes and interfaces that are used to implement the Device Firmware Update feature in 
an Android application.

The main access point is the [DfuServiceInitiator][no.nordicsemi.android.dfu.DfuServiceInitiator] 
class that is used to initiate the DFU process.

# Package no.nordicsemi.android.error

Classes that are used to parse errors that may occur during the DFU process.