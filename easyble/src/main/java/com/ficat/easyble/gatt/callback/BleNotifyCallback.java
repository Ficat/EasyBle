package com.ficat.easyble.gatt.callback;


import com.ficat.easyble.BleDevice;

import java.util.UUID;

public interface BleNotifyCallback {
    /**
     * Receive notification from the remote device
     * <p>
     * Note that this method is called back in Non-UI thread
     *
     * @param receivedData       received data
     * @param characteristicUuid target characteristic uuid
     * @param device             the remote device
     */
    void onCharacteristicChanged(byte[] receivedData, UUID characteristicUuid, BleDevice device);

    /**
     * Enable target characteristic notification successfully
     *
     * @param characteristicUuid target characteristic uuid
     * @param device             the remote device
     */
    void onNotifySuccess(UUID characteristicUuid, BleDevice device);

    /**
     * Failed to set notification
     *
     * @param errorCode          see details from the following codes
     *                           {@link com.ficat.easyble.BleErrorCodes#CONNECTION_NOT_ESTABLISHED}
     *                           {@link com.ficat.easyble.BleErrorCodes#SERVICE_NOT_FOUND}
     *                           {@link com.ficat.easyble.BleErrorCodes#CHARACTERISTIC_NOT_FOUND_IN_SERVICE}
     *                           {@link com.ficat.easyble.BleErrorCodes#NOTIFICATION_OR_INDICATION_UNSUPPORTED}
     *                           {@link com.ficat.easyble.BleErrorCodes#UNKNOWN}
     * @param characteristicUuid target characteristic uuid
     * @param device             the remote device
     */
    void onNotifyFailed(int errorCode, UUID characteristicUuid, BleDevice device);
}
