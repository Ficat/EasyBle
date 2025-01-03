package com.ficat.easyble.gatt.callback;


import com.ficat.easyble.BleDevice;

/**
 * Created by pw on 2018/9/13.
 */

public interface BleCallback {
    int FAIL_DISCONNECTED = 200; //connection with remote device is not established yet
    int FAIL_CONNECTION_TIMEOUT = 201;//connection timed out
    int FAIL_CONNECTION_CANCELED = 202;//connection canceled
    int FAIL_SERVICE_NOT_FOUND = 203; //specified service not found
    int FAIL_CHARACTERISTIC_NOT_FOUND_IN_SERVICE = 204; //specified characteristic not found in specified service
    int FAIL_NOTIFICATION_OR_INDICATION_UNSUPPORTED = 205; //specified characteristic doesn't support notification or indication
    int FAIL_READ_UNSUPPORTED = 206; //specified characteristic doesn't support reading
    int FAIL_WRITE_UNSUPPORTED = 207; //specified characteristic doesn't support writing
    int FAIL_OTHER = 208;//unknown reason

    void onFailure(int failCode, String info, BleDevice device);
}
