# DFU Library - Android 4.3+

The DFU Library for Android 4.3+ adds the DFU feature to the Android project. 

### Features:

* Allows to program Application, Soft Device and Bootloader Over-the-Air on the nRF51 Series SoC over Bluetooth Smart.
* Supports HEX or BIN files.
* Supports zip files with Soft Device, Bootloader and Application together.
* Supports the Init packet (which has been required since Bootloader/DFU from SDK 7.0+).
* Performs the DFU operation in the background service.
* Displays the notification with progress.
* Sends local broadcasts with progress, errors and log events to the application.
* Handles bonded devices and buttonless update.
* Android DFU Library is compatible with all Bootloader/DFU versions.
  * Only the application may be updated with the bootloader from SDK 4.3.0. An error will be broadcast in case of sending a Soft Device or a Bootloader.
  * When App, SD and BL files are in a zip file, the service will first try to send all three files together, which will result in an Unsupported Operation DFU error, because currently this operation is not supported by the bootloader. Then, the service will try to send the SD and BL together, which is supported by Bootloader/DFU from SDK 6.1+. Once completed, the service will reconnect to the new bootloader and send the Application in the second connection as part two.

#### Error handling
In case of any communication error the peripheral device will never be bricked. When an application or a bootloader is updated, the previous application (or bootloader, in case there was no application) is restored. When a Soft Device is updated, the previous bootloader will be restored as the application has to be removed to in order to save the new version of the Soft Device. You will still be able to repeat the update and flash the Soft Device and the new application again.

### Requirements

* **An Android 4.3+ device.**

    Support for the Bluetooth 4.0 technology is required. Android introduced Bluetooth Smart in version 4.3. Android 4.4.4 or newer is recommended for better user experience.
* **Android Studio IDE** or **Eclipse ADT**

    Projects are compatible with Android Studio and the Gradle build engine. It is possible to convert them to Eclipse ADT projects. See Integration for more details.
* **nRF51 device for testing.**

   A nRF51 Series device is required to test the working solution. If your final product is not available, use the nRF51 DK, which you can find [here](http://www.nordicsemi.com/eng/Products/nRF51-DK "nRF51 DK").

### Integration

The DFULibrary is compatible as such with Android Studio 1.0.2 IDE. If you are using Eclipse ADT, you will have to convert the project to match the Eclipse project structure.

#### Android Studio

1. Clone the project, or just the *DFULibrary* folder (using sparse-checkout) to a temporary location. 
2. Copy the *DFULibrary* folder to your projects root, for example to *AndroidstudioProjects*.
3. Add the **dfu** module to your project:
    1. Add **'..:DFULibrary:dfu'** to the *settings.gradle* file: `include ':app', '..:DFULibrary:dfu'`
    2. Open Project Structure -> Modules -> app -> Dependencies tab and add dfu module dependency. You may also edit the *build.gradle* file in your app module manually by adding the following dependency: `compile project(':..:DFULibrary:dfu')`

#### Eclipse

1. Clone the project, or just the *DFULibrary* folder (using sparse-checkout) to a temporary location.
2. Create an empty *DFULibrary* project in Eclipse. Make it a library.
3. Copy the content of *java* code folder to the *src*.
4. Copy the content of the *res* folder to the *res* in your Eclipse project.
5. Make sure that *android support library v4* is available in *libs* folders. It should have been added automatically when creating the project.
6. In your application project, open Properties->Android and add DFULibrary as a library.

### Usage

Extend the **DfuBaseService** in your project and implement the `protected Class<? extends Activity> getNotificationTarget()` method. This method should return an activity class that will be open when you press the DFU notification while transfering the firmware. This activity will be started with the 'Intent.FLAG_ACTIVITY_NEW_TASK' flag. 

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
}

```

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

Remember to add your service to the *AndroidManifest.xml*.

Start the DFU service with the following code:

```java
final Intent service = new Intent(this, MyDfuService.class);

service.putExtra(DfuService.EXTRA_DEVICE_ADDRESS, mSelectedDevice.getAddress());
service.putExtra(DfuService.EXTRA_DEVICE_NAME, mSelectedDevice.getName());
service.putExtra(DfuService.EXTRA_FILE_MIME_TYPE, 
    mFileType == DfuService.TYPE_AUTO ? DfuService.MIME_TYPE_ZIP : DfuService.MIME_TYPE_OCTET_STREAM);
service.putExtra(DfuService.EXTRA_FILE_TYPE, mFileType);
service.putExtra(DfuService.EXTRA_FILE_PATH, mFilePath); // a path or URI must be provided.
service.putExtra(DfuService.EXTRA_FILE_URI, mFileStreamUri);
// Init packet is required by Bootloader/DFU from SDK 7.0+ if HEX or BIN file is given above.
// In case of a ZIP file, the init packet (a DAT file) must be included inside the ZIP file.
service.putExtra(DfuService.EXTRA_INIT_FILE_PATH, mInitFilePath); 
service.putExtra(DfuService.EXTRA_INIT_FILE_URI, mInitFileStreamUri);
service.putExtra(DfuService.EXTRA_KEEP_BOND, mKeepBond);

startService(service);
```

Please, see [How to create init packet](https://github.com/NordicSemiconductor/nRF-Master-Control-Panel/tree/master/init%20packet%20handling "Init packet handling") document for more information about the init packet.

The service will send local broadcast events using **LocalBroadcastManager**.


```java
    @Override
    protected void onResume() {
        super.onResume();

        // We are using LocalBroadcastReceiver instead of a normal BroadcastReceiver for 
        // optimization purposes
        final LocalBroadcastManager broadcastManager = LocalBroadcastManager.getInstance(this);
        broadcastManager.registerReceiver(mDfuUpdateReceiver, makeDfuUpdateIntentFilter());
    }

    @Override
    protected void onPause() {
        super.onPause();

        final LocalBroadcastManager broadcastManager = LocalBroadcastManager.getInstance(this);
        broadcastManager.unregisterReceiver(mDfuUpdateReceiver);
    }

    private static IntentFilter makeDfuUpdateIntentFilter() {
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(DfuService.BROADCAST_PROGRESS);
        intentFilter.addAction(DfuService.BROADCAST_ERROR);
        intentFilter.addAction(DfuService.BROADCAST_LOG);
        return intentFilter;
    }
```

### Example

Check the Android projects: nRF Toolbox ([here](https://github.com/NordicSemiconductor/Android-nRF-Toolbox "nRF Toolbox")) or nRF Beacon ([here](https://github.com/NordicSemiconductor/Android-nRF-Toolbox "nRF Toolbox")) for usage examples.

### Issues

Please, submit all issues to the Android DFU Library [here](https://github.com/NordicSemiconductor/Android-DFU-Library/issues "Issues").
