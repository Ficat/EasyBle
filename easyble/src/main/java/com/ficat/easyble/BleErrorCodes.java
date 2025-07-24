package com.ficat.easyble;

public class BleErrorCodes {
    /**
     * Current API Version dost not support this operation
     */
    public static final int API_VERSION_NOT_SUPPORTED = 1;

    /**
     * Bluetooth turned off.
     */
    public static final int BLUETOOTH_OFF = 2;

    /**
     * Scan permissions not granted.
     */
    public static final int SCAN_PERMISSION_NOT_GRANTED = 11;

    /**
     * Previous scan not finished
     */
    public static final int SCAN_ALREADY_STARTED = 12;

    /**
     * Scan too frequently
     */
    public static final int SCAN_TOO_FREQUENTLY = 13;

    /**
     * Connection permissions not granted
     */
    public static final int CONNECTION_PERMISSION_NOT_GRANTED = 20;

    /**
     * The maximum number of connections has been reached
     */
    public static final int CONNECTION_REACH_MAX_NUM = 21;

    /**
     * Connection timed out
     */
    public static final int CONNECTION_TIMEOUT = 22;

    /**
     * Connection canceled
     */
    public static final int CONNECTION_CANCELED = 23;

    /**
     * Connection not established yet
     */
    public static final int CONNECTION_NOT_ESTABLISHED = 30;

    /**
     * Service does not found in current remote device
     */
    public static final int SERVICE_NOT_FOUND = 31;

    /**
     * Characteristic does not found in specific service
     */
    public static final int CHARACTERISTIC_NOT_FOUND_IN_SERVICE = 32;

    /**
     * Target characteristic doesn't support notification or indication
     */
    public static final int NOTIFICATION_OR_INDICATION_UNSUPPORTED = 33;

    /**
     * Read-Operation not supported in target characteristic
     */
    public static final int READ_UNSUPPORTED = 34;

    /**
     * Write-Operation not supported in target characteristic
     */
    public static final int WRITE_UNSUPPORTED = 35;

    /**
     * The data length is greater than MTU while writing data or writing data in batches
     */
    public static final int DATA_LENGTH_GREATER_THAN_MTU = 36;

    /**
     * Other unknown reason
     */
    public static final int UNKNOWN = 40;
}
