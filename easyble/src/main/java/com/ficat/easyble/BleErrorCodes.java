package com.ficat.easyble;

import android.bluetooth.BluetoothGatt;

public class BleErrorCodes {
    /**********************************SDK custom error constants********************************/
    /**
     * NO ble required permissions
     */
    public static final int PERMISSION_MISSING = 1000;


    /**
     * Bluetooth turned off.
     */
    public static final int BLUETOOTH_OFF = 1001;


    /**
     * Current API Version dost not support this operation
     */
    public static final int API_VERSION_NOT_SUPPORTED = 1002;

    /**
     * Timed out
     */
    public static final int TIMEOUT = 1003;

    /**
     * Scan permissions not granted.
     *
     * @deprecated Use {@linkplain #PERMISSION_MISSING} instead.
     */
    @Deprecated
    public static final int SCAN_PERMISSION_NOT_GRANTED = PERMISSION_MISSING;

    /**
     * Previous scan not finished
     */
    public static final int SCAN_ALREADY_STARTED = 1010;

    /**
     * Scan too frequently
     */
    public static final int SCAN_TOO_FREQUENTLY = 1011;

    /**
     * Connection permissions not granted
     *
     * @deprecated Use {@linkplain #PERMISSION_MISSING} instead.
     */
    @Deprecated
    public static final int CONNECTION_PERMISSION_NOT_GRANTED = PERMISSION_MISSING;

    /**
     * The maximum number of connections has been reached
     */
    public static final int CONNECTION_REACH_MAX_NUM = 1020;

    /**
     * Connection timed out
     *
     * @deprecated Use {@linkplain #TIMEOUT} instead.
     */
    @Deprecated
    public static final int CONNECTION_TIMEOUT = TIMEOUT;

    /**
     * Connection canceled
     */
    public static final int CONNECTION_CANCELED = 1021;

    /**
     * Connection already started or established
     */
    public static final int CONNECTION_ALREADY_STARTED_OR_ESTABLISHED = 1022;

    /**
     * Connection not established yet
     */
    public static final int CONNECTION_NOT_ESTABLISHED = 1030;

    /**
     * Service does not found in current remote device
     */
    public static final int SERVICE_NOT_FOUND = 1031;

    /**
     * Characteristic does not found in specific service
     */
    public static final int CHARACTERISTIC_NOT_FOUND_IN_SERVICE = 1032;

    /**
     * Descriptor does not found in specific characteristic
     */
    public static final int DESCRIPTOR_NOT_FOUND_IN_CHARACTERISTIC = 1033;

    /**
     * Target characteristic doesn't support notification or indication
     */
    public static final int NOTIFICATION_OR_INDICATION_UNSUPPORTED = 1034;

    /**
     * Read-Operation not supported in target characteristic
     */
    public static final int READ_UNSUPPORTED = 1035;

    /**
     * Write-Operation not supported in target characteristic
     */
    public static final int WRITE_UNSUPPORTED = 1036;

    /**
     * The data length is greater than MTU while writing data or writing data in batches
     */
    public static final int DATA_LENGTH_GREATER_THAN_MTU = 1037;

    /**
     * Other unknown reason
     */
    public static final int UNKNOWN = 1040;


    /**********************{@link BluetoothGatt}GATT error constants*************************/
    /**
     * GATT read operation is not permitted
     */
    public static final int GATT_READ_NOT_PERMITTED = BluetoothGatt.GATT_READ_NOT_PERMITTED;

    /**
     * GATT write operation is not permitted
     */
    public static final int GATT_WRITE_NOT_PERMITTED = BluetoothGatt.GATT_WRITE_NOT_PERMITTED;

    /**
     * Insufficient authentication for a given operation
     */
    public static final int GATT_INSUFFICIENT_AUTHENTICATION = BluetoothGatt.GATT_INSUFFICIENT_AUTHENTICATION;

    /**
     * The given request is not supported
     */
    public static final int GATT_REQUEST_NOT_SUPPORTED = BluetoothGatt.GATT_REQUEST_NOT_SUPPORTED;

    /**
     * Insufficient encryption for a given operation
     */
    public static final int GATT_INSUFFICIENT_ENCRYPTION = BluetoothGatt.GATT_INSUFFICIENT_ENCRYPTION;

    /**
     * A read or write operation was requested with an invalid offset
     */
    public static final int GATT_INVALID_OFFSET = BluetoothGatt.GATT_INVALID_OFFSET;

    /**
     * Insufficient authorization for a given operation
     */
    public static final int GATT_INSUFFICIENT_AUTHORIZATION = 0x8;

    /**
     * A write operation exceeds the maximum length of the attribute
     */
    public static final int GATT_INVALID_ATTRIBUTE_LENGTH = 0xd;

    /**
     * A remote device connection is congested.
     */
    public static final int GATT_CONNECTION_CONGESTED = 0x8f;

    /**
     * GATT connection timed out, likely due to the remote device being out of range or not
     * advertising as connectable.
     */
    public static final int GATT_CONNECTION_TIMEOUT = 0x93;

    /**
     * A GATT operation failed, errors other than the above gatt error constants
     */
    public static final int GATT_FAILURE = BluetoothGatt.GATT_FAILURE;
}
