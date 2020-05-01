package com.ficat.easyble.gatt.callback;


import com.ficat.easyble.BleDevice;

/**
 * Created by pw on 2018/9/13.
 */

public interface BleCallback {
    int FAIL_DISCONNECTED = 200;
    int FAIL_OTHER = 201;

    void onFailure(int failCode, String info, BleDevice device);
}
