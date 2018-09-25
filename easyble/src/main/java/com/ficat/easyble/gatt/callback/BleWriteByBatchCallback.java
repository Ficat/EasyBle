package com.ficat.easyble.gatt.callback;


import com.ficat.easyble.BleDevice;

public interface BleWriteByBatchCallback extends BleCallback {
    void writeByBatchSuccess(byte[] data, BleDevice device);
}
