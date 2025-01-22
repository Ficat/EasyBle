package com.ficat.easyble;

import android.bluetooth.BluetoothDevice;

import com.ficat.easyble.gatt.BleGattImpl;
import com.ficat.easyble.scan.BleScanner;


public class BleDeviceAccessor {
    /**
     * Create a BleDevice instance
     *
     * @param device BluetoothDevice
     * @param key    access key
     * @return BleDevice instance
     */
    public static BleDevice newBleDevice(BluetoothDevice device, Object key) {
        // Thread.currentThread().getStackTrace();//not a good solution (performance / code-obfuscation)
        if (device == null) {
            throw new IllegalArgumentException("BluetoothDevice is null");
        }
        if (!(key instanceof BleScanner.AccessKey)) {
            throw new SecurityException("Invalid key");
        }
        return new BleDevice(device);
    }

    /**
     * Access the method {@link BleDevice#setConnectionState(int)} to set connection state
     *
     * @param device       target BleDevice
     * @param newConnState new connection state
     * @param key          access key
     */
    public static void setBleDeviceConnection(BleDevice device, int newConnState, Object key) {
        if (device == null) {
            throw new IllegalArgumentException("BleDevice is null");
        }
        if (!(key instanceof BleGattImpl.AccessKey)) {
            throw new SecurityException("Invalid key");
        }
        device.setConnectionState(newConnState);
    }

}
