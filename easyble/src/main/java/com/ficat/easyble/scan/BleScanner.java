package com.ficat.easyble.scan;

import android.annotation.TargetApi;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanRecord;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelUuid;
import android.text.TextUtils;

import com.ficat.easyble.BleDevice;
import com.ficat.easyble.BleDeviceAccessor;
import com.ficat.easyble.BleErrorCodes;
import com.ficat.easyble.BleManager;
import com.ficat.easyble.utils.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class BleScanner implements BleScan<BleScanCallback>, BleManager.BluetoothStateListen {
    private static final int SCAN_PERIOD_DEFAULT = 12000;

    protected BluetoothAdapter mBluetoothAdapter;
    private BluetoothAdapter.LeScanCallback mLeScanCallback;//sdk<21 uses this scan callback
    private ScanCallback mScanCallback;//SDK>=21 uses this scan callback
    private BleScanCallback mBleScanCallback;//all sdk version uses this scan callback
    private BluetoothLeScanner mBluetoothLeScanner;
    private ScanSettings mScanSettings;
    private String mDeviceName;
    private String mDeviceAddress;
    private UUID[] mServiceUuids;
    private volatile boolean mScanning;
    private final Handler mHandler;
    private final Runnable mScanTimeoutRunnable = new Runnable() {
        @Override
        public void run() {
            stopScan();
        }
    };

    /**
     * The key to obtain some objects, like BleDevice instance
     */
    private final AccessKey mAccessorKey = new AccessKey();

    BleScanner() {
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        mHandler = new Handler(Looper.getMainLooper());
    }

    @Override
    public void startScan(int scanPeriod, String scanDeviceName, String scanDeviceAddress,
                          UUID[] scanServiceUuids, final BleScanCallback callback) {
        if (!BleManager.isBluetoothOn()) {
            callback.onScanFailed(BleErrorCodes.BLUETOOTH_OFF);
            return;
        }
        if (!BleManager.scanPermissionGranted(BleManager.getInstance().getContext())) {
            callback.onScanFailed(BleErrorCodes.SCAN_PERMISSION_NOT_GRANTED);
            return;
        }
        synchronized (this) {
            if (mScanning) {
                callback.onScanFailed(BleErrorCodes.SCAN_ALREADY_STARTED);
                return;
            }
            mBleScanCallback = callback;
            mDeviceName = scanDeviceName;
            mDeviceAddress = scanDeviceAddress;
            mServiceUuids = scanServiceUuids;
            mScanning = sdkVersionLowerThan21() ? scanByOldApi() : scanByNewApi();
            if (mBleScanCallback != null) {
                if (mScanning) {
                    mBleScanCallback.onScanStarted();
                } else {
                    mBleScanCallback.onScanFailed(BleErrorCodes.UNKNOWN);
                }
            }
            if (mScanning) {
                mHandler.postDelayed(mScanTimeoutRunnable, scanPeriod > 0 ? scanPeriod : SCAN_PERIOD_DEFAULT);
            }
        }
    }

    @Override
    public void stopScan() {
        stopScan(true);
    }

    @SuppressWarnings("NewApi")
    private void stopScan(boolean stopNormally) {
        synchronized (this) {
            if (mBluetoothAdapter == null || !mScanning) {
                return;
            }
            if (sdkVersionLowerThan21()) {
                if (mLeScanCallback != null) {
                    mBluetoothAdapter.stopLeScan(mLeScanCallback);
                }
            } else {
                // If bluetooth is turned off, stopScan() will throw an exception
                if (mBluetoothLeScanner != null && mBluetoothAdapter.isEnabled() && mScanCallback != null) {
                    mBluetoothLeScanner.stopScan(mScanCallback);
                }
            }
            mScanning = false;
            if (mBleScanCallback != null) {
                if (stopNormally) {
                    mBleScanCallback.onScanFinished();
                }
                mBleScanCallback = null;
            }
            mHandler.removeCallbacks(mScanTimeoutRunnable);
        }
    }

    @Override
    public boolean isScanning() {
        return mScanning;
    }

    @Override
    public void onBluetoothStateChanged(int state) {
        if (state == BluetoothAdapter.STATE_OFF) {
            stopScan();
        }
    }

    @Override
    public void destroy() {
        stopScan();
        // Remove scan period delayed message
        mHandler.removeCallbacksAndMessages(null);
    }

    private boolean scanByOldApi() {
        if (mBluetoothAdapter == null || !mBluetoothAdapter.isEnabled()) {
            return false;
        }
        if (mLeScanCallback == null) {
            mLeScanCallback = new BluetoothAdapter.LeScanCallback() {
                @Override
                public void onLeScan(final BluetoothDevice device, final int rssi, final byte[] scanRecord) {
                    if (!TextUtils.isEmpty(mDeviceName) && !mDeviceName.equals(device.getName())) {
                        return;
                    }
                    if (!TextUtils.isEmpty(mDeviceAddress) && !mDeviceAddress.equals(device.getAddress())) {
                        return;
                    }
                    if (mBleScanCallback != null) {
                        BleDevice bleDevice = BleDeviceAccessor.newBleDevice(device, mAccessorKey);
                        mBleScanCallback.onScanning(bleDevice, rssi, scanRecord);
                    }
                }
            };
        }
        return mBluetoothAdapter.startLeScan(mServiceUuids, mLeScanCallback);
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private boolean scanByNewApi() {
        // BluetoothAdapter#getBluetoothLeScanner() will be null and BluetoothLeScanner#startScan()
        // will throw an exception if bluetooth is turned off, so check it
        if (mBluetoothAdapter == null || !mBluetoothAdapter.isEnabled()) {
            return false;
        }
        if (mScanCallback == null) {
            mScanCallback = new ScanCallback() {
                @Override
                public void onScanResult(int callbackType, final ScanResult result) {
                    super.onScanResult(callbackType, result);
                    if (!hasResultByFilterUuids(result)) {
                        return;
                    }
                    if (mBleScanCallback == null) {
                        return;
                    }
                    byte[] scanRecord = (result.getScanRecord() == null) ? new byte[]{} : result.getScanRecord().getBytes();
                    BleDevice bleDevice = BleDeviceAccessor.newBleDevice(result.getDevice(), mAccessorKey);
                    mBleScanCallback.onScanning(bleDevice, result.getRssi(), scanRecord);
                }

                @Override
                public void onBatchScanResults(List<ScanResult> results) {
                    for (ScanResult sr : results) {
                        Logger.i("Batch scan results: " + sr.toString());
                    }
                }

                @Override
                public void onScanFailed(int errorCode) {
                    if (mScanning && mBleScanCallback != null) {
                        switch (errorCode) {
                            case ScanCallback.SCAN_FAILED_ALREADY_STARTED:
                                mBleScanCallback.onScanFailed(BleErrorCodes.SCAN_ALREADY_STARTED);
                                break;
                            case ScanCallback.SCAN_FAILED_SCANNING_TOO_FREQUENTLY:
                                mBleScanCallback.onScanFailed(BleErrorCodes.SCAN_TOO_FREQUENTLY);
                                break;
                            case ScanCallback.SCAN_FAILED_APPLICATION_REGISTRATION_FAILED:
                            case ScanCallback.SCAN_FAILED_INTERNAL_ERROR:
                            case ScanCallback.SCAN_FAILED_FEATURE_UNSUPPORTED:
                            case ScanCallback.SCAN_FAILED_OUT_OF_HARDWARE_RESOURCES:
                            default:
                                mBleScanCallback.onScanFailed(BleErrorCodes.UNKNOWN);
                                break;
                        }
                    }
                    stopScan(false);
                }
            };
        }
        if (mBluetoothLeScanner == null) {
            mBluetoothLeScanner = mBluetoothAdapter.getBluetoothLeScanner();
        }
        if (mScanSettings == null) {
            mScanSettings = new ScanSettings.Builder()
                    .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                    .build();
        }
        List<ScanFilter> scanFilters = new ArrayList<>();
        ScanFilter filter = new ScanFilter.Builder()
                .setDeviceName(mDeviceName)
                .setDeviceAddress(mDeviceAddress)
                .build();
        scanFilters.add(filter);
        mBluetoothLeScanner.startScan(scanFilters, mScanSettings, mScanCallback);
        return true;
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private boolean hasResultByFilterUuids(ScanResult result) {
        if (mServiceUuids == null || mServiceUuids.length <= 0) {//no filtered uuids
            return true;
        }
        ScanRecord scanRecord = result.getScanRecord();
        if (scanRecord == null) {
            return false;
        }
        List<ParcelUuid> serviceUuidList = new ArrayList<>();
        for (UUID uuid : mServiceUuids) {
            serviceUuidList.add(new ParcelUuid(uuid));
        }
        List<ParcelUuid> scanServiceUuids = result.getScanRecord().getServiceUuids();
        return scanServiceUuids != null && scanServiceUuids.containsAll(serviceUuidList);
    }

    private boolean sdkVersionLowerThan21() {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP;
    }

    public static final class AccessKey {
        private AccessKey() {

        }
    }
}
