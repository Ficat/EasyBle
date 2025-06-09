package com.ficat.easyble.gatt.callback;


import com.ficat.easyble.BleDevice;

import java.util.UUID;

public interface BleReadCallback {
    /**
     * Read successfully.
     *
     * @param readData           data read from target characteristic
     * @param characteristicUuid target characteristic uuid
     * @param device             the remote device
     */
    void onReadSuccess(byte[] readData, UUID characteristicUuid, BleDevice device);

    /**
     * Failed to read data from target characteristic
     *
     * @param errorCode          see details from the following codes
     *                           {@link com.ficat.easyble.BleErrorCodes#CONNECTION_NOT_ESTABLISHED}
     *                           {@link com.ficat.easyble.BleErrorCodes#SERVICE_NOT_FOUND}
     *                           {@link com.ficat.easyble.BleErrorCodes#CHARACTERISTIC_NOT_FOUND_IN_SERVICE}
     *                           {@link com.ficat.easyble.BleErrorCodes#READ_UNSUPPORTED}
     *                           {@link com.ficat.easyble.BleErrorCodes#UNKNOWN}
     * @param characteristicUuid target characteristic uuid
     * @param device             the remote device
     */
    void onReadFailed(int errorCode, UUID characteristicUuid, BleDevice device);
}
