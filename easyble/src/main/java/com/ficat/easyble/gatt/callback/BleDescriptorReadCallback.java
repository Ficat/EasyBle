package com.ficat.easyble.gatt.callback;


import com.ficat.easyble.BleDevice;

import java.util.UUID;

public interface BleDescriptorReadCallback {
    /**
     * Descriptor read successfully.
     *
     * @param readData       data read from target descriptor
     * @param descriptorUuid target descriptor uuid
     * @param device         the remote device
     */
    void onDescriptorReadSuccess(byte[] readData, UUID descriptorUuid, BleDevice device);

    /**
     * Failed to read data from target descriptor
     *
     * @param errCode        If it's sdk custom error, it will be one of the following:
     *                       {@link com.ficat.easyble.BleErrorCodes#CONNECTION_NOT_ESTABLISHED}
     *                       {@link com.ficat.easyble.BleErrorCodes#SERVICE_NOT_FOUND}
     *                       {@link com.ficat.easyble.BleErrorCodes#CHARACTERISTIC_NOT_FOUND_IN_SERVICE}
     *                       {@link com.ficat.easyble.BleErrorCodes#DESCRIPTOR_NOT_FOUND_IN_CHARACTERISTIC}
     *                       {@link com.ficat.easyble.BleErrorCodes#TIMEOUT}
     *                       {@link com.ficat.easyble.BleErrorCodes#UNKNOWN}
     *                       Or it belongs to gatt error codes, like
     *                       {@link com.ficat.easyble.BleErrorCodes#GATT_INSUFFICIENT_AUTHORIZATION},
     *                       {@link com.ficat.easyble.BleErrorCodes#GATT_FAILURE} and so on.
     * @param descriptorUuid target descriptor uuid
     * @param device         the remote device
     */
    void onDescriptorReadFailed(int errCode, UUID descriptorUuid, BleDevice device);
}
