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
import com.ficat.easyble.BleReceiver;
import com.ficat.easyble.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class BleScanner implements BleScan<BleScanCallback>, BleReceiver.BluetoothStateChangedListener {
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
    private final BleReceiver mReceiver;
    private final Runnable mScanTimeoutRunnable = new Runnable() {
        @Override
        public void run() {
            stopScan();
        }
    };

    /**
     * The key to obtain some object, like BleDevice instance
     */
    private final AccessKey mAccessorKey = new AccessKey();

    BleScanner(BleReceiver bleReceiver) {
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        mHandler = new Handler(Looper.getMainLooper());
        mReceiver = bleReceiver;
        // Register BluetoothStateChangedListener
        if (mReceiver != null) {
            mReceiver.registerBluetoothStateChangedListener(this);
        }
    }

    @Override
    public void startScan(int scanPeriod, String scanDeviceName, String scanDeviceAddress,
                          UUID[] scanServiceUuids, final BleScanCallback callback) {
        synchronized (this) {
            if (mScanning) {
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        callback.onStart(false, "The previous scan has not ended yet");
                    }
                });
                return;
            }
            mBleScanCallback = callback;
            mDeviceName = scanDeviceName;
            mDeviceAddress = scanDeviceAddress;
            mServiceUuids = scanServiceUuids;
            mScanning = sdkVersionLowerThan21() ? scanByOldApi() : scanByNewApi();
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    if (mBleScanCallback != null) {
                        mBleScanCallback.onStart(mScanning, mScanning ? "Scan start" :
                                "Failed to start scanning due to unknown reason");
                    }
                }
            });
            if (mScanning) {
                mHandler.postDelayed(mScanTimeoutRunnable, scanPeriod > 0 ? scanPeriod : SCAN_PERIOD_DEFAULT);
            }
        }
    }

    @SuppressWarnings("NewApi")
    @Override
    public synchronized void stopScan() {
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
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    if (mBleScanCallback != null) {
                        mBleScanCallback.onFinish();
                        mBleScanCallback = null;
                    }
                }
            });
            mHandler.removeCallbacks(mScanTimeoutRunnable);
        }
    }

    @Override
    public boolean isScanning() {
        return mScanning;
    }

    @Override
    public void onBluetoothStateChanged() {
        if (mBluetoothAdapter.getState() == BluetoothAdapter.STATE_OFF) {
            stopScan();
        }
    }

    @Override
    public void destroy() {
        stopScan();
        // Remove scan period delayed message
        mHandler.removeCallbacksAndMessages(null);
        // Unregister BluetoothStateChangedListener
        if (mReceiver != null) {
            mReceiver.unregisterBluetoothStateChangedListener(this);
        }
    }

    private boolean scanByOldApi() {
        if (mBluetoothAdapter == null || !mBluetoothAdapter.isEnabled()) {
            return false;
        }
        if (mLeScanCallback == null) {
            mLeScanCallback = new BluetoothAdapter.LeScanCallback() {
                @Override
                public void onLeScan(final BluetoothDevice device, final int rssi, final byte[] scanRecord) {
                    mHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            if (!TextUtils.isEmpty(mDeviceName) && !mDeviceName.equals(device.getName())) {
                                return;
                            }
                            if (!TextUtils.isEmpty(mDeviceAddress) && !mDeviceAddress.equals(device.getAddress())) {
                                return;
                            }
                            if (mBleScanCallback != null) {
                                BleDevice bleDevice = BleDeviceAccessor.newBleDevice(device, mAccessorKey);
                                mBleScanCallback.onLeScan(bleDevice, rssi, scanRecord);
                            }
                        }
                    });
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
                    mHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            if (!hasResultByFilterUuids(result)) {
                                return;
                            }
                            if (mBleScanCallback == null) {
                                return;
                            }
                            byte[] scanRecord = (result.getScanRecord() == null) ? new byte[]{} : result.getScanRecord().getBytes();
                            BleDevice bleDevice = BleDeviceAccessor.newBleDevice(result.getDevice(), mAccessorKey);
                            mBleScanCallback.onLeScan(bleDevice, result.getRssi(), scanRecord);
                        }
                    });
                }

                @Override
                public void onBatchScanResults(List<ScanResult> results) {
                    for (ScanResult sr : results) {
                        Logger.i("Batch scan results: " + sr.toString());
                    }
                }

                @Override
                public void onScanFailed(int errorCode) {
                    Logger.i("Ble scan fail: " + errorCode);
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
