package no.nordicsemi.android.dfu;

/**
 * Created by jesse.rogers on 9/11/17.
 */

public enum DfuState {
    Idle, DeviceConnecting, DeviceConnected, DfuProcessStarting, DfuProcessStarted,
    EnablingDfuMode, Uploading, FirmwareValidating, DeviceDisconnecting,
    DeviceDisconnected, DfuCompleted, DfuAborted, Error
}
