package com.ficat.easyble.scan;

public interface BleScan<T> {
    void startScan(T callback);

    void stopScan();

    void destroy();
}
