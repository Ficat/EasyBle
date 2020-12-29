package com.ficat.easyble;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.location.LocationManager;
import android.os.Build;
import android.text.TextUtils;

import com.ficat.easyble.gatt.BleGatt;
import com.ficat.easyble.gatt.BleGattImpl;
import com.ficat.easyble.gatt.bean.CharacteristicInfo;
import com.ficat.easyble.gatt.bean.ServiceInfo;
import com.ficat.easyble.gatt.callback.BleConnectCallback;
import com.ficat.easyble.gatt.callback.BleMtuCallback;
import com.ficat.easyble.gatt.callback.BleNotifyCallback;
import com.ficat.easyble.gatt.callback.BleReadCallback;
import com.ficat.easyble.gatt.callback.BleRssiCallback;
import com.ficat.easyble.gatt.callback.BleWriteByBatchCallback;
import com.ficat.easyble.gatt.callback.BleWriteCallback;
import com.ficat.easyble.scan.BleScan;
import com.ficat.easyble.scan.BleScanCallback;
import com.ficat.easyble.scan.BleScanner;
import com.ficat.easyble.utils.PermissionChecker;

import java.lang.reflect.Constructor;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class BleManager {
    private Context mContext;
    private BluetoothAdapter mBluetoothAdapter;
    private ScanOptions mScanOptions;
    private ConnectOptions mConnectOptions;
    private BleScan<BleScanCallback> mScan;
    private BleGatt mGatt;
    private BleReceiver mReceiver;

    private static volatile BleManager instance;


    private BleManager() {

    }

    public BleManager init(Context context) {
        if (mContext != null) {
            Logger.d("you have called init() already");
            return this;
        }
        if (context == null) {
            throw new IllegalArgumentException("context is null");
        }
        if (context instanceof Activity) {
            Logger.w("Activity Leak Risk:" + context.getClass().getSimpleName());
        }
        mContext = context;
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        mReceiver = new BleReceiver();
        mScan = new BleScanner(mReceiver);
        mGatt = new BleGattImpl(mContext);
        registerBleReceiver();
        return this;
    }

    private void registerBleReceiver() {
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
        mContext.registerReceiver(mReceiver, intentFilter);
    }

    public static BleManager getInstance() {
        if (instance == null) {
            synchronized (BleManager.class) {
                if (instance == null) {
                    instance = new BleManager();
                }
            }
        }
        return instance;
    }

    public BleManager setScanOptions(ScanOptions options) {
        mScanOptions = options;
        return this;
    }

    public BleManager setConnectionOptions(ConnectOptions options) {
        mConnectOptions = options;
        return this;
    }

    public BleManager setLog(boolean enable, String tag) {
        Logger.SHOW_LOG = enable;
        if (!TextUtils.isEmpty(tag)) {
            Logger.TAG = tag;
        }
        return this;
    }

    public boolean isScanning() {
        return mScan.isScanning();
    }

    /**
     * Scan ble device
     */
    public void startScan(BleScanCallback callback) {
        startScan(mScanOptions, callback);
    }

    public void startScan(ScanOptions options, BleScanCallback callback) {
        if (callback == null) {
            throw new IllegalArgumentException("BleScanCallback is null");
        }
        if (!isScanPermissionGranted(mContext)) {
            String permission = Build.VERSION.SDK_INT > Build.VERSION_CODES.P ? "location(ACCESS_FINE_LOCATION)" :
                    "location(ACCESS_COARSE_LOCATION or ACCESS_FINE_LOCATION)";
            callback.onStart(false, "You must grant " + permission + " permission before scanning");
            return;
        }
        if (options == null) {
            options = ScanOptions.newInstance();
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && !isGpsOn()) {
            Logger.i("For this device,scanning may need GPS,you'd better turn on GPS to avoid that scanning doesn't work");
        }
        mScan.startScan(options.scanPeriod, options.scanDeviceName, options.scanDeviceAddress,
                options.scanServiceUuids, callback);
    }

    /**
     * Stop scanning device, it's strongly recommended that you call this method
     * to stop scanning after target device has been discovered
     */
    public void stopScan() {
        mScan.stopScan();
    }

    /**
     * Connect to the remote device
     */
    public void connect(BleDevice device, BleConnectCallback callback) {
        connect(device, mConnectOptions, callback);
    }

    public void connect(BleDevice device, ConnectOptions options, BleConnectCallback callback) {
        if (options == null) {
            options = ConnectOptions.newInstance();
        }
        mGatt.connect(options.connectTimeout, device, callback);
    }

    /**
     * Connect to remote device by address
     */
    public void connect(String address, BleConnectCallback callback) {
        connect(address, mConnectOptions, callback);
    }

    public void connect(String address, ConnectOptions options, BleConnectCallback callback) {
        checkBluetoothAddress(address);
        BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(address);
        BleDevice bleDevice = newBleDevice(device);
        if (bleDevice == null) {
            Logger.d("new BleDevice fail!");
            return;
        }
        connect(bleDevice, options, callback);
    }

    /**
     * Disconnect from the remote device
     *
     * @param device remote device
     */
    public void disconnect(BleDevice device) {
        if (device == null) {
            throw new IllegalArgumentException("BleDevice is null");
        }
        disconnect(device.address);
    }

    /**
     * Disconnect from the remote device
     *
     * @param address remote device address
     */
    public void disconnect(String address) {
        if (!isAddressValid(address)) {
            Logger.d("disconnect fail because of invalid address:" + address);
            return;
        }
        mGatt.disconnect(address);
    }

    /**
     * Disconnect all connected devices
     */
    public void disconnectAll() {
        mGatt.disconnectAll();
    }

    /**
     * Listen remote device notification/indication by specific notification/indication
     * characteristic
     *
     * @param device      remote device
     * @param serviceUuid service uuid which the notification or indication uuid belongs to
     * @param notifyUuid  characteristic uuid that you wanna notify or indicate, note that
     *                    the characteristic must support notification or indication, or it
     *                    will call back onFail()
     * @param callback    notification callback
     */
    public void notify(BleDevice device, String serviceUuid, String notifyUuid, BleNotifyCallback callback) {
        mGatt.notify(device, serviceUuid, notifyUuid, callback);
    }

    /**
     * Cancel notification/indication
     *
     * @param device             remote device
     * @param serviceUuid        service uuid
     * @param characteristicUuid characteristic uuid you want to stop notifying or indicating
     */
    public void cancelNotify(BleDevice device, String serviceUuid, String characteristicUuid) {
        mGatt.cancelNotify(device, serviceUuid, characteristicUuid);
    }

    /**
     * Write data to the remote device by specific writable characteristic
     *
     * @param device      remote device
     * @param serviceUuid service uuid that the writable characteristic belongs to
     * @param writeUuid   characteristic uuid which you write data, note that the
     *                    characteristic must be writable, or it will call back onFail()
     * @param data        data
     * @param callback    result callback
     */
    public void write(BleDevice device, String serviceUuid, String writeUuid, byte[] data,
                      BleWriteCallback callback) {
        mGatt.write(device, serviceUuid, writeUuid, data, callback);
    }

    /**
     * Write by batch, you can use this method to split data and deliver it to remote
     * device by batch
     *
     * @param device           remote device
     * @param serviceUuid      service uuid that the writable characteristic belongs to
     * @param writeUuid        characteristic uuid which you write data, note that the
     *                         characteristic must be writable, or it will call back onFail()
     * @param data             data
     * @param lengthPerPackage data length per package
     * @param callback         result callback
     */
    public void writeByBatch(BleDevice device, String serviceUuid, String writeUuid, byte[] data,
                             int lengthPerPackage, BleWriteByBatchCallback callback) {
        mGatt.writeByBatch(device, serviceUuid, writeUuid, data, lengthPerPackage, 0L, callback);
    }

    /**
     * Write by batch, you can use this method to split data and deliver it to remote
     * device by batch
     *
     * @param device           remote device
     * @param serviceUuid      service uuid that the writable characteristic belongs to
     * @param writeUuid        characteristic uuid which you write data, note that the
     *                         characteristic must be writable, or it will call back onFail()
     * @param data             data
     * @param lengthPerPackage data length per package
     * @param writeDelay       the interval of packages
     * @param callback         result callback
     */
    public void writeByBatch(BleDevice device, String serviceUuid, String writeUuid, byte[] data,
                             int lengthPerPackage, long writeDelay, BleWriteByBatchCallback callback) {
        mGatt.writeByBatch(device, serviceUuid, writeUuid, data, lengthPerPackage, writeDelay, callback);
    }

    /**
     * Read data from specific readable characteristic
     *
     * @param device      remote device
     * @param serviceUuid service uuid that the readable characteristic belongs to
     * @param readUuid    characteristic uuid you wanna read, note that the characteristic
     *                    must be readable, or it will call back onFail()
     * @param callback    the read callback
     */
    public void read(BleDevice device, String serviceUuid, String readUuid, BleReadCallback callback) {
        mGatt.read(device, serviceUuid, readUuid, callback);
    }

    /**
     * Read the remote device rssi(Received Signal Strength Indication)
     *
     * @param device   remote device
     * @param callback result callback
     */
    public void readRssi(BleDevice device, BleRssiCallback callback) {
        mGatt.readRssi(device, callback);
    }

    /**
     * Set MTU (Maximum Transmission Unit)
     *
     * @param device   remote device
     * @param mtu      MTU value, rang from 23 to 512
     * @param callback result callback
     */
    public void setMtu(BleDevice device, int mtu, BleMtuCallback callback) {
        mGatt.setMtu(device, mtu, callback);
    }

    /**
     * Get service information which the remote device supports.
     * Note that this method will return null if this device is not connected
     *
     * @return service information
     */
    public Map<ServiceInfo, List<CharacteristicInfo>> getDeviceServices(BleDevice device) {
        if (device == null) {
            return null;
        }
        return getDeviceServices(device.address);
    }

    /**
     * Get service information which the remote device supports.
     * Note that this method will return null if this device is not connected
     *
     * @return service information
     */
    public Map<ServiceInfo, List<CharacteristicInfo>> getDeviceServices(String address) {
        if (!isAddressValid(address)) {
            return null;
        }
        return mGatt.getDeviceServices(address);
    }

    /**
     * Get connected devices list
     *
     * @return connected devices list
     */
    public List<BleDevice> getConnectedDevices() {
        return mGatt.getConnectedDevices();
    }

    /**
     * Return true if the specific remote device has connected with local device
     *
     * @param address device mac
     * @return true if local device has connected to the specific remote device
     */
    public boolean isConnected(String address) {
        if (!BluetoothAdapter.checkBluetoothAddress(address)) {
            return false;
        }
        List<BleDevice> deviceList = getConnectedDevices();
        for (BleDevice d : deviceList) {
            if (address.equals(d.address)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Return true if local device is connecting with the specific remote device
     */
    public boolean isConnecting(String address) {
        if (!BluetoothAdapter.checkBluetoothAddress(address)) {
            return false;
        }
        return mGatt.isConnecting(address);
    }

    /**
     * Once you finish bluetooth, call this method to release some resources.
     * <p>
     * Note that if you want to use BleManager again, you must call init()
     * again before that
     *
     * @see BleManager#init(Context)
     */
    public void destroy() {
        if (mGatt != null) {
            mGatt.destroy();
            mGatt = null;
        }
        if (mScan != null) {
            mScan.destroy();
            mScan = null;
        }
        unregisterBleReceiver();
        mScanOptions = null;
        mConnectOptions = null;
        mReceiver = null;
        mContext = null;
    }

    private void unregisterBleReceiver() {
        try {
            if (mContext == null) return;
            mContext.unregisterReceiver(mReceiver);
        } catch (Exception e) {
            Logger.i("unregistering BleReceiver encounters an exception: " + e.getMessage());
        }
    }

    /**
     * Return true if this device supports ble
     */
    public static boolean supportBle(Context context) {
        if (context == null) {
            throw new IllegalArgumentException("Context is null");
        }
        return BluetoothAdapter.getDefaultAdapter() != null &&
                context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE);
    }

    /**
     * Turn on local bluetooth, calling the method will show users a request dialog
     * to grant or reject,so you can get the result from Activity#onActivityResult()
     *
     * @param activity    activity, note that to get the result whether users have granted
     *                    or rejected to enable bluetooth, you should handle the method
     *                    onActivityResult() of this activity
     * @param requestCode enable bluetooth request code
     */
    public static void enableBluetooth(Activity activity, int requestCode) {
        if (activity == null || requestCode < 0) {
            return;
        }
        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        if (adapter != null && !adapter.isEnabled()) {
            Intent intent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            activity.startActivityForResult(intent, requestCode);
        }
    }

    /**
     * Turn on or off local bluetooth directly without showing users a request
     * dialog.
     * Note that a request dialog may still show when you call this method, due to
     * some special Android devices' system may have been modified by manufacturers
     *
     * @param enable enable or disable local bluetooth
     */
    public static void toggleBluetooth(boolean enable) {
        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        if (adapter == null) {
            return;
        }
        if (enable) {
            adapter.enable();
        } else {
            if (adapter.isEnabled()) {
                adapter.disable();
            }
        }
    }

    /**
     * Return true if local bluetooth is enabled at present
     *
     * @return true if local bluetooth is open
     */
    public static boolean isBluetoothOn() {
        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        return adapter != null && adapter.isEnabled();
    }

    /**
     * Check if the address is valid
     *
     * @param address mac address
     * @return true if the address is valid
     */
    public static boolean isAddressValid(String address) {
        return BluetoothAdapter.checkBluetoothAddress(address);
    }

    /**
     * Check if scan-permission has been granted
     */
    public static boolean isScanPermissionGranted(Context context) {
        if (context == null) {
            throw new IllegalArgumentException("Context is null");
        }
        if (Build.VERSION.SDK_INT >= 29 && getTargetVersion(context) >= 29) {
            return PermissionChecker.isPermissionGranted(context, Manifest.permission.ACCESS_FINE_LOCATION);
        } else if (Build.VERSION.SDK_INT >= 23) {
            return PermissionChecker.isPermissionGranted(context, Manifest.permission.ACCESS_COARSE_LOCATION) ||
                    PermissionChecker.isPermissionGranted(context, Manifest.permission.ACCESS_FINE_LOCATION);
        } else {
            return true;
        }
    }

    public ScanOptions getScanOptions() {
        return mScanOptions == null ? ScanOptions.newInstance() : mScanOptions;
    }

    public ConnectOptions getConnectOptions() {
        return mConnectOptions == null ? ConnectOptions.newInstance() : mConnectOptions;
    }

    /**
     * Get the BluetoothGatt object of specific remote device
     *
     * @return the BluetoothGatt object, note that it will return null if connection between
     * the central device and the remote device has not started or established.
     */
    public BluetoothGatt getBluetoothGatt(String address) {
        checkBluetoothAddress(address);
        return mGatt.getBluetoothGatt(address);
    }

    private static int getTargetVersion(Context context) {
        int targetSdkVersion = -100;
        try {
            PackageInfo info = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
            targetSdkVersion = info.applicationInfo.targetSdkVersion;
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }
        return targetSdkVersion;
    }

    private void checkBluetoothAddress(String address) {
        if (!BluetoothAdapter.checkBluetoothAddress(address)) {
            throw new IllegalArgumentException("Invalid address: " + address);
        }
    }

    private boolean isGpsOn() {
        if (mContext == null) {
            return false;
        }
        LocationManager locationManager
                = (LocationManager) mContext.getSystemService(Context.LOCATION_SERVICE);
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
    }

    private BleDevice newBleDevice(BluetoothDevice device) {
        Class<?> clasz = BleDevice.class;
        try {
            Constructor<?> constructor = clasz.getDeclaredConstructor(BluetoothDevice.class);
            constructor.setAccessible(true);
            BleDevice d = (BleDevice) constructor.newInstance(device);
            return d;
        } catch (Exception e) {
            Logger.i("Encounter an exception while creating a BleDevice object by reflection: " + e.getMessage());
            return null;
        }
    }

    public static final class ScanOptions {
        private int scanPeriod = 12000;
        private String scanDeviceName;
        private String scanDeviceAddress;
        private UUID[] scanServiceUuids;

        private ScanOptions() {

        }

        public static ScanOptions newInstance() {
            return new ScanOptions();
        }

        public ScanOptions scanPeriod(int scanPeriod) {
            if (scanPeriod > 0) {
                this.scanPeriod = scanPeriod;
            }
            return this;
        }

        public ScanOptions scanDeviceName(String deviceName) {
            scanDeviceName = deviceName;
            return this;
        }

        public ScanOptions scanDeviceAddress(String deviceAddress) {
            scanDeviceAddress = deviceAddress;
            return this;
        }

        public ScanOptions scanServiceUuids(UUID[] serviceUuids) {
            scanServiceUuids = serviceUuids;
            return this;
        }


    }

    public static final class ConnectOptions {
        private int connectTimeout = 10000;

        private ConnectOptions() {

        }

        public static ConnectOptions newInstance() {
            return new ConnectOptions();
        }

        public ConnectOptions connectTimeout(int timeout) {
            if (timeout > 0) {
                connectTimeout = timeout;
            }
            return this;
        }
    }
}
