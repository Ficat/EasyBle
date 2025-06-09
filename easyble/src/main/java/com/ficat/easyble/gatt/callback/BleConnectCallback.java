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
     * @param gattOperationStatus operation status, see details from {@link android.bluetooth.BluetoothGatt}
     *                            normally, it will be {@link android.bluetooth.BluetoothGatt#GATT_SUCCESS},
     *                            otherwise it indicates an abnormal situation.
     * @param device              the remote device
     */
    void onDisconnected(BleDevice device, int gattOperationStatus);

    /**
     * Failed to connect to the remote device
     *
     * @param errCode see details from the following codes
     *                {@link com.ficat.easyble.BleErrorCodes#BLUETOOTH_OFF}
     *                {@link com.ficat.easyble.BleErrorCodes#CONNECTION_PERMISSION_NOT_GRANTED}
     *                {@link com.ficat.easyble.BleErrorCodes#CONNECTION_REACH_MAX_NUM}
     *                {@link com.ficat.easyble.BleErrorCodes#CONNECTION_TIMEOUT}
     *                {@link com.ficat.easyble.BleErrorCodes#CONNECTION_CANCELED}
     *                {@link com.ficat.easyble.BleErrorCodes#UNKNOWN}
     * @param device  the remote device
     */
    void onConnectionFailed(int errCode, BleDevice device);
}
