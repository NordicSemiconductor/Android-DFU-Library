# DFU Library

[ ![Download](https://api.bintray.com/packages/nordic/android/no.nordicsemi.android%3Adfu/images/download.svg) ](https://bintray.com/nordic/android/no.nordicsemi.android%3Adfu/_latestVersion)

### Usage

The DFU library may be found on jcenter and Maven Central repository. Add it to your project by 
adding the following dependency:

```Groovy
implementation 'no.nordicsemi.android:dfu:1.10.1'
```

For projects not migrated to Android Jetpack, use:

```Groovy
implementation 'no.nordicsemi.android:dfu:1.8.1'
```

> Note: This version is not maintained anymore. All new features and bug fixes will be released on 
the latest version only.

If you use proguard/R8, add the following line to your proguard rules:
```-keep class no.nordicsemi.android.dfu.** { *; }```

Starting from version 1.9.0 the library is able to retry a DFU update in case of an unwanted
disconnection. However, to maintain backward compatibility, this feature is by default disabled.
Call `initiator.setNumberOfRetries(int)` to set how many attempts the service should perform.
Secure DFU will be resumed after it has been interrupted from the point it stopped, while the 
Legacy DFU will start again. 

### Device Firmware Update (DFU)

The nRF5x Series chips are flash-based SoCs, and as such they represent the most flexible solution available. 
A key feature of the nRF5x Series and their associated software architecture and S-Series SoftDevices 
is the possibility for Over-The-Air Device Firmware Upgrade (OTA-DFU). See Figure 1. 
OTA-DFU allows firmware upgrades to be issued and downloaded to products in the field via the cloud 
and so enables OEMs to fix bugs and introduce new features to products that are already out on the market. 
This brings added security and flexibility to product development when using the nRF5x Series SoCs.

![Device Firmware Update](resources/dfu.png)

This repository contains a tested library for Android 4.3+ platform which may be used to perform 
Device Firmware Update on the nRF5x device using a phone or a tablet.

DFU library has been designed to make it very easy to include these devices into your application. 
It is compatible with all Bootloader/DFU versions.

[![Alt text for your video](http://img.youtube.com/vi/LdY2m_bZTgE/0.jpg)](http://youtu.be/LdY2m_bZTgE)

### Documentation

See the [documentation](documentation) for more information.

### Requirements

The library is compatible with nRF51 and nRF52 devices with S-Series Soft Device and the 
DFU Bootloader flashed on. 

### DFU History

#### Legacy DFU

* **SDK 4.3.0** - First version of DFU over Bluetooth Smart. DFU supports Application update.
* **SDK 6.1.0** - DFU Bootloader supports Soft Device and Bootloader update. As the updated 
                  Bootloader may be dependent on the new Soft Device, those two may be sent and 
                  installed together.
    - Buttonless update support for non-bonded devices.
* **SDK 7.0.0** - The extended init packet is required. The init packet contains additional 
                  validation information: device type and revision, application version, compatible 
                  Soft Devices and the firmware CRC.
* **SDK 8.0.0** - The bond information may be preserved after an application update. 
                  The new application, when first started, will send the Service Change indication 
                  to the phone to refresh the services.
    - Buttonless update support for bonded devices 
    - sharing the LTK between an app and the bootloader.
    
#### Secure DFU

* **SDK 12.0.0** - New Secure DFU has been released. Buttonless service is experimental.
* **SDK 13.0.0** - Buttonless DFU (still experimental) uses different UUIDs. No bond sharing 
                   supported. Bootloader will use address +1.
* **SDK 14.0.0** - Buttonless DFU is no longer experimental. A new UUID (0004) added for bonded 
                   only devices (previous one (0003) is for non-bonded only).
* **SDK 15.0.0** - Support for higher MTUs added.

This library is fully backwards compatible and supports both the new and legacy DFU.
The experimental buttonless DFU service from SDK 12 is supported since version 1.1.0. 
Due to the fact, that this experimental service from SDK 12 is not safe, you have to call 
[starter.setUnsafeExperimentalButtonlessServiceInSecureDfuEnabled(true)](https://github.com/NordicSemiconductor/Android-DFU-Library/blob/release/dfu/src/main/java/no/nordicsemi/android/dfu/DfuServiceInitiator.java#L376)
to enable it. Read the method documentation for details. It is recommended to use the Buttonless 
service from SDK 13 (for non-bonded devices, or 14 for bonded).
Both are supported since DFU Library 1.3.0.

Check platform folders for mode details about compatibility for each library.

### React Native

A library for both iOS and Android that is based on this library is available for React Native: 
[react-native-nordic-dfu](https://github.com/Pilloxa/react-native-nordic-dfu) 

### Flutter

A library for both iOS and Android that is based on this library is available for Flutter: 
[flutter-nordic-dfu](https://github.com/fengqiangboy/flutter-nordic-dfu) 

### Resources

- [DFU Introduction](http://infocenter.nordicsemi.com/topic/com.nordic.infocenter.sdk5.v11.0.0/examples_ble_dfu.html?cp=6_0_0_4_3_1 "BLE Bootloader/DFU")
- [Secure DFU Introduction](https://infocenter.nordicsemi.com/topic/sdk_nrf5_v16.0.0/lib_bootloader_modules.html?cp=6_1_3_5 "BLE Secure DFU Bootloader")
- [nRF51 Development Kit (DK)](https://www.nordicsemi.com/Software-and-tools/Development-Kits/nRF51-DK "nRF51 DK") (compatible with Arduino Uno Revision 3)
- [nRF52 Development Kit (DK)](https://www.nordicsemi.com/Software-and-tools/Development-Kits/nRF52-DK "nRF52 DK") (compatible with Arduino Uno Revision 3)
- [nRF52840 Development Kit (DK)](https://www.nordicsemi.com/Software-and-tools/Development-Kits/nRF52840-DK "nRF52840 DK") (compatible with Arduino Uno Revision 3)
