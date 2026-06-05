package com.ficat.easyble.scan;

import android.annotation.SuppressLint;
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
import com.ficat.easyble.utils.Utils;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@SuppressLint("MissingPermission")
public final class BleScanner implements BleScan<BleScanCallback>, BleManager.BluetoothStateListen {
    private static final long SCAN_PERIOD_DEFAULT = 12000;

    private final BluetoothAdapter mBluetoothAdapter;
    private BluetoothAdapter.LeScanCallback mLeScanCallback;//sdk<21 uses this scan callback
    private ScanCallback mScanCallback;//SDK>=21 uses this scan callback
    private BleScanCallback mBleScanCallback;//all sdk version uses this scan callback
    private BluetoothLeScanner mBluetoothLeScanner;
    private String mDeviceName;
    private String mDeviceAddress;
    private UUID[] mServiceUuids;
    private boolean mFuzzyDeviceName;
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
    public void startScan(long scanPeriod, String scanDeviceName, String scanDeviceAddress,
                          UUID[] scanServiceUuids, boolean fuzzyDeviceName, final BleScanCallback callback) {
        if (!BleManager.isBluetoothEnabled()) {
            callback.onScanFailed(BleErrorCodes.BLUETOOTH_OFF);
            return;
        }
        if (!BleManager.scanPermissionGranted(BleManager.getInstance().getContext())) {
            callback.onScanFailed(BleErrorCodes.PERMISSION_MISSING);
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
            mFuzzyDeviceName = !TextUtils.isEmpty(scanDeviceName) && fuzzyDeviceName;
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
    private void stopScan(boolean callbackScanFinished) {
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
            // reset params
            mScanning = false;
            mFuzzyDeviceName = false;
            mDeviceName = null;
            mDeviceAddress = null;
            mServiceUuids = null;
            if (mBleScanCallback != null) {
                if (callbackScanFinished) {
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
    public void destroy(boolean callbackEnabledOnDestroy) {
        stopScan(callbackEnabledOnDestroy);
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
                    String name = device.getName();
                    if (!TextUtils.isEmpty(mDeviceName)) {
                        // Exact device name
                        if (!mFuzzyDeviceName && !mDeviceName.equals(name)) {
                            return;
                        }
                        // Fuzzy device name
                        if (mFuzzyDeviceName && (TextUtils.isEmpty(name) || !name.contains(mDeviceName))) {
                            return;
                        }
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
                    String name = result.getDevice().getName();
                    if (mFuzzyDeviceName && !TextUtils.isEmpty(mDeviceName) && (TextUtils.isEmpty(name) || !name.contains(mDeviceName))) {
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
                    BleScanCallback callback = null;
                    if (mScanning && mBleScanCallback != null) {
                        callback = mBleScanCallback;
                    }
                    stopScan(false);
                    if (callback == null) {
                        return;
                    }
                    switch (errorCode) {
                        case ScanCallback.SCAN_FAILED_ALREADY_STARTED:
                            callback.onScanFailed(BleErrorCodes.SCAN_ALREADY_STARTED);
                            break;
                        case ScanCallback.SCAN_FAILED_SCANNING_TOO_FREQUENTLY:
                            callback.onScanFailed(BleErrorCodes.SCAN_TOO_FREQUENTLY);
                            break;
                        case ScanCallback.SCAN_FAILED_APPLICATION_REGISTRATION_FAILED:
                        case ScanCallback.SCAN_FAILED_INTERNAL_ERROR:
                        case ScanCallback.SCAN_FAILED_FEATURE_UNSUPPORTED:
                        case ScanCallback.SCAN_FAILED_OUT_OF_HARDWARE_RESOURCES:
                        default:
                            callback.onScanFailed(BleErrorCodes.UNKNOWN);
                            break;
                    }
                }
            };
        }
        if (mBluetoothLeScanner == null) {
            mBluetoothLeScanner = mBluetoothAdapter.getBluetoothLeScanner();
        }

        boolean isScreenOff = !Utils.isScreenOn(BleManager.getInstance().getContext());
        boolean isBackground = !Utils.isForeground(BleManager.getInstance().getContext());
        boolean filterServiceUuids = mServiceUuids != null && mServiceUuids.length > 0;
        boolean filterExactName = !mFuzzyDeviceName && !TextUtils.isEmpty(mDeviceName);
        boolean filterAddress = !TextUtils.isEmpty(mDeviceAddress);
        boolean hasFilter = filterServiceUuids || filterExactName || filterAddress;

        if (isScreenOff && !hasFilter) {
            Logger.w("The screen is off, the current scan has no filters, it may be suspended until the screen is turned on again");
        }

        ScanSettings scanSettings = new ScanSettings.Builder()
                .setScanMode((isScreenOff || isBackground) ? ScanSettings.SCAN_MODE_LOW_POWER : ScanSettings.SCAN_MODE_LOW_LATENCY)
                .build();

        List<ScanFilter> scanFilters = new ArrayList<>();
        ScanFilter filter = new ScanFilter.Builder()
                .setDeviceName(filterExactName ? mDeviceName : null)
                .setDeviceAddress(mDeviceAddress)
                .setServiceUuid(filterServiceUuids ? new ParcelUuid(mServiceUuids[0]) : null)
                .build();
        scanFilters.add(filter);

        mBluetoothLeScanner.startScan(scanFilters, scanSettings, mScanCallback);
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
