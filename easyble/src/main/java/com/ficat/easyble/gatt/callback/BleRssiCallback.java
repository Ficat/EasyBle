package com.ficat.easyble.gatt.callback;


import com.ficat.easyble.BleDevice;

public interface BleRssiCallback {
    /**
     * Read RSSI from the remote device successfully
     *
     * @param rssi   RSSI
     * @param device the remote device
     */
    void onRssiSuccess(int rssi, BleDevice device);

    /**
     * Failed to read RSSI from the remote device
     *
     * @param errCode If it's sdk custom error, it will be one of the following:
     *                {@link com.ficat.easyble.BleErrorCodes#CONNECTION_NOT_ESTABLISHED}
     *                {@link com.ficat.easyble.BleErrorCodes#TIMEOUT}
     *                {@link com.ficat.easyble.BleErrorCodes#UNKNOWN}
     *                Or it belongs to gatt error codes, like
     *                {@link com.ficat.easyble.BleErrorCodes#GATT_INSUFFICIENT_AUTHORIZATION},
     *                {@link com.ficat.easyble.BleErrorCodes#GATT_FAILURE} and so on.
     * @param device  the remote device
     */
    void onRssiFailed(int errCode, BleDevice device);
}
