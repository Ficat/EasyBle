package com.ficat.easyble.gatt.callback;


import com.ficat.easyble.BleDevice;


public interface BleMtuCallback {
    /**
     * MTU is set successfully
     *
     * @param mtu    current MTU
     * @param device the remote device
     */
    void onMtuChanged(int mtu, BleDevice device);

    /**
     * Failed to set MTU
     *
     * @param errCode see details from the following codes
     *                {@link com.ficat.easyble.BleErrorCodes#API_VERSION_NOT_SUPPORTED}
     *                {@link com.ficat.easyble.BleErrorCodes#CONNECTION_NOT_ESTABLISHED}
     *                {@link com.ficat.easyble.BleErrorCodes#UNKNOWN}
     * @param device  the remote device
     */
    void onMtuFailed(int errCode, BleDevice device);
}
