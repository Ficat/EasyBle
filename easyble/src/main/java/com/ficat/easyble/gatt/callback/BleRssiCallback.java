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
     * @param errCode see details from the following codes
     *                {@link com.ficat.easyble.BleErrorCodes#CONNECTION_NOT_ESTABLISHED}
     *                {@link com.ficat.easyble.BleErrorCodes#UNKNOWN}
     * @param device  the remote device
     */
    void onRssiFailed(int errCode, BleDevice device);
}
