package com.ficat.easyble.scan;


import com.ficat.easyble.BleDevice;
import com.ficat.easyble.BleErrorCodes;

public interface BleScanCallback {
    /**
     * Bluetooth disabled
     */
    int BLUETOOTH_OFF = BleErrorCodes.BLUETOOTH_OFF;

    /**
     * Scan permissions not granted.
     */
    int SCAN_PERMISSION_NOT_GRANTED = BleErrorCodes.SCAN_PERMISSION_NOT_GRANTED;

    /**
     * Previous scan not finished
     */
    int PREVIOUS_SCAN_NOT_FINISHED = BleErrorCodes.PREVIOUS_SCAN_NOT_FINISHED;

    /**
     * Failed to start scan because of unknown reason
     */
    int SCAN_FAILED = BleErrorCodes.SCAN_FAILED;

    void onScanning(BleDevice device, int rssi, byte[] scanRecord);

    void onScanStarted();

    void onScanFinished();

    /**
     * Scan failed
     *
     * @param code see {@link #BLUETOOTH_OFF}
     *                    {@link #SCAN_PERMISSION_NOT_GRANTED}
     *                    {@link #PREVIOUS_SCAN_NOT_FINISHED}
     *                    {@link #SCAN_FAILED}
     */
    void onScanFailed(int code);
}
