package com.ficat.easyble.gatt.callback;


import com.ficat.easyble.BleDevice;

public interface BleConnectCallback extends BleCallback {
    void onStart(boolean startSuccess, String info, BleDevice device);

    void onConnected(BleDevice device);

    void onDisconnected(String info, int status, BleDevice device);
}
