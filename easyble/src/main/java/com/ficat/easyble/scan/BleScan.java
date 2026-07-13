package com.ficat.easyble.scan;

import java.util.List;


public interface BleScan<T> {
    void startScan(long scanPeriod, List<BleScanFilter> scanFilters, T callback);

    void stopScan();

    boolean isScanning();

    void destroy(boolean callbackEnabledOnDestroy);
}
