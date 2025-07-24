package com.ficat.easyble.scan;


import com.ficat.easyble.BleManager;

public class BleScanAccessor {
    /**
     * Create a BleScan instance
     *
     * @param key access key.
     * @return BleScan instance
     */
    public static BleScan<BleScanCallback> newBleScan(Object key) {
        if (!(key instanceof BleManager.AccessKey)) {
            throw new SecurityException("Invalid key");
        }
        return new BleScanner();
    }
}
