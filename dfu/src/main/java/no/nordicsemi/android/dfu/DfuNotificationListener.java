package no.nordicsemi.android.dfu;

import java.util.UUID;

/**
 * Listener for characteristic notification events. This listener should be used instead of creating the BroadcastReceiver on your own.
 */
public interface DfuNotificationListener {

    /**
     * Method called when a notification was received from the DFU device.
     * @param deviceAddress the target device address
     * @param characteristicUuid the characteristic uuid of the notifying characteristic
     * @param data  the notified data
     */
    void onNotificationEvent(final String deviceAddress, final UUID characteristicUuid, final byte[] data);
}
