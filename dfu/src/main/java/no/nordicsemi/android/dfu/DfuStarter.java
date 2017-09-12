package no.nordicsemi.android.dfu;

import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.net.Uri;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Created by jesse on 9/1/2017.
 */

public class DfuStarter {

    private static final String ZIP_FILE_PATH = "/sdcard/dfu.zip";
    private static final String HEX_ENTRY_NAME = "application.hex";
    private static final String DAT_ENTRY_NAME = "application.dat";

    private static DfuListener mListener;
    private static DummyListener mDummyListener;

    public static DfuServiceController startDfu(Context context, BluetoothDevice device, String zipFilePath) {
        final DfuServiceInitiator starter = new DfuServiceInitiator(device.getAddress());
        starter.setDeviceName(device.getName());
        starter.setKeepBond(true);
        starter.setUnsafeExperimentalButtonlessServiceInSecureDfuEnabled(true);
        Uri zipUri = Uri.fromFile(new File(zipFilePath));
        starter.setZip(zipUri, zipUri.getPath());
        return starter.start(context, DfuServiceImpl.class);
    }

    public static DfuServiceController startDfu(Context context, BluetoothDevice device, String hexFilePath, String datFilePath) throws IOException {
        zipDfuFiles(hexFilePath, datFilePath);
        return startDfu(context, device, ZIP_FILE_PATH);
    }

    public static void setDfuListener(Context context, DfuListener listener) {
        if (mDummyListener == null) {
            mDummyListener = new DummyListener();
            DfuServiceListenerHelper.registerProgressListener(context, mDummyListener);
        }
        mListener = listener;
    }

    public static DfuListener getDfuListener() {
        return mListener;
    }

    private static void zipDfuFiles(String hexFilePath, String datFilePath) throws IOException {
        File outFile = new File(ZIP_FILE_PATH);
        if (outFile.exists()) {
            outFile.delete();
            outFile = new File(ZIP_FILE_PATH);
        }
        outFile.createNewFile();


        File hexFile = new File(hexFilePath);
        File datFile = new File(datFilePath);

        FileOutputStream fos = new FileOutputStream(outFile);
        ZipOutputStream outs = new ZipOutputStream(fos);

        addToZip(outs, hexFile, HEX_ENTRY_NAME);
        addToZip(outs, datFile, DAT_ENTRY_NAME);

        outs.close();
    }

    private static void addToZip(ZipOutputStream outs, File file, String entryName) throws IOException {
        ZipEntry hexEntry = new ZipEntry(entryName);
        hexEntry.setTime(file.lastModified());
        FileInputStream ins = new FileInputStream(file);
        outs.putNextEntry(hexEntry);
        pipe(ins, outs);
        outs.closeEntry();
        ins.close();
    }

    private static void pipe(InputStream ins, OutputStream out) throws IOException {
        byte[] buffer = new byte[512];
        int len = ins.read(buffer);
        while (len != -1) {
            out.write(buffer, 0, len);
            len = ins.read(buffer);
        }
        out.flush();
    }
}
