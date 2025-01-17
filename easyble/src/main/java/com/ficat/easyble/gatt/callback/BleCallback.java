package com.ficat.easyble.gatt.callback;


import com.ficat.easyble.BleDevice;

/**
 * Created by pw on 2018/9/13.
 */

public interface BleCallback {
    int FAILURE_CONNECTION_NOT_ESTABLISHED = 200; //connection not established yet
    int FAILURE_CONNECTION_TIMEOUT = 201;//connection timed out
    int FAILURE_CONNECTION_FAILED = 202;//connection failed
    int FAILURE_CONNECTION_CANCELED = 203;//connection canceled

    int FAILURE_SERVICE_NOT_FOUND = 300; //specified service not found
    int FAILURE_CHARACTERISTIC_NOT_FOUND_IN_SERVICE = 301; //characteristic not found in service
    int FAILURE_NOTIFICATION_OR_INDICATION_UNSUPPORTED = 302; //characteristic doesn't support notification/indication
    int FAILURE_READ_UNSUPPORTED = 303; //characteristic doesn't support reading
    int FAILURE_WRITE_UNSUPPORTED = 304; //characteristic doesn't support writing

    int FAILURE_OTHER = 400;//unknown reason

    void onFailure(int failureCode, String info, BleDevice device);
}
