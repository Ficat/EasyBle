package com.ficat.easyble;

import android.bluetooth.BluetoothDevice;

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
}
