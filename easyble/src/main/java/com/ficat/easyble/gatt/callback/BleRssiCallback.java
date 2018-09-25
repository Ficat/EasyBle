package com.ficat.easyble.gatt.callback;


import com.ficat.easyble.BleDevice;

public interface BleRssiCallback extends BleCallback {

    void onRssi(int rssi, BleDevice bleDevice);
}
