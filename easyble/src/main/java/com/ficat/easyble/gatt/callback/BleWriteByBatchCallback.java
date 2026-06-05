package com.ficat.easyble.gatt.callback;


import com.ficat.easyble.BleDevice;

import java.util.UUID;

public interface BleWriteByBatchCallback {
    /**
     * Write successfully, that means all batches have been written
     *
     * @param originalData       original data
     * @param characteristicUuid target characteristic uuid
     * @param device             the remote device
     */
    void onWriteBatchSuccess(byte[] originalData, UUID characteristicUuid, BleDevice device);

    /**
     * Batch progress
     *
     * @param progress           current progress
     * @param characteristicUuid target characteristic uuid
     * @param device             the remote device
     */
    void onWriteBatchProgress(float progress, UUID characteristicUuid, BleDevice device);


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
     * @param writtenLength      written length from original data
     * @param originalData       original data
     * @param characteristicUuid target characteristic uuid
     * @param device             the remote device
     */
    void onWriteBatchFailed(int errCode, int writtenLength, byte[] originalData, UUID characteristicUuid, BleDevice device);
}
