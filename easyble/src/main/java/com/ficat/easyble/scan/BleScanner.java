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
import com.ficat.easyble.BleReceiver;
import com.ficat.easyble.Logger;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class BleScanner implements BleScan<BleScanCallback>, BleReceiver.BluetoothStateChangedListener {
    private static final String TAG = "BleScanner";

    protected BluetoothAdapter mBluetoothAdapter;
    private BluetoothAdapter.LeScanCallback mLeScanCallback;//sdk<21 uses this scan callback
    private ScanCallback mScanCallback;//SDK>=21 uses this scan callback
    private BleScanCallback mBleScanCallback;//all sdk version uses this scan callback
    private BluetoothLeScanner mBluetoothLeScanner;
    private ScanSettings mScanSettings;
    private List<ScanFilter> mScanFilters;
    private int mScanPeriod;
    private String mDeviceName;
    private String mDeviceAddress;
    private UUID[] mServiceUuids;
    private List<ParcelUuid> mServiceUuidList;
    private volatile boolean mScanning;
    private Handler mHandler;

    private BleScanner(String deviceName, String address, UUID[] serviceUuids, int scanPeriod) {
        mDeviceName = deviceName;
        mDeviceAddress = address;
        mServiceUuids = serviceUuids;
        mScanPeriod = scanPeriod;
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        mHandler = new Handler(Looper.getMainLooper());
        //register BluetoothStateChangedListener
        BleReceiver.getInstance().registerBluetoothStateChangedListener(this);
    }

    @Override
    public synchronized void startScan(final BleScanCallback callback) {
        if (callback == null) {
            throw new IllegalArgumentException("BleScanCallback is null");
        }
        mBleScanCallback = callback;
        if (mScanning) {
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    if (mBleScanCallback != null) {
                        mBleScanCallback.onStart(false, "you can't start a new scan until the previous scan is over");
                    }
                }
            });
            return;
        }
        boolean scanStart;
        if (sdkVersionLowerThan21()) {
            scanStart = scanByOldApi();
        } else {
            scanStart = scanByNewApi();
        }
        if (scanStart) {
            mScanning = true;
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    if (mBleScanCallback != null) {
                        mBleScanCallback.onStart(true, "scan begin success");
                    }
                }
            });
            mHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    stopScan();
                }
            }, mScanPeriod);
        } else {
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    if (mBleScanCallback != null) {
                        mBleScanCallback.onStart(false, "scan begin fail,bluetooth is closed or other reason");
                    }
                }
            });
        }
    }

    @SuppressWarnings("NewApi")
    @Override
    public synchronized void stopScan() {
        if (mBluetoothAdapter == null || !mScanning) {
            return;
        }
        if (sdkVersionLowerThan21()) {
            if (mLeScanCallback != null) {
                mBluetoothAdapter.stopLeScan(mLeScanCallback);
            }
        } else {
            //if bluetooth is close, stopScan() will throw an exception
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
                }
            }
        });
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
        //remove scan period delayed message
        mHandler.removeCallbacksAndMessages(null);
        //unregister BluetoothStateChangedListener
        BleReceiver.getInstance().unregisterBluetoothStateChangedListener(this);
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
                                mBleScanCallback.onLeScan(newBleDevice(device), rssi, scanRecord);
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
        //mBluetoothLeScanner will be null and startScan() will throw an exception if bluetooth isn't open
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
                            if (!hasResultByFilterUuids(result)) return;
                            if (mBleScanCallback == null) return;
                            mBleScanCallback.onLeScan(newBleDevice(result.getDevice()), result.getRssi(), result.getScanRecord().getBytes());
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
        //note that getBluetoothLeScanner() will be null if bluetooth is closed
        if (mBluetoothLeScanner == null) {
            mBluetoothLeScanner = mBluetoothAdapter.getBluetoothLeScanner();
        }
        if (mScanSettings == null) {
            mScanSettings = new ScanSettings.Builder()
                    .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                    .build();
        }
        if (mScanFilters == null) {
            mScanFilters = new ArrayList<>();
            ScanFilter filter = new ScanFilter.Builder()
                    .setDeviceName(mDeviceName)
                    .setDeviceAddress(mDeviceAddress)
                    .build();
            mScanFilters.add(filter);
        }
        mBluetoothLeScanner.startScan(mScanFilters, mScanSettings, mScanCallback);
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
        if (mServiceUuidList == null) {
            mServiceUuidList = new ArrayList<>();
            for (UUID uuid : mServiceUuids) {
                mServiceUuidList.add(new ParcelUuid(uuid));
            }
        }
        List<ParcelUuid> scanServiceUuids = result.getScanRecord().getServiceUuids();
        return scanServiceUuids != null && scanServiceUuids.containsAll(mServiceUuidList);
    }

    private boolean sdkVersionLowerThan21() {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP;
    }

    private BleDevice newBleDevice(BluetoothDevice device) {
        Class<?> clasz = BleDevice.class;
        try {
            Constructor<?> constructor = clasz.getDeclaredConstructor(BluetoothDevice.class);
            constructor.setAccessible(true);
            BleDevice bleDevice = (BleDevice) constructor.newInstance(device);
            return bleDevice;
        } catch (Exception e) {
            Logger.i("encounter an exception while creating a BleDevice object by reflection: " + e.getMessage());
            return null;
        }
    }

    public static final class Builder {
        private int mScanPeriod = 12000;//defalut scan period is 12s
        private String mDeviceName;
        private String mDeviceAddress;
        private UUID[] mServiceUuids;

        public Builder setScanPeriod(int scanPeriod) {
            if (scanPeriod > 0) {
                mScanPeriod = scanPeriod;
            }
            return this;
        }

        public Builder setDeviceName(String deviceName) {
            mDeviceName = deviceName;
            return this;
        }

        public Builder setDeviceAddress(String address) {
            mDeviceAddress = address;
            return this;
        }

        public Builder setServiceUuids(UUID[] serviceUuids) {
            mServiceUuids = serviceUuids;
            return this;
        }

        public BleScanner build() {
            return new BleScanner(mDeviceName, mDeviceAddress, mServiceUuids, mScanPeriod);
        }
    }
}
