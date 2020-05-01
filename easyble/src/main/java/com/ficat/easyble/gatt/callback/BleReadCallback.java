package com.ficat.easyble.gatt.callback;


import com.ficat.easyble.BleDevice;

public interface BleReadCallback extends BleCallback {
    void onReadSuccess(byte[] data, BleDevice device);
}
