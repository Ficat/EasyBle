package com.ficat.easyble.utils;

import android.bluetooth.BluetoothGattCharacteristic;

public class BluetoothGattUtils {
    public static boolean isCharacteristicReadable(BluetoothGattCharacteristic characteristic) {
        return characteristic != null && (characteristic.getProperties() & BluetoothGattCharacteristic.PROPERTY_READ) > 0;
    }

    public static boolean isCharacteristicWritable(BluetoothGattCharacteristic characteristic) {
        return characteristic != null && (characteristic.getProperties() & (BluetoothGattCharacteristic.PROPERTY_WRITE |
                BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE)) > 0;
    }

    public static boolean isCharacteristicNotifiable(BluetoothGattCharacteristic characteristic) {
        return characteristic != null && (characteristic.getProperties() & BluetoothGattCharacteristic.PROPERTY_NOTIFY) > 0;
    }

    public static boolean isCharacteristicIndicative(BluetoothGattCharacteristic characteristic) {
        return characteristic != null && (characteristic.getProperties() & BluetoothGattCharacteristic.PROPERTY_INDICATE) > 0;
    }
}
