package com.ficat.easyble.gatt.callback;


import com.ficat.easyble.BleDevice;

public interface BleWriteCallback extends BleCallback {
    void onWriteSuccess(byte[] data, BleDevice device);
}
