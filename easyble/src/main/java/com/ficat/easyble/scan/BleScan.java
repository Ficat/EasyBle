package com.ficat.easyble.scan;

import java.util.UUID;

public interface BleScan<T> {
    void startScan(int scanPeriod, String scanDeviceName, String scanDeviceAddress, UUID[] scanServiceUuids, T callback);

    void stopScan();

    boolean isScanning();

    void destroy();
}
