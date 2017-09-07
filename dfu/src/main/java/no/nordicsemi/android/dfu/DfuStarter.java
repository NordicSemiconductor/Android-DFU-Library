package no.nordicsemi.android.dfu;

import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.net.Uri;

import java.io.File;

/**
 * Created by jesse on 9/1/2017.
 */

public class DfuStarter {
    public static DfuServiceController startDfu(Context context, BluetoothDevice device, String zipFilePath) {
        final DfuServiceInitiator starter = new DfuServiceInitiator(device.getAddress());
        starter.setDeviceName(device.getName());
        starter.setKeepBond(true);
        starter.setUnsafeExperimentalButtonlessServiceInSecureDfuEnabled(true);
        Uri zipUri = Uri.fromFile(new File(zipFilePath));
        starter.setZip(zipUri, zipUri.getPath());
        return starter.start(context, DfuServiceImpl.class);
    }
}
