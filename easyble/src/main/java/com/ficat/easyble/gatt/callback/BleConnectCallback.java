package com.ficat.easyble.gatt.callback;


import com.ficat.easyble.BleDevice;

public interface BleConnectCallback {
    void onStart(boolean startConnectSuccess, String info, BleDevice device);

    void onTimeout(BleDevice device);

    void onConnected(BleDevice device);

    void onDisconnected(BleDevice device);
}
