# DFU Library - Android 4.3+

The DFU Library for Android 4.3+ adds the DFU feature to the Android project.

### Features:

* DFU Library version 1.0.0+ supports **Secure DFU** introduced in SDK 12.0.0 and is fully backwards 
  compatible with all versions of Legacy DFU. For projects based on [nRF Connect SDK](https://www.nordicsemi.com/Products/Development-software/nrf-connect-sdk), use [nRF Connect Device Manager library](https://github.com/NordicSemiconductor/Android-nRF-Connect-Device-Manager) instead.
* Allows to program Application, Soft Device and Bootloader Over-the-Air on the nRF5 Series SoC over 
  Bluetooth Low Energy.
* Supports ZIP files with Soft Device, Bootloader and Application together.
* Supports legacy HEX or BIN files.
* Supports the Init packet (which has been required since Bootloader/DFU from SDK 7.0+).
* Performs the DFU operation in a foreground service (option to keep in background service).
* Displays the notification with progress (option to disable).
* Sends local broadcasts with progress, errors and log events to the application.
* Handles bonded devices and buttonless update.
* Android DFU Library is compatible with all Bootloader/DFU versions.
  * Only the application may be updated with the bootloader from SDK 4.3.0. 
    An error will be broadcast in case of sending a Soft Device or a Bootloader.
  * When App, SD and BL files are in a ZIP file, the service will first try to send all three files 
    together, which will result in an Unsupported Operation DFU error, because currently this 
    operation is not supported by the bootloader. Then, the service will try to send the SD and BL 
    together, which is supported by Bootloader/DFU from SDK 6.1+. Once completed, the service will 
    reconnect to the new bootloader and send the Application in the second connection as part two.

#### Error handling

In case of any communication error the peripheral device will never be bricked. When an application 
or a bootloader is updated, the previous application (or bootloader, in case there was no application) 
is restored. When a Soft Device is updated, the previous bootloader will be restored as the application 
has to be removed to in order to save the new version of the Soft Device. You will still be able to 
repeat the update and flash the Soft Device and the new application again.

### Requirements

* **An Android 4.3+ device.**

    Support for the Bluetooth 4.x or 5.x technology is required. Android introduced Bluetooth LE in 
    version 4.3.
* **Android Studio IDE**

    Projects are compatible with Android Studio and the Gradle build engine.
* **nRF5 device for testing.**

   A nRF5 Series device is required to test the working solution. If your final product is not 
   available, use one of the nRF5 DK, which you can find 
   [here](https://www.nordicsemi.com/Software-and-tools/Development-Kits "Development Kits").

### Integration

The easiest way to include the library to your project is to add the 

```groovy
implementation 'no.nordicsemi.android:dfu:[Version]'
``` 

line to your *build.gradle* file.

However, if you want to modify the code to your needs you have to clone the project and add it as follows:

1. Clone the project into local folder (by default it will be cloned into Android-DFU-Library 
   folder), for example to *AndroidstudioProjects*.
2. Add the following to your **settings.gradle** file:
    ```groovy    
    if (file('../Android-DFU-Library').exists()) {
        includeBuild('../Android-DFU-Library')
    }
    ```

### Usage

The library is designed in a way that it is easy to integrate. Whole logic is performed by an
`IntentService`, started using `DfuServiceInitiator`. The service reportes progress and errors
using `LocalBroadcastManager`.

Extend the `DfuBaseService` in your project and implement the following method:

```java
protected Class<? extends Activity> getNotificationTarget()
``` 
This method should return an activity class that will be open when you press the DFU 
notification while transferring the firmware. 
This activity will be started with the `Intent.FLAG_ACTIVITY_NEW_TASK` flag. 

```java
package com.example.coolproject;

import no.nordicsemi.android.dfu.DfuBaseService;
import android.app.Activity;

public class DfuService extends DfuBaseService {

    @Override
    protected Class<? extends Activity> getNotificationTarget() {
        /*
         * As a target activity the NotificationActivity is returned, not the MainActivity. This is because
         * the notification must create a new task:
         * 
         * intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
         * 
         * when you press it. You can use NotificationActivity to check whether the new activity 
         * is a root activity (that means no other activity was open earlier) or that some 
         * other activity is already open. In the latter case the NotificationActivity will just be
         * closed. The system will restore the previous activity. However, if the application has been 
         * closed during upload and you click the notification, a NotificationActivity will
         * be launched as a root activity. It will create and start the main activity and
         * terminate itself.
         * 
         * This method may be used to restore the target activity in case the application
         * was closed or is open. It may also be used to recreate an activity history using
         * startActivities(...).
         */
        return NotificationActivity.class;
    }
    
    @Override
    protected boolean isDebug() {
        // Here return true if you want the service to print more logs in LogCat.
        // Library's BuildConfig in current version of Android Studio is always set to DEBUG=false, so
        // make sure you return true or your.app.BuildConfig.DEBUG here.
        return BuildConfig.DEBUG;
    }
    
	@Override
	protected void updateForegroundNotification(@NonNull final NotificationCompat.Builder builder) {
		// Customize the foreground service notification here.
	}
}
```
Remember to add your service to *AndroidManifest.xml*.

You may use the following class in order to prevent starting another instance of your application:

```java
package com.example.coolproject;

import com.example.coolproject.MyActivity;
import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

public class NotificationActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // If this activity is the root activity of the task, the app is not running
        if (isTaskRoot()) {
            // Start the app before finishing
            final Intent intent = new Intent(this, MyActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            intent.putExtras(getIntent().getExtras()); // copy all extras
            startActivity(intent);
        }

        // Now finish, which will drop you to the activity at which you were at the top of the task stack
        finish();
    }
}
```

Remember to add your activity to the *AndroidManifest.xml*.

Start the DFU service with the following code:

```java
final DfuServiceInitiator starter = new DfuServiceInitiator(mSelectedDevice.getAddress())
        .setDeviceName(mSelectedDevice.getName())
        .setKeepBond(keepBond);
// If you want to have experimental buttonless DFU feature (DFU from SDK 12.x only!) supported call 
// additionally:
starter.setUnsafeExperimentalButtonlessServiceInSecureDfuEnabled(true);
// but be aware of this: https://devzone.nordicsemi.com/question/100609/sdk-12-bootloader-erased-after-programming/
// and other issues related to this experimental service.

// For DFU bootloaders from SDK 15 and 16 it may be required to add a delay before sending each
// data packet. This delay gives the DFU target more time to perpare flash memory, causing less
// packets being dropped and more reliable transfer. Detection of packets being lost would cause
// automatic switch to PRN = 1, making the DFU very slow (but reliable). 
stater.setPrepareDataObjectDelay(300L);

// Init packet is required by Bootloader/DFU from SDK 7.0+ if HEX or BIN file is given above.
// In case of a ZIP file, the init packet (a DAT file) must be included inside the ZIP file.
if (mFileType == DfuService.TYPE_AUTO)
    starter.setZip(mFileStreamUri, mFilePath);
else {
    starter.setBinOrHex(mFileType, mFileStreamUri, mFilePath).setInitFile(mInitFileStreamUri, mInitFilePath);
}
final DfuServiceController controller = starter.start(this, DfuService.class);
// You may use the controller to pause, resume or abort the DFU process.
```

The service will send local broadcast events using `LocalBroadcastManager`.


```java
private final DfuProgressListener dfuProgressListener = new DfuProgressListenerAdapter() {
    @Override
    public void onDeviceConnecting(final String deviceAddress) {
        progressBar.setIndeterminate(true);
        textPercentage.setText(R.string.dfu_status_connecting);
    }

    @Override
    public void onDfuProcessStarting(final String deviceAddress) {
        progressBar.setIndeterminate(true);
        textPercentage.setText(R.string.dfu_status_starting);
    }
    ///...
}

@Override
protected void onResume() {
    super.onResume();

    DfuServiceListenerHelper.registerProgressListener(this, dfuProgressListener);
}

@Override
protected void onPause() {
    super.onPause();

    DfuServiceListenerHelper.unregisterProgressListener(this, dfuProgressListener);
}
```

For Android Oreo and above, if you want the `DfuService` to show a notification with the progress, 
you have to create a notification channel. The easiest way to do this is to call

```java
DfuServiceInitiator.createDfuNotificationChannel(context);
```

### Example

Check the Android projects: [nRF Toolbox](https://github.com/NordicSemiconductor/Android-nRF-Toolbox "nRF Toolbox") 
or [nRF Beacon](https://github.com/NordicSemiconductor/Android-nRF-Beacon "nRF Beacon") for usage examples.

### Issues

Please, submit all issues to the Android DFU Library [here](https://github.com/NordicSemiconductor/Android-DFU-Library/issues "Issues").
