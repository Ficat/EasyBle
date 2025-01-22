package com.ficat.easyble.scan;


import com.ficat.easyble.BleManager;
import com.ficat.easyble.BleReceiver;

public class BleScanAccessor {
    /**
     * Create a BleScan instance
     *
     * @param bleReceiver BleReceiver used to listen bluetooth state.
     * @param key         access key.
     * @return BleScan instance
     */
    public static BleScan<BleScanCallback> newBleScan(BleReceiver bleReceiver, Object key) {
        if (bleReceiver == null) {
            throw new IllegalArgumentException("BleReceiver is null");
        }
        if (!(key instanceof BleManager.AccessKey)) {
            throw new SecurityException("Invalid key");
        }
        return new BleScanner(bleReceiver);
    }
}
