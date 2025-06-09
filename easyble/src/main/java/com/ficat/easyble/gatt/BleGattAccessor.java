package com.ficat.easyble.gatt;

import com.ficat.easyble.BleManager;

public class BleGattAccessor {
    /**
     * Create a BleGatt instance
     *
     * @param key access key.
     * @return BleGatt instance
     */
    public static BleGatt newBleGatt(Object key) {

        if (!(key instanceof BleManager.AccessKey)) {
            throw new SecurityException("Invalid key");
        }
        return new BleGattImpl();
    }
}
