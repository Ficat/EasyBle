package com.ficat.easyble.gatt.callback;


import com.ficat.easyble.BleDevice;

public interface BleConnectCallback {
    /**
     * Connection started
     *
     * @param device target device
     */
    void onConnectionStarted(BleDevice device);

    /**
     * Connection with the remote device is established
     *
     * @param device target remote device
     */
    void onConnected(BleDevice device);

    /**
     * Disconnected from the remote device
     *
     * @param status operation status, if success, it will be
     *               {@link android.bluetooth.BluetoothGatt#GATT_SUCCESS}.
     *               Otherwise it indicates an abnormal situation, in this case, these codes
     *               will be like {@link com.ficat.easyble.BleErrorCodes#GATT_FAILURE},
     *               {@link com.ficat.easyble.BleErrorCodes#GATT_INSUFFICIENT_AUTHORIZATION}
     *               and so on.
     * @param device the remote device
     */
    void onDisconnected(BleDevice device, int status);

    /**
     * Failed to connect to the remote device
     *
     * @param errCode If it's sdk custom error, it will be one of the following:
     *                {@link com.ficat.easyble.BleErrorCodes#BLUETOOTH_OFF}
     *                {@link com.ficat.easyble.BleErrorCodes#PERMISSION_MISSING}
     *                {@link com.ficat.easyble.BleErrorCodes#CONNECTION_REACH_MAX_NUM}
     *                {@link com.ficat.easyble.BleErrorCodes#TIMEOUT}
     *                {@link com.ficat.easyble.BleErrorCodes#CONNECTION_CANCELED}
     *                {@link com.ficat.easyble.BleErrorCodes#CONNECTION_ALREADY_STARTED_OR_ESTABLISHED}
     *                {@link com.ficat.easyble.BleErrorCodes#UNKNOWN}.
     *                Or it belongs to gatt error codes, like
     *                {@link com.ficat.easyble.BleErrorCodes#GATT_INSUFFICIENT_AUTHORIZATION},
     *                {@link com.ficat.easyble.BleErrorCodes#GATT_FAILURE} and so on.
     * @param device  the remote device
     */
    void onConnectionFailed(int errCode, BleDevice device);
}
