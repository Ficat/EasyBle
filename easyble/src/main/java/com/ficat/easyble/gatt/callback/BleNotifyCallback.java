package com.ficat.easyble.gatt.callback;


import com.ficat.easyble.BleDevice;

public interface BleNotifyCallback extends BleCallback {
    void onCharacteristicChanged(byte[] data, BleDevice device);
}
