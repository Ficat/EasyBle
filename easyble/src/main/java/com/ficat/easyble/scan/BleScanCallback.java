package com.ficat.easyble.scan;


import com.ficat.easyble.BleDevice;
import com.ficat.easyble.BleErrorCodes;

public interface BleScanCallback {

    void onScanning(BleDevice device, int rssi, byte[] scanRecord);

    void onScanStarted();

    void onScanFinished();

    /**
     * Scan failed
     *
     * @param code see {@link BleErrorCodes#BLUETOOTH_OFF}
     *             {@link BleErrorCodes#SCAN_PERMISSION_NOT_GRANTED}
     *             {@link BleErrorCodes#SCAN_ALREADY_STARTED}
     *             {@link BleErrorCodes#SCAN_TOO_FREQUENTLY}
     *             {@link BleErrorCodes#UNKNOWN}
     */
    void onScanFailed(int code);
}
