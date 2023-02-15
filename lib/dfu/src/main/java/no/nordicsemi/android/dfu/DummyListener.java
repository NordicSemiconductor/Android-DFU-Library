package no.nordicsemi.android.dfu;

/**
 * Created by jesse.rogers on 9/12/17.
 */

public class DummyListener implements DfuProgressListener {
    @Override
    public void onDeviceConnecting(String deviceAddress) {

    }

    @Override
    public void onDeviceConnected(String deviceAddress) {

    }

    @Override
    public void onDfuProcessStarting(String deviceAddress) {

    }

    @Override
    public void onDfuProcessStarted(String deviceAddress) {

    }

    @Override
    public void onEnablingDfuMode(String deviceAddress) {

    }

    @Override
    public void onProgressChanged(String deviceAddress, int percent, float speed, float avgSpeed, int currentPart, int partsTotal) {

    }

    @Override
    public void onFirmwareValidating(String deviceAddress) {

    }

    @Override
    public void onDeviceDisconnecting(String deviceAddress) {

    }

    @Override
    public void onDeviceDisconnected(String deviceAddress) {

    }

    @Override
    public void onDfuCompleted(String deviceAddress) {

    }

    @Override
    public void onDfuAborted(String deviceAddress) {

    }

    @Override
    public void onError(String deviceAddress, int error, int errorType, String message) {

    }
}
