package com.ficat.easyble.gatt.callback;


import com.ficat.easyble.BleDevice;


public interface BleMtuCallback extends BleCallback {
    void onMtuChanged(int mtu, BleDevice device);
}
