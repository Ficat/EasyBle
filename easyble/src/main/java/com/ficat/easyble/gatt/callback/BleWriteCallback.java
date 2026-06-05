package com.ficat.easyble.gatt.callback;


import com.ficat.easyble.BleDevice;

import java.util.UUID;

public interface BleWriteCallback {
    /**
     * Write successfully
     *
     * @param data               data that has been written successfully
     * @param characteristicUuid target characteristic uuid
     * @param device             the remote device
     */
    void onWriteSuccess(byte[] data, UUID characteristicUuid, BleDevice device);

    /**
     * Failed to write data
     *
     * @param errCode            If it's sdk custom error, it will be one of the following:
     *                           {@link com.ficat.easyble.BleErrorCodes#CONNECTION_NOT_ESTABLISHED}
     *                           {@link com.ficat.easyble.BleErrorCodes#SERVICE_NOT_FOUND}
     *                           {@link com.ficat.easyble.BleErrorCodes#CHARACTERISTIC_NOT_FOUND_IN_SERVICE}
     *                           {@link com.ficat.easyble.BleErrorCodes#WRITE_UNSUPPORTED}
     *                           {@link com.ficat.easyble.BleErrorCodes#DATA_LENGTH_GREATER_THAN_MTU}
     *                           {@link com.ficat.easyble.BleErrorCodes#TIMEOUT}
     *                           {@link com.ficat.easyble.BleErrorCodes#UNKNOWN}
     *                           Or it belongs to gatt error codes, like
     *                           {@link com.ficat.easyble.BleErrorCodes#GATT_INSUFFICIENT_AUTHORIZATION},
     *                           {@link com.ficat.easyble.BleErrorCodes#GATT_FAILURE} and so on.
     * @param data               data failed to write
     * @param characteristicUuid target characteristic uuid
     * @param device             the remote device
     */
    void onWriteFailed(int errCode, byte[] data, UUID characteristicUuid, BleDevice device);
}
