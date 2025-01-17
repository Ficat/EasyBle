package com.ficat.easyble.gatt;

import android.content.Context;

import com.ficat.easyble.BleManager;

public class BleGattAccessor {
    /**
     * Create a BleGatt instance
     *
     * @param context context
     * @param key     access key.
     * @return BleGatt instance
     */
    public static BleGatt newBleGatt(Context context, Object key) {
        if (context == null) {
            throw new IllegalArgumentException("Context is null");
        }
        if (!(key instanceof BleManager.AccessKey)) {
            throw new SecurityException("Invalid key");
        }
        return new BleGattImpl(context);
    }
}
