package com.ficat.easyble;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattService;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.location.LocationManager;
import android.os.Build;
import android.text.TextUtils;

import com.ficat.easyble.gatt.BleGatt;
import com.ficat.easyble.gatt.BleGattAccessor;
import com.ficat.easyble.gatt.callback.BleConnectCallback;
import com.ficat.easyble.gatt.callback.BleMtuCallback;
import com.ficat.easyble.gatt.callback.BleNotifyCallback;
import com.ficat.easyble.gatt.callback.BleReadCallback;
import com.ficat.easyble.gatt.callback.BleRssiCallback;
import com.ficat.easyble.gatt.callback.BleWriteByBatchCallback;
import com.ficat.easyble.gatt.callback.BleWriteCallback;
import com.ficat.easyble.scan.BleScan;
import com.ficat.easyble.scan.BleScanAccessor;
import com.ficat.easyble.scan.BleScanCallback;
import com.ficat.easyble.utils.Logger;
import com.ficat.easyble.utils.PermissionChecker;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class BleManager {
    private Context mContext;
    private BluetoothAdapter mBluetoothAdapter;
    private ScanOptions mScanOptions;
    private ConnectionOptions mConnectionOptions;
    private BleScan<BleScanCallback> mScan;
    private BleGatt mGatt;
    private BleReceiver mReceiver;

    private static volatile BleManager instance;


    private BleManager() {

    }

    public BleManager init(Context context) {
        if (context == null) {
            throw new IllegalArgumentException("Context is null");
        }
        if (mContext != null) {
            Logger.d("You have called init() already!");
            return this;
        }
        if (context instanceof Activity) {
            Logger.w("Activity Leak Risk: " + context.getClass().getName());
        }
        mContext = context;
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        mReceiver = new BleReceiver();
        mScan = BleScanAccessor.newBleScan(mReceiver, new AccessKey());
        mGatt = BleGattAccessor.newBleGatt(new AccessKey());
        registerBleReceiver(mContext, mReceiver);
        return this;
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

    public BleManager setConnectionOptions(ConnectionOptions options) {
        mConnectionOptions = options;
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
        if (options == null) {
            options = ScanOptions.newInstance();
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && !isGpsOn()) {
            Logger.i("You'd better turn on GPS to avoid that scan doesn't work");
        }
        mScan.startScan(options.scanPeriod, options.scanDeviceName, options.scanDeviceAddress, options.scanServiceUuids, callback);
    }

    /**
     * Stop scanning, it's strongly recommended that you call this method
     * to stop scanning after target device has been discovered
     */
    public void stopScan() {
        mScan.stopScan();
    }

    /**
     * Connect to the remote device
     */
    public void connect(BleDevice device, BleConnectCallback callback) {
        connect(device, mConnectionOptions, callback);
    }

    /**
     * Connect to the remote device
     *
     * @param device   remote device
     * @param options  connection options
     * @param callback connection callback
     */
    public void connect(BleDevice device, ConnectionOptions options, BleConnectCallback callback) {
        if (device == null) {
            throw new IllegalArgumentException("BleDevice is null");
        }
        if (callback == null) {
            throw new IllegalArgumentException("BleConnectCallback is null");
        }
        if (options == null) {
            options = ConnectionOptions.newInstance();
        }
        mGatt.connect(options.connectionPeriod, device, callback);
    }

    /**
     * Connect to remote device by address
     */
    public void connect(String address, BleConnectCallback callback) {
        connect(address, mConnectionOptions, callback);
    }

    public void connect(String address, ConnectionOptions options, BleConnectCallback callback) {
        checkBluetoothAddress(address);
        BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(address);
        BleDevice bleDevice = new BleDevice(device);
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
        disconnect(device.getAddress());
    }

    /**
     * Disconnect from the remote device
     *
     * @param address remote device address
     */
    public void disconnect(String address) {
        checkBluetoothAddress(address);
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
     *                    will call back onFailureure()
     * @param callback    notification callback
     */
    public void notify(BleDevice device, UUID serviceUuid, UUID notifyUuid, BleNotifyCallback callback) {
        if (device == null) {
            throw new IllegalArgumentException("BleDevice is null");
        }
        if (callback == null) {
            throw new IllegalArgumentException("BleNotifyCallback is null");
        }
        mGatt.notify(device, serviceUuid, notifyUuid, callback);
    }

    /**
     * Cancel notification/indication
     *
     * @param device             remote device
     * @param serviceUuid        service uuid
     * @param characteristicUuid characteristic uuid you want to stop notifying or indicating
     */
    public void cancelNotify(BleDevice device, UUID serviceUuid, UUID characteristicUuid) {
        if (device == null) {
            throw new IllegalArgumentException("BleDevice is null");
        }
        mGatt.cancelNotify(device, serviceUuid, characteristicUuid);
    }

    /**
     * Write data to the remote device by specific writable characteristic
     *
     * @param device      remote device
     * @param serviceUuid service uuid that the writable characteristic belongs to
     * @param writeUuid   characteristic uuid which you write data, note that the
     *                    characteristic must be writable, or it will call back onFailure()
     * @param data        data
     * @param callback    result callback
     */
    public void write(BleDevice device, UUID serviceUuid, UUID writeUuid, byte[] data,
                      BleWriteCallback callback) {
        if (device == null) {
            throw new IllegalArgumentException("BleDevice is null");
        }
        if (callback == null) {
            throw new IllegalArgumentException("BleWriteCallback is null");
        }
        if (data == null || data.length <= 0) {
            throw new IllegalArgumentException("Data is null");
        }
        mGatt.write(device, serviceUuid, writeUuid, data, callback);
    }

    public void writeByBatch(BleDevice device, UUID serviceUuid, UUID writeUuid, byte[] data,
                             int lengthPerPackage, BleWriteByBatchCallback callback) {
        writeByBatch(device, serviceUuid, writeUuid, data, lengthPerPackage, 0L, callback);
    }

    /**
     * Write by batch, you can call this method to split data and deliver it to remote
     * device by batch
     *
     * @param device           remote device
     * @param serviceUuid      service uuid that the writable characteristic belongs to
     * @param writeUuid        characteristic uuid which you write data, note that the
     *                         characteristic must be writable, or it will call back onFailure()
     * @param data             data
     * @param lengthPerPackage data length per package
     * @param writeDelay       the interval of packages
     * @param callback         result callback
     */
    public void writeByBatch(BleDevice device, UUID serviceUuid, UUID writeUuid, byte[] data,
                             int lengthPerPackage, long writeDelay, BleWriteByBatchCallback callback) {
        if (device == null) {
            throw new IllegalArgumentException("BleDevice is null");
        }
        if (callback == null) {
            throw new IllegalArgumentException("BleWriteByBatchCallback is null");
        }
        if (data == null || data.length <= 0) {
            throw new IllegalArgumentException("Data is null");
        }
        mGatt.writeByBatch(device, serviceUuid, writeUuid, data, lengthPerPackage, writeDelay, callback);
    }

    /**
     * Read data from specific readable characteristic
     *
     * @param device      remote device
     * @param serviceUuid service uuid that the readable characteristic belongs to
     * @param readUuid    characteristic uuid you wanna read, note that the characteristic
     *                    must be readable, or it will call back onFailure()
     * @param callback    the read callback
     */
    public void read(BleDevice device, UUID serviceUuid, UUID readUuid, BleReadCallback callback) {
        if (device == null) {
            throw new IllegalArgumentException("BleDevice is null");
        }
        if (callback == null) {
            throw new IllegalArgumentException("BleReadCallback is null");
        }
        mGatt.read(device, serviceUuid, readUuid, callback);
    }

    /**
     * Read the remote device rssi(Received Signal Strength Indication)
     *
     * @param device   remote device
     * @param callback result callback
     */
    public void readRssi(BleDevice device, BleRssiCallback callback) {
        if (device == null) {
            throw new IllegalArgumentException("BleDevice is null");
        }
        if (callback == null) {
            throw new IllegalArgumentException("BleRssiCallback is null");
        }
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
        if (device == null) {
            throw new IllegalArgumentException("BleDevice is null");
        }
        if (callback == null) {
            throw new IllegalArgumentException("BleMtuCallback is null");
        }
        mGatt.setMtu(device, mtu, callback);
    }

    /**
     * Get service information which the remote device supports.
     * Note that this method will return null if this device is not connected
     *
     * @return service information
     */
    public List<BluetoothGattService> getDeviceServices(String address) {
        checkBluetoothAddress(address);
        BluetoothGatt bluetoothGatt = mGatt.getBluetoothGatt(address);
        if (bluetoothGatt != null) {
            return bluetoothGatt.getServices();
        }
        return null;
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
     * Return true if the remote device has connected with local device
     *
     * @param address mac address
     * @return true if local device has connected to the specific remote device
     */
    public boolean isConnected(String address) {
        checkBluetoothAddress(address);
        return mGatt.isConnected(address);
    }

    /**
     * Return true if local device is connecting with the remote device
     */
    public boolean isConnecting(String address) {
        checkBluetoothAddress(address);
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
        if (mReceiver != null) {
            mReceiver.clearAllListener();
            unregisterBleReceiver(mContext, mReceiver);
        }
        mScanOptions = null;
        mConnectionOptions = null;
        mReceiver = null;
        mContext = null;
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
     * to grant or reject,so you can get the result from Activity#onActivityResult().
     * <p>
     * Note that if on Android12(api31) or higher devices, only the permission
     * {@link Manifest.permission#BLUETOOTH_CONNECT} has been granted by user,
     * calling this method can work, or it will return false directly.
     *
     * @param activity    activity, you can receive request result in onActivityResult() of
     *                    this activity
     * @param requestCode enable bluetooth request code
     * @return true if the request to turn on bluetooth has started successfully, otherwise false
     */
    public static boolean enableBluetooth(Activity activity, int requestCode) {
        if (activity == null) {
            throw new IllegalArgumentException("Activity is null");
        }
        if (requestCode < 0) {
            throw new IllegalArgumentException("Request code cannot be negative");
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !PermissionChecker.isPermissionGranted(activity,
                Manifest.permission.BLUETOOTH_CONNECT)) {
            Logger.i("Android12 or higher, BleManager#enableBluetooth(Activity,int) needs the " +
                    "permission 'android.permission.BLUETOOTH_CONNECT'");
            return false;
        }
        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        if (adapter != null) {
            Intent intent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            activity.startActivityForResult(intent, requestCode);
            return true;
        }
        return false;
    }

    /**
     * Turn on or off local bluetooth directly without showing users a request
     * dialog. Like {@link #enableBluetooth(Activity, int)}, if current version
     * is Android12(api31) or higher, before calling this method, make sure the
     * permission {@link Manifest.permission#BLUETOOTH_CONNECT} has been
     * granted by user.
     * <p>
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
        try {
            if (enable == adapter.isEnabled()) {
                return;
            }
            // Now, especially on high version android device, the result of enable()
            // or disable() is unreliable. On some devices even if bluetooth state
            // has changed or the request dialog used to turn on/off bluetooth has
            // showed, it still return false.
            boolean result = enable ? adapter.enable() : adapter.disable();
        } catch (SecurityException e) {
            Logger.e("Android12 or higher, BleManager#toggleBluetooth(boolean) needs the" +
                    " permission 'android.permission.BLUETOOTH_CONNECT'");
        }
    }

    /**
     * Return true if local bluetooth is enabled at present
     *
     * @return true if local bluetooth is turned on
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
     * Get all permissions BLE required
     *
     * @return all BLE permissions
     */
    public static List<String> getBleRequiredPermissions() {
        List<String> list = new ArrayList<>();
        //BLE required permissions
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) { //Android12
            //BLUETOOTH_SCAN: enable this central device to scan peripheral devices
            //BLUETOOTH_CONNECT: used to get peripheral device name (BluetoothDevice#getName())
            list.add(Manifest.permission.BLUETOOTH_SCAN);
            list.add(Manifest.permission.BLUETOOTH_CONNECT);
            list.add(Manifest.permission.BLUETOOTH_ADVERTISE);
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {//Android10
            list.add(Manifest.permission.ACCESS_FINE_LOCATION);
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {//Android6
            list.add(Manifest.permission.ACCESS_COARSE_LOCATION);
        }
        return list;
    }

    /**
     * Check if all BLE permissions have been granted.
     */
    public static boolean allBlePermissionsGranted(Context context) {
        if (context == null) {
            throw new IllegalArgumentException("Context is null");
        }
        return scanPermissionGranted(context) && connectionPermissionGranted(context);
    }

    /**
     * Check if scan-permission has been granted
     */
    public static boolean scanPermissionGranted(Context context) {
        if (context == null) {
            throw new IllegalArgumentException("Context is null");
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) { //Android12
            //BLUETOOTH_SCAN: enable this central device to scan peripheral devices
            //BLUETOOTH_CONNECT: used to get peripheral device name (BluetoothDevice#getName())
            return PermissionChecker.isPermissionGranted(context, Manifest.permission.BLUETOOTH_SCAN) &&
                    PermissionChecker.isPermissionGranted(context, Manifest.permission.BLUETOOTH_CONNECT);
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {//Android10
            return PermissionChecker.isPermissionGranted(context, Manifest.permission.ACCESS_FINE_LOCATION);
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {//Android6
            return PermissionChecker.isPermissionGranted(context, Manifest.permission.ACCESS_COARSE_LOCATION) ||
                    PermissionChecker.isPermissionGranted(context, Manifest.permission.ACCESS_FINE_LOCATION);
        } else {
            return true;
        }
    }

    /**
     * Check if connection-permission has been granted
     */
    public static boolean connectionPermissionGranted(Context context) {
        if (context == null) {
            throw new IllegalArgumentException("Context is null");
        }
        //Android12(api31) or higher, BLUETOOTH_CONNECT permission is necessary
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.S || PermissionChecker.isPermissionGranted(context, Manifest.permission.BLUETOOTH_CONNECT);
    }

    /**
     * Get valid MTU range array, the first is min value, and the second is max value.
     *
     * @return MTU range array
     */
    public static int[] getValidMtuRange() {
        return new int[]{BleGatt.MTU_MIN, BleGatt.MTU_MAX};
    }

    public ScanOptions getScanOptions() {
        return mScanOptions == null ? ScanOptions.newInstance() : mScanOptions;
    }

    public ConnectionOptions getConnectionOptions() {
        return mConnectionOptions == null ? ConnectionOptions.newInstance() : mConnectionOptions;
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

    public Context getContext() {
        return mContext;
    }

    private void registerBleReceiver(Context context, BleReceiver receiver) {
        try {
            IntentFilter intentFilter = new IntentFilter();
            intentFilter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
            context.registerReceiver(receiver, intentFilter);
        } catch (Exception e) {
            Logger.e("Registering BleReceiver encounters an exception: " + e.getMessage());
        }
    }

    private void unregisterBleReceiver(Context context, BleReceiver receiver) {
        try {
            context.unregisterReceiver(receiver);
        } catch (Exception e) {
            Logger.e("Unregistering BleReceiver encounters an exception: " + e.getMessage());
        }
    }

    private void checkBluetoothAddress(String address) {
        if (!isAddressValid(address)) {
            throw new IllegalArgumentException("Invalid address: " + address);
        }
    }

    private boolean isGpsOn() {
        if (mContext == null) {
            throw new IllegalStateException("BleManager not initialized");
        }
        LocationManager locationManager
                = (LocationManager) mContext.getSystemService(Context.LOCATION_SERVICE);
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
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

    public static final class ConnectionOptions {
        private int connectionPeriod = 10000;

        private ConnectionOptions() {

        }

        public static ConnectionOptions newInstance() {
            return new ConnectionOptions();
        }

        public ConnectionOptions connectionPeriod(int timeout) {
            if (timeout > 0) {
                connectionPeriod = timeout;
            }
            return this;
        }
    }

    public static final class AccessKey {
        private AccessKey() {

        }
    }
}
