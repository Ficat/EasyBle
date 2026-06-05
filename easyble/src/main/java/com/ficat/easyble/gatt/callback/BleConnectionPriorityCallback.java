package com.ficat.easyble.gatt.callback;

import com.ficat.easyble.BleDevice;

public interface BleConnectionPriorityCallback {
    /**
     * Request connection priority successfully
     *
     * <p>
     * Note that if this callback is triggered, it just means the request succeeded,
     * not connection params updated successfully.
     * </p>
     *
     * @param connPriority connection priority
     * @param device       the remote device
     */
    void onConnectionPriorityRequestSuccess(int connPriority, BleDevice device);

    /**
     * Failed to request connection priority
     *
     * @param errCode If it's sdk custom error, it will be one of the following:
     *                {@link com.ficat.easyble.BleErrorCodes#API_VERSION_NOT_SUPPORTED}
     *                {@link com.ficat.easyble.BleErrorCodes#CONNECTION_NOT_ESTABLISHED}
     *                {@link com.ficat.easyble.BleErrorCodes#UNKNOWN}.
     *                Or it belongs to gatt error codes, like
     *                {@link com.ficat.easyble.BleErrorCodes#GATT_INSUFFICIENT_AUTHORIZATION},
     *                {@link com.ficat.easyble.BleErrorCodes#GATT_FAILURE} and so on.
     * @param device  the remote device
     */
    void onConnectionPriorityFailed(int errCode, BleDevice device);
}
