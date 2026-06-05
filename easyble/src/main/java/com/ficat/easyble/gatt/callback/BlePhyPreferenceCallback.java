package com.ficat.easyble.gatt.callback;

import com.ficat.easyble.BleDevice;

public interface BlePhyPreferenceCallback {
    /**
     * Phy changed
     *
     * @param txPhy  the transmitter PHY in use. One of
     *               {@link android.bluetooth.BluetoothDevice#PHY_LE_1M},
     *               {@link android.bluetooth.BluetoothDevice#PHY_LE_2M},
     *               {@link android.bluetooth.BluetoothDevice#PHY_LE_CODED}
     * @param rxPhy  the receiver PHY in use. One of
     *               {@link android.bluetooth.BluetoothDevice#PHY_LE_1M},
     *               {@link android.bluetooth.BluetoothDevice#PHY_LE_2M},
     *               {@link android.bluetooth.BluetoothDevice#PHY_LE_CODED}
     * @param device the remote device
     */
    void onPhyChanged(int txPhy, int rxPhy, BleDevice device);

    /**
     * Failed to read phy
     *
     * @param errCode If it's sdk custom error, it will be one of the following:
     *                {@link com.ficat.easyble.BleErrorCodes#API_VERSION_NOT_SUPPORTED}
     *                {@link com.ficat.easyble.BleErrorCodes#CONNECTION_NOT_ESTABLISHED}
     *                {@link com.ficat.easyble.BleErrorCodes#TIMEOUT}
     *                {@link com.ficat.easyble.BleErrorCodes#UNKNOWN}
     *                Or it belongs to gatt error codes, like
     *                {@link com.ficat.easyble.BleErrorCodes#GATT_INSUFFICIENT_AUTHORIZATION},
     *                {@link com.ficat.easyble.BleErrorCodes#GATT_FAILURE} and so on.
     * @param device  the remote device
     */
    void onPhyPreferenceSetFailed(int errCode, BleDevice device);
}
