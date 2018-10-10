package com.ficat.easyble;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;


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

import java.lang.reflect.Constructor;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class BleManager {
    private Context mContext;
    private BluetoothAdapter mBluetoothAdapter;
    private Options mOptions;
    private volatile BleScan<BleScanCallback> mScan;
    private volatile BleGatt mGatt;
    private static volatile BleManager instance;

    private final Object mLock1 = new Object();
    private final Object mLock2 = new Object();

    private BleManager(Context context, Options options) {
        if (context == null) {
            throw new IllegalArgumentException("Context is null");
        }
        if (context instanceof Activity) {
            Logger.w("Activity Leak Risk: " + context.getClass().getSimpleName());
        }
        if (options == null) {
            options = new Options();
        }
        this.mContext = context;
        this.mOptions = options;
        this.mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        setLoggable(options.loggable);
        registerBleReceiver();
    }

    private void setLoggable(boolean loggalbe) {
        Logger.LOGGABLE = loggalbe;
    }

    private void registerBleReceiver() {
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
        mContext.registerReceiver(BleReceiver.getInstance(), intentFilter);
    }

    public static BleManager getInstance(Context context, Options options) {
        if (instance == null) {
            synchronized (BleManager.class) {
                if (instance == null) {
                    instance = new BleManager(context, options);
                }
            }
        }
        return instance;
    }

    /**
     * Scan ble device
     */
    public void startScan(BleScanCallback callback) {
        checkBleScan();
        mScan.startScan(callback);
    }

    /**
     * Stop scaning device, it's strongly recommended that you call this method
     * to stop scaning after target device has been discovered
     */
    public void stopScan() {
        checkBleScan();
        mScan.stopScan();
    }

    /**
     * Connect to the remote device
     */
    public void connect(BleDevice device, BleConnectCallback callback) {
        checkBleGatt();
        mGatt.connect(device, callback);
    }

    /**
     * Connect to remote device by address
     */
    public void connect(String address, BleConnectCallback callback) {
        if (!BluetoothAdapter.checkBluetoothAddress(address)) {
            throw new IllegalArgumentException("invalid address: " + address);
        }
        BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(address);
        BleDevice bleDevice = newBleDevice(device);
        if (bleDevice == null) {
            Logger.d("new BleDevice fail!");
            return;
        }
        connect(bleDevice, callback);
    }

    /**
     * Disconnect from the remote device
     *
     * @param device remote device
     */
    public void disconnect(BleDevice device) {
        checkBleGatt();
        mGatt.disconnect(device);
    }

    /**
     * Disconnect from the remote device
     *
     * @param address remote device address
     * @throws IllegalArgumentException if the address is invalid
     */
    public void disconnect(String address) {
        if (!BluetoothAdapter.checkBluetoothAddress(address)) {
            throw new IllegalArgumentException("invalid address: " + address);
        }
        BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(address);
        BleDevice bleDevice = newBleDevice(device);
        if (bleDevice == null) {
            Logger.d("new BleDevice fail!");
            return;
        }
        disconnect(bleDevice);
    }

    /**
     * Disconnect all connected devices
     */
    public void disconnectAll() {
        checkBleGatt();
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
        checkBleGatt();
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
        checkBleGatt();
        mGatt.cancelNotify(device, serviceUuid, characteristicUuid);
    }

    /**
     * Write data to the remote device by specific writeable characteristic
     *
     * @param device      remote device
     * @param serviceUuid serivce uuid that the writeable characteristic belongs to
     * @param writeUuid   characteristic uuid which you write data, note that the
     *                    characteristic must be writeable, or it will call back onFail()
     * @param data        data
     * @param callback    result callback
     */
    public void write(BleDevice device, String serviceUuid, String writeUuid, byte[] data,
                      BleWriteCallback callback) {
        checkBleGatt();
        mGatt.write(device, serviceUuid, writeUuid, data, callback);
    }

    /**
     * Write by batch, you can use this method to split data and deliver it to remote
     * device by batch
     *
     * @param device           remote device
     * @param serviceUuid      serivce uuid that the writeable characteristic belongs to
     * @param writeUuid        characteristic uuid which you write data, note that the
     *                         characteristic must be writeable, or it will call back onFail()
     * @param data             data
     * @param lengthPerPackage data length per package
     * @param callback         result callback
     */
    public void writeByBatch(BleDevice device, String serviceUuid, String writeUuid, byte[] data,
                             int lengthPerPackage, BleWriteByBatchCallback callback) {
        checkBleGatt();
        mGatt.writeByBatch(device, serviceUuid, writeUuid, data, lengthPerPackage, callback);
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
        checkBleGatt();
        mGatt.read(device, serviceUuid, readUuid, callback);
    }

    /**
     * Read the remote device rssi(Received Signal Strength Indication)
     *
     * @param device   remote device
     * @param callback result callback
     */
    public void readRssi(BleDevice device, BleRssiCallback callback) {
        checkBleGatt();
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
        checkBleGatt();
        mGatt.setMtu(device, mtu, callback);
    }

    /**
     * Get service information which the remote device supports.
     * Note that this method will return null if this device is not connected
     *
     * @return service infomations,
     */
    public Map<ServiceInfo, List<CharacteristicInfo>> getDeviceServices(BleDevice device) {
        checkBleGatt();
        return mGatt.getDeviceServices(device);
    }

    /**
     * Get connected devices list
     *
     * @return connected devices list
     */
    public List<BleDevice> getConnectedDevices() {
        checkBleGatt();
        return mGatt.getConnectedDevices();
    }

    /**
     * Return true if the specific remote device has connected with local device
     *
     * @param address device mac
     * @return true if local device has connected to the specific remote device
     * @throws IllegalArgumentException if the address is invalid
     */
    public boolean isConnected(String address) {
        if (!BluetoothAdapter.checkBluetoothAddress(address)) {
            throw new IllegalArgumentException("invalid address: " + address);
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
     * Once you finish bluetooth, call this method to release some resources
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
        unregisterBleReciver();
    }

    private void unregisterBleReciver() {
        try {
            mContext.unregisterReceiver(BleReceiver.getInstance());
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
     * @param activity    activity, note that to get the result wether users have granted
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
     * @param enable eanble or disable local bluetooth
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

    private void checkBleScan() {
        if (mScan == null) {
            synchronized (mLock1) {
                if (mScan == null) {
                    mScan = new BleScanner.Builder()
                            .setScanPeriod(mOptions.scanPeriod)
                            .setDeviceName(mOptions.scanDeviceName)
                            .setDeviceAddress(mOptions.scanDeviceAddress)
                            .setServiceUuids(mOptions.scanServiceUuids)
                            .build();
                }
            }
        }
    }

    private void checkBleGatt() {
        if (mGatt == null) {
            synchronized (mLock2) {
                if (mGatt == null) {
                    mGatt = new BleGattImpl.Builder(mContext)
                            .setConnectTimeout(mOptions.connectTimeout)
                            .build();
                }
            }
        }
    }

    private BleDevice newBleDevice(BluetoothDevice device) {
        Class<?> clasz = BleDevice.class;
        try {
            Constructor<?> constructor = clasz.getDeclaredConstructor(BluetoothDevice.class);
            constructor.setAccessible(true);
            BleDevice bleDevice = (BleDevice) constructor.newInstance(device);
            return bleDevice;
        } catch (Exception e) {
            Logger.i("Encounter an exception while creating a BleDevice object by reflection: " + e.getMessage());
            return null;
        }
    }

    public static final class Options {
        public int scanPeriod;
        public String scanDeviceName;
        public String scanDeviceAddress;
        public UUID[] scanServiceUuids;
        public int connectTimeout;
        public boolean loggable;
    }
}
