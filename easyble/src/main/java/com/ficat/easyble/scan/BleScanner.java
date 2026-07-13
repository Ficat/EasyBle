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
    private List<BleScanFilter> mScanFilters;
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
    public void startScan(long scanPeriod, List<BleScanFilter> scanFilters, final BleScanCallback callback) {
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
            mScanFilters = scanFilters;
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
            mScanFilters = null;
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
                    BleScanRecord bleScanRecord = BleScanRecord.parseFromBytes(scanRecord);
                    if (!matchFilterList(device.getName(), device.getAddress(), bleScanRecord.getServiceUuids())) {
                        return;
                    }
                    if (mBleScanCallback != null) {
                        BleDevice bleDevice = BleDeviceAccessor.newBleDevice(device, mAccessorKey);
                        mBleScanCallback.onScanning(bleDevice, rssi, scanRecord);
                    }
                }
            };
        }
        UUID[] uuids = null;
        if (mScanFilters != null) {
            for (BleScanFilter filter : mScanFilters) {
                if (filter.getServiceUuid() != null) {
                    uuids = new UUID[]{filter.getServiceUuid()};
                    break;
                }
            }
        }
        return mBluetoothAdapter.startLeScan(uuids, mLeScanCallback);
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
                    ScanRecord scanRecord = result.getScanRecord();
                    BluetoothDevice device = result.getDevice();
                    if (!matchFilterList(device.getName(), device.getAddress(),
                            scanRecord == null ? null : scanRecord.getServiceUuids())) {
                        return;
                    }
                    if (mBleScanCallback == null) {
                        return;
                    }
                    byte[] scanBytes = (result.getScanRecord() == null) ? new byte[]{} : result.getScanRecord().getBytes();
                    BleDevice bleDevice = BleDeviceAccessor.newBleDevice(result.getDevice(), mAccessorKey);
                    mBleScanCallback.onScanning(bleDevice, result.getRssi(), scanBytes);
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

        List<ScanFilter> scanFilterList = new ArrayList<>();
        List<BleScanFilter> onlyFilterFuzzyNameScanFilterList = new ArrayList<>();
        if (mScanFilters != null) {
            for (BleScanFilter filter : mScanFilters) {
                String exactName = null, fuzzyName = null, address = null;
                UUID uuid = null;
                if (!TextUtils.isEmpty(filter.getDeviceName())) {
                    if (filter.isFuzzyDeviceName()) {
                        fuzzyName = filter.getDeviceName();
                    } else {
                        exactName = filter.getDeviceName();
                    }
                }
                if (!TextUtils.isEmpty(filter.getDeviceAddress())) {
                    address = filter.getDeviceAddress();
                }
                if (filter.getServiceUuid() != null) {
                    uuid = filter.getServiceUuid();
                }
                if (!TextUtils.isEmpty(exactName) || !TextUtils.isEmpty(address) || uuid != null) {
                    scanFilterList.add(new ScanFilter.Builder()
                            .setDeviceName(exactName)
                            .setDeviceAddress(address)
                            .setServiceUuid(uuid == null ? null : new ParcelUuid(uuid))
                            .build());
                }
                if (!TextUtils.isEmpty(fuzzyName) && TextUtils.isEmpty(address) && uuid == null) {
                    onlyFilterFuzzyNameScanFilterList.add(filter);
                }
            }
        }

        boolean isScreenOff = !Utils.isScreenOn(BleManager.getInstance().getContext());
        boolean isBackground = !Utils.isForeground(BleManager.getInstance().getContext());

        // BluetoothLeScanner#startScan() does not support fuzzy name filters, so we must
        // filter fuzzy names after receiving scan results, but these scan results have
        // already been filtered once by the filter in BluetoothLeScanner#startScan(),
        // to avoid this situation, once there is at least one BleScanFilter that only
        // filters fuzzy names and screen is on (if screen-off and no any valid filter,
        // scan may be suspended), we set an empty filter when calling
        // BluetoothLeScanner#startScan() and handle all filters after receiving scan results
        if (!onlyFilterFuzzyNameScanFilterList.isEmpty() && !isScreenOff) {
            scanFilterList.clear();
        }

        if (isScreenOff && scanFilterList.isEmpty()) {
            Logger.w("The screen is off, the current scan has no any valid filter, it" +
                    " may be suspended until the screen is turned on again");
        }

        if (scanFilterList.isEmpty()) {
            scanFilterList.add(new ScanFilter.Builder().build());
        }

        ScanSettings scanSettings = new ScanSettings.Builder()
                .setScanMode((isScreenOff || isBackground) ? ScanSettings.SCAN_MODE_LOW_POWER : ScanSettings.SCAN_MODE_LOW_LATENCY)
                .build();

        mBluetoothLeScanner.startScan(scanFilterList, scanSettings, mScanCallback);
        return true;
    }


    private boolean matchFilterList(String deviceName, String deviceAddress, List<ParcelUuid> serviceUuids) {
        if (mScanFilters == null || mScanFilters.isEmpty()) {
            return true;
        }
        for (BleScanFilter filter : mScanFilters) {
            boolean match = true;

            // Check device name
            if (!TextUtils.isEmpty(filter.getDeviceName())) {
                if (TextUtils.isEmpty(deviceName)) {
                    match = false;
                } else {
                    if (filter.isFuzzyDeviceName()) {
                        match = deviceName.contains(filter.getDeviceName());
                    } else {
                        match = deviceName.equals(filter.getDeviceName());
                    }
                }
            }

            // Check device address
            if (match && !TextUtils.isEmpty(filter.getDeviceAddress())) {
                match = filter.getDeviceAddress().equals(deviceAddress);
            }

            // Check service uuid
            if (match && filter.getServiceUuid() != null) {
                if (serviceUuids == null) {
                    match = false;
                } else {
                    boolean uuidMatch = false;
                    for (ParcelUuid pu : serviceUuids) {
                        if (pu != null && filter.getServiceUuid().equals(pu.getUuid())) {
                            uuidMatch = true;
                            break;
                        }
                    }
                    match = uuidMatch;
                }
            }

            if (match) {
                return true;
            }
        }

        return false;
    }

    private boolean sdkVersionLowerThan21() {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP;
    }

    public static final class AccessKey {
        private AccessKey() {

        }
    }
}
