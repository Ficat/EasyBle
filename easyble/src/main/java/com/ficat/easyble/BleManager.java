package com.ficat.easyble;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Application;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Build;
import android.text.TextUtils;

import com.ficat.easyble.gatt.BleGatt;
import com.ficat.easyble.gatt.BleGattAccessor;
import com.ficat.easyble.gatt.callback.BleConnectCallback;
import com.ficat.easyble.gatt.callback.BleConnectionPriorityCallback;
import com.ficat.easyble.gatt.callback.BleDescriptorReadCallback;
import com.ficat.easyble.gatt.callback.BleDescriptorWriteCallback;
import com.ficat.easyble.gatt.callback.BleMtuCallback;
import com.ficat.easyble.gatt.callback.BleNotifyCallback;
import com.ficat.easyble.gatt.callback.BlePhyPreferenceCallback;
import com.ficat.easyble.gatt.callback.BlePhyReadCallback;
import com.ficat.easyble.gatt.callback.BleReadCallback;
import com.ficat.easyble.gatt.callback.BleRssiCallback;
import com.ficat.easyble.gatt.callback.BleWriteByBatchCallback;
import com.ficat.easyble.gatt.callback.BleWriteCallback;
import com.ficat.easyble.scan.BleScan;
import com.ficat.easyble.scan.BleScanAccessor;
import com.ficat.easyble.scan.BleScanCallback;
import com.ficat.easyble.scan.BleScanFilter;
import com.ficat.easyble.scan.BleScanRecord;
import com.ficat.easyble.utils.BluetoothGattUtils;
import com.ficat.easyble.utils.Logger;
import com.ficat.easyble.utils.Utils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public final class BleManager {
    private Context mContext;
    private ScanOptions mScanOptions;
    private ConnectionOptions mConnectionOptions;
    private long mGattOperationTimeoutMillis = BleGatt.DEFAULT_OPERATION_TIMEOUT_MILLIS;
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
        Context appContext = (context instanceof Application) ? context : context.getApplicationContext();
        mContext = appContext == null ? context : appContext;
        registerBleReceiver();
        mScan = BleScanAccessor.newBleScan(new AccessKey());
        mGatt = BleGattAccessor.newBleGatt(new AccessKey());
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

    public BleManager setGattOperationTimeout(long millis) {
        if (millis <= 0) {
            millis = BleGatt.DEFAULT_OPERATION_TIMEOUT_MILLIS;
        }
        mGattOperationTimeoutMillis = millis;
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
        if (Build.VERSION.SDK_INT >= 24 && !Utils.isGpsOn(mContext)) {
            Logger.i("You'd better turn on GPS to avoid that scan doesn't work");
        }
        mScan.startScan(options.getScanPeriod(), options.getScanFilters(), callback);
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
        mGatt.connect(options.mConnectionPeriod, options.mRetryCount, options.mRetryDelay,
                options.mAutoConnect, device, callback);
    }

    /**
     * Connect to remote device by address
     */
    public void connect(String address, BleConnectCallback callback) {
        connect(address, mConnectionOptions, callback);
    }

    public void connect(String address, ConnectionOptions options, BleConnectCallback callback) {
        checkBluetoothAddress(address);
        BluetoothDevice device = BluetoothAdapter.getDefaultAdapter().getRemoteDevice(address);
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
        disconnect(address, false);
    }

    /**
     * Disconnect from the target device. That means we will disconnect an established
     * connection, or cancels a connection attempt currently in progress.
     *
     * @param address              remote device address
     * @param closeGattImmediately whether to close {@link  android.bluetooth.BluetoothGatt}
     *                             immediately without waiting for the system disconnection
     *                             callback if the device is already connected.
     */
    public void disconnect(String address, boolean closeGattImmediately) {
        checkBluetoothAddress(address);
        mGatt.disconnect(address, closeGattImmediately);
    }

    /**
     * Disconnect all connected devices
     */
    public void disconnectAll() {
        mGatt.disconnectAll(false);
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
        if (serviceUuid == null || notifyUuid == null) {
            throw new IllegalArgumentException("UUID is null");
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
        if (serviceUuid == null) {
            throw new IllegalArgumentException("Service uuid is null");
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
        if (serviceUuid == null || writeUuid == null) {
            throw new IllegalArgumentException("UUID is null");
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
     * @param device         remote device
     * @param serviceUuid    service uuid that the writable characteristic belongs to
     * @param writeUuid      characteristic uuid which you write data, note that the
     *                       characteristic must be writable, or it will call back onFailure()
     * @param data           data
     * @param lengthPerBatch data length per batch
     * @param batchInterval  the interval of batches
     * @param callback       result callback
     */
    public void writeByBatch(BleDevice device, UUID serviceUuid, UUID writeUuid, byte[] data,
                             int lengthPerBatch, long batchInterval, BleWriteByBatchCallback callback) {
        if (device == null) {
            throw new IllegalArgumentException("BleDevice is null");
        }
        if (callback == null) {
            throw new IllegalArgumentException("BleWriteByBatchCallback is null");
        }
        if (serviceUuid == null || writeUuid == null) {
            throw new IllegalArgumentException("UUID is null");
        }
        if (data == null || data.length <= 0) {
            throw new IllegalArgumentException("Data is null");
        }
        mGatt.writeByBatch(device, serviceUuid, writeUuid, data, lengthPerBatch, batchInterval, callback);
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
        if (serviceUuid == null || readUuid == null) {
            throw new IllegalArgumentException("UUID is null");
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
     * @param mtu      MTU we requested successfully, rang from 23 to 515. Note that valid
     *                 maximum transmission is (MTU - 3). For example, if the mtu we requested
     *                 is 23, the max data length that we can write/read is 20 (23 - 3)
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
     * Write the value of a given descriptor to the associated remote device
     * If the operation succeed, the callback
     * {@link BleDescriptorWriteCallback#onDescriptorWriteSuccess(byte[], UUID, BleDevice)}
     * will be triggered
     *
     * @param device             the remote device
     * @param serviceUuid        service uuid
     * @param characteristicUuid characteristic uuid
     * @param descriptorUuid     target descriptor uuid
     * @param data               data
     * @param callback           callback
     */
    public void descriptorWrite(BleDevice device, UUID serviceUuid, UUID characteristicUuid,
                                UUID descriptorUuid, byte[] data, BleDescriptorWriteCallback callback) {
        if (device == null) {
            throw new IllegalArgumentException("BleDevice is null");
        }
        if (serviceUuid == null || characteristicUuid == null || descriptorUuid == null) {
            throw new IllegalArgumentException("UUID is null");
        }
        if (data == null || data.length <= 0) {
            throw new IllegalArgumentException("Data is null");
        }
        if (callback == null) {
            throw new IllegalArgumentException("BleDescriptorWriteCallback is null");
        }
        mGatt.descriptorWrite(device, serviceUuid, characteristicUuid, descriptorUuid, data, callback);
    }

    /**
     * Reads the value for a given descriptor from the associated remote device.
     * Once the read operation has been completed, the callback
     * {@link BleDescriptorReadCallback#onDescriptorReadSuccess(byte[], UUID, BleDevice)}
     * will be triggered.
     *
     * @param device             the remote device
     * @param serviceUuid        service uuid
     * @param characteristicUuid characteristic uuid
     * @param descriptorUuid     target descriptor uuid
     * @param callback           callback
     */
    public void descriptorRead(BleDevice device, UUID serviceUuid, UUID characteristicUuid,
                               UUID descriptorUuid, BleDescriptorReadCallback callback) {
        if (device == null) {
            throw new IllegalArgumentException("BleDevice is null");
        }
        if (serviceUuid == null || characteristicUuid == null || descriptorUuid == null) {
            throw new IllegalArgumentException("UUID is null");
        }
        if (callback == null) {
            throw new IllegalArgumentException("BleDescriptorReadCallback is null");
        }
        mGatt.descriptorRead(device, serviceUuid, characteristicUuid, descriptorUuid, callback);
    }

    /**
     * Read the current transmitter PHY and receiver PHY of the connection. The values are
     * returned in {@link BlePhyReadCallback#onPhyReadSuccess(int, int, BleDevice)}
     *
     * @param device   the remote device
     * @param callback callback
     */
    public void readPhy(BleDevice device, BlePhyReadCallback callback) {
        if (device == null) {
            throw new IllegalArgumentException("BleDevice is null");
        }
        if (callback == null) {
            throw new IllegalArgumentException("BlePhyReadCallback is null");
        }
        mGatt.readPhy(device, callback);
    }

    /**
     * Set the preferred connection PHY.
     * <p>
     * Note that this is just a recommendation, whether the PHY change will happen depends
     * on other applications preferences, local and remote controller capabilities.
     * {@link BlePhyPreferenceCallback#onPhyChanged(int, int, BleDevice)} will be triggered
     * as a result of this call, even if no PHY change happens. It is also triggered when
     * remote device updates the PHY.
     * </p>
     *
     * @param device     the remote device
     * @param txPhy      preferred transmitter PHY. Bitwise OR of any of
     *                   {@link BluetoothDevice#PHY_LE_1M_MASK},
     *                   {@link BluetoothDevice#PHY_LE_2M_MASK},
     *                   {@link BluetoothDevice#PHY_LE_CODED_MASK}.
     * @param rxPhy      preferred receiver PHY. Bitwise OR of any of
     *                   {@link BluetoothDevice#PHY_LE_1M_MASK},
     *                   {@link BluetoothDevice#PHY_LE_2M_MASK},
     *                   {@link BluetoothDevice#PHY_LE_CODED_MASK}.
     * @param phyOptions preferred coding to use when transmitting on the LE Coded PHY.
     *                   Use one of the following:
     *                   {@link BluetoothDevice#PHY_OPTION_NO_PREFERRED},
     *                   {@link BluetoothDevice#PHY_OPTION_S2},
     *                   {@link BluetoothDevice#PHY_OPTION_S8}.
     * @param callback   callback
     */
    public void setPreferencePhy(BleDevice device, int txPhy, int rxPhy, int phyOptions,
                                 BlePhyPreferenceCallback callback) {
        if (device == null) {
            throw new IllegalArgumentException("BleDevice is null");
        }
        if (callback == null) {
            throw new IllegalArgumentException("BlePhyPreferenceCallback is null");
        }
        mGatt.setPreferencePhy(device, txPhy, rxPhy, phyOptions, callback);
    }

    /**
     * Request a connection parameter update.
     * <p>
     * Note that {@link BleConnectionPriorityCallback#onConnectionPriorityRequestSuccess(int, BleDevice)}
     * just means the request succeeded, not connection params updated successfully.
     * </p>
     *
     * @param device       the remote device
     * @param connPriority request a specific connection priority. it must be one of
     *                     {@link BluetoothGatt#CONNECTION_PRIORITY_BALANCED}
     *                     {@link BluetoothGatt#CONNECTION_PRIORITY_HIGH}
     *                     {@link BluetoothGatt#CONNECTION_PRIORITY_LOW_POWER}
     *                     {@link BluetoothGatt#CONNECTION_PRIORITY_DCK}
     * @param callback     callback
     */
    public void requestConnectionPriority(BleDevice device, int connPriority,
                                          BleConnectionPriorityCallback callback) {
        if (device == null) {
            throw new IllegalArgumentException("BleDevice is null");
        }
        if (callback == null) {
            throw new IllegalArgumentException("BleConnectionPriorityCallback is null");
        }
        if (connPriority != BluetoothGatt.CONNECTION_PRIORITY_BALANCED &&
                connPriority != BluetoothGatt.CONNECTION_PRIORITY_HIGH &&
                connPriority != BluetoothGatt.CONNECTION_PRIORITY_LOW_POWER &&
                connPriority != 3) { // BluetoothGatt.CONNECTION_PRIORITY_DCK == 3
            throw new IllegalArgumentException(String.format(Locale.US,
                    "Invalid connection priority=%d, it must be from %d to %d",
                    connPriority, 0, 3));
        }
        mGatt.requestConnectionPriority(device, connPriority, callback);
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
     * Get connecting devices
     *
     * @return connecting devices
     */
    public List<BleDevice> getConnectingDevices() {
        return mGatt.getConnectingDevices();
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
     * Releases all resources held by BleManager.
     * <p>
     * Note that after calling this method, all states and params will be reset, include
     * the {@link #mContext}, it will be set to null, so if you want to reuse BleManger,
     * do not forget to call {@link #init(Context)} again.
     * </p>
     */
    public void destroy() {
        destroy(false, false);
    }

    /**
     * Releases all resources held by BleManager.
     * <p>
     * Note that after calling this method, all states and params will be reset, include
     * the {@link #mContext}, it will be set to null, so if you want to reuse BleManger,
     * do not forget to call {@link #init(Context)} again.
     * </p>
     *
     * @param scanCallbacksEnabledOnDestroy whether scan-callbacks should be invoked during
     *                                      the destroy process. If false, scan-calls will
     *                                      be cleared and not be invoked. If true, callbacks
     *                                      will be invoked. Usually, in these callback, we
     *                                      will perform some operations such as updating UI
     *                                      and rescanning. Before doing so, especially
     *                                      scanCallbacksEnabledOnDestroy is true, we must
     *                                      ensure BleManger is not destroyed by checking
     *                                      that {@link #getContext()} does not return null.
     * @param gattCallbacksEnabledOnDestroy whether gatt-callbacks should be invoked during
     *                                      the destroy process. If false, gatt-callbacks
     *                                      will be cleared and not be invoked. If true,
     *                                      callbacks will be invoked. Usually, in these
     *                                      callback, we will perform some operations such
     *                                      as updating UI and reconnecting. Before doing so,
     *                                      especially gattCallbacksEnabledOnDestroy is true,
     *                                      we must ensure BleManger is not destroyed by
     *                                      checking that {@link #getContext()} does not
     *                                      return null.
     */
    public void destroy(boolean scanCallbacksEnabledOnDestroy, boolean gattCallbacksEnabledOnDestroy) {
        unregisterBleReceiver();
        if (mGatt != null) {
            mGatt.destroy(gattCallbacksEnabledOnDestroy);
            mGatt = null;
        }
        if (mScan != null) {
            mScan.destroy(scanCallbacksEnabledOnDestroy);
            mScan = null;
        }
        mScanOptions = null;
        mConnectionOptions = null;
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
    @SuppressLint("MissingPermission")
    public static boolean enableBluetooth(Activity activity, int requestCode) {
        if (activity == null) {
            throw new IllegalArgumentException("Activity is null");
        }
        if (requestCode < 0) {
            throw new IllegalArgumentException("Request code cannot be negative");
        }
        if (!connectionPermissionGranted(activity)) {
            Logger.w("Android12 or higher, BleManager#enableBluetooth(Activity,int) requires the " +
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
     * some special Android devices' system may have been modified by manufacturers.
     * </p>
     * <p>
     * On Android 13+ devices, it doesn't work
     * </p>>
     *
     * @param enable enable or disable local bluetooth
     * @deprecated Use {@linkplain #enableBluetooth(Activity, int)} instead.
     */
    @Deprecated
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
     * @deprecated Use {@linkplain #isBluetoothEnabled()} instead.
     */
    @Deprecated
    public static boolean isBluetoothOn() {
        return isBluetoothEnabled();
    }

    /**
     * Return true if local bluetooth is enabled at present
     *
     * @return true if local bluetooth is turned on
     */
    public static boolean isBluetoothEnabled() {
        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        return adapter != null && adapter.isEnabled();
    }

    /**
     * Check if the address is valid
     *
     * @param address mac address
     * @return true if the address is valid
     * @deprecated Use {@linkplain #isValidAddress(String)} instead.
     */
    @Deprecated
    public static boolean isAddressValid(String address) {
        return isValidAddress(address);
    }

    /**
     * Check if the address is valid
     *
     * @param address mac address
     * @return true if the address is valid
     */
    public static boolean isValidAddress(String address) {
        return BluetoothAdapter.checkBluetoothAddress(address);
    }

    /**
     * Get all permissions BLE required
     *
     * @return all BLE permissions
     */
    public static List<String> getBleRequiredPermissions() {
        return Utils.getBleRequiredPermissions();
    }

    /**
     * Check if all BLE permissions have been granted.
     */
    public static boolean allBlePermissionsGranted(Context context) {
        return scanPermissionGranted(context) && connectionPermissionGranted(context);
    }

    /**
     * Check if scan-permission has been granted
     */
    public static boolean scanPermissionGranted(Context context) {
        return Utils.scanPermissionGranted(context);
    }

    /**
     * Check if connection-permission has been granted
     */
    public static boolean connectionPermissionGranted(Context context) {
        return Utils.connectionPermissionGranted(context);
    }

    /**
     * Get valid MTU range array, the first is min value, and the second is max value.
     *
     * @return MTU range array
     */
    public static int[] getValidMtuRange() {
        return new int[]{BleGatt.MTU_MIN, BleGatt.MTU_MAX};
    }

    /**
     * Get default max connection num
     *
     * @return max connection num
     */
    public static int getDefaultMaxConnectionNum() {
        return BleGatt.MAX_CONNECTION_NUM;
    }

    /**
     * Parse BLE scan record bytes into a {@link BleScanRecord}.
     *
     * @param scanRecord raw advertising data bytes from BLE scan result
     * @return parsed result {@link BleScanRecord}
     */
    public static BleScanRecord parseScanRecord(byte[] scanRecord) {
        return BleScanRecord.parseFromBytes(scanRecord);
    }

    /**
     * Is the characteristic readable?
     *
     * @param characteristic characteristic
     * @return true if this characteristic is readable
     */
    public static boolean isCharacteristicReadable(BluetoothGattCharacteristic characteristic) {
        return BluetoothGattUtils.isCharacteristicReadable(characteristic);
    }

    /**
     * Is the characteristic writable?
     *
     * @param characteristic characteristic
     * @return true if this characteristic is writable
     */
    public static boolean isCharacteristicWritable(BluetoothGattCharacteristic characteristic) {
        return BluetoothGattUtils.isCharacteristicWritable(characteristic);
    }

    /**
     * Does the characteristic support notification?
     *
     * @param characteristic characteristic
     * @return true if this characteristic supports notification
     */
    public static boolean isCharacteristicNotifiable(BluetoothGattCharacteristic characteristic) {
        return BluetoothGattUtils.isCharacteristicNotifiable(characteristic);
    }

    /**
     * Does the characteristic support indication?
     *
     * @param characteristic characteristic
     * @return true if this characteristic supports indication
     */
    public static boolean isCharacteristicIndicative(BluetoothGattCharacteristic characteristic) {
        return BluetoothGattUtils.isCharacteristicIndicative(characteristic);
    }

    public ScanOptions getScanOptions() {
        return mScanOptions == null ? ScanOptions.newInstance() : mScanOptions;
    }

    public ConnectionOptions getConnectionOptions() {
        return mConnectionOptions == null ? ConnectionOptions.newInstance() : mConnectionOptions;
    }

    public long getGattOperationTimeout() {
        return mGattOperationTimeoutMillis;
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

    private void registerBleReceiver() {
        if (mContext == null) {
            return;
        }
        mReceiver = new BleReceiver();
        mReceiver.setBluetoothStateChangeListener(new BleReceiver.BluetoothStateChangeListener() {
            @Override
            public void onBluetoothStateChanged(int state) {
                if (mScan != null && (mScan instanceof BluetoothStateListen)) {
                    ((BluetoothStateListen) mScan).onBluetoothStateChanged(state);
                }
                if (mGatt != null && (mGatt instanceof BluetoothStateListen)) {
                    ((BluetoothStateListen) mGatt).onBluetoothStateChanged(state);
                }
            }
        });
        mReceiver.setBluetoothBondStateChangeListener(new BleReceiver.BluetoothBondStateChangeListener() {
            @Override
            public void onBluetoothBondStateChanged(int newState, int previousState, BluetoothDevice device) {
                if (mGatt != null && (mGatt instanceof BluetoothBondListen)) {
                    ((BluetoothBondListen) mGatt).onBluetoothBondStateChanged(newState, previousState, device);
                }
            }
        });
        try {
            IntentFilter intentFilter = new IntentFilter();
            intentFilter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
            intentFilter.addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED);
            mContext.registerReceiver(mReceiver, intentFilter);
        } catch (Exception e) {
            Logger.e("Registering BleReceiver encounters an exception: " + e.getMessage());
        }
    }

    private void unregisterBleReceiver() {
        if (mContext == null || mReceiver == null) {
            return;
        }
        try {
            mReceiver.cancelBluetoothStateChangeListener();
            mReceiver.cancelBluetoothBondStateChangeListener();
            mContext.unregisterReceiver(mReceiver);
            mReceiver = null;
        } catch (Exception e) {
            Logger.e("Unregistering BleReceiver encounters an exception: " + e.getMessage());
        }
    }

    private void checkBluetoothAddress(String address) {
        if (!isValidAddress(address)) {
            throw new IllegalArgumentException("Invalid address: " + address);
        }
    }

    public static final class ScanOptions {
        private long mScanPeriod = 12000;
        private final BleScanFilter.Builder mScanFilterBuilder = new BleScanFilter.Builder();
        private List<BleScanFilter> mScanFilters;

        private ScanOptions() {

        }

        public static ScanOptions newInstance() {
            return new ScanOptions();
        }

        /**
         * Set scan period, i.e. connection timeout
         *
         * @param millis scan period
         * @return ScanOptions instance
         */
        public ScanOptions scanPeriod(long millis) {
            if (millis > 0) {
                this.mScanPeriod = millis;
            }
            return this;
        }

        /**
         * Set scan filter name
         *
         * @param deviceName filter device name
         * @return ScanOptions
         * @deprecated Use {@link #addScanFilter(BleScanFilter)} instead
         */
        @Deprecated
        public ScanOptions scanDeviceName(String deviceName) {
            return scanDeviceName(deviceName, false);
        }

        /**
         * Set scan filter name
         *
         * @param deviceName filter device name
         * @param fuzzy      is fuzzy?
         * @return ScanOptions
         * @deprecated Use {@link #addScanFilter(BleScanFilter)} instead
         */
        @Deprecated
        public ScanOptions scanDeviceName(String deviceName, boolean fuzzy) {
            if (fuzzy && TextUtils.isEmpty(deviceName)) {
                Logger.w("You enabled fuzzy device name matching, but provided a null or empty deviceName");
            }
            mScanFilterBuilder.setDeviceName(deviceName, fuzzy);
            return this;
        }

        /**
         * Set scan filter address
         *
         * @param deviceAddress filter address
         * @return ScanOptions
         * @deprecated Use {@link #addScanFilter(BleScanFilter)} instead
         */
        @Deprecated
        public ScanOptions scanDeviceAddress(String deviceAddress) {
            mScanFilterBuilder.setDeviceAddress(deviceAddress);
            return this;
        }

        /**
         * Set scan filter uuids
         *
         * @param serviceUuids filter uuids
         * @return Deprecated
         * @deprecated Use {@link #addScanFilter(BleScanFilter)} instead
         */
        @Deprecated
        public ScanOptions scanServiceUuids(UUID[] serviceUuids) {
            if (serviceUuids != null && serviceUuids.length > 0) {
                mScanFilterBuilder.setServiceUuid(serviceUuids[0]);
            } else {
                mScanFilterBuilder.setServiceUuid(null);
            }
            return this;
        }

        public ScanOptions addScanFilter(BleScanFilter filter) {
            if (filter == null) {
                return this;
            }
            if (mScanFilters == null) {
                mScanFilters = new ArrayList<>();
            }
            mScanFilters.add(filter);
            return this;
        }

        public ScanOptions clearScanFilters() {
            // Clear first filter
            mScanFilterBuilder.setDeviceName(null)
                    .setDeviceAddress(null)
                    .setServiceUuid(null);

            // Clear others
            if (mScanFilters != null) {
                mScanFilters.clear();
                mScanFilters = null;
            }

            return this;
        }

        public List<BleScanFilter> getScanFilters() {
            List<BleScanFilter> list = null;

            // Add first filter
            BleScanFilter filter = mScanFilterBuilder.build();
            if (!TextUtils.isEmpty(filter.getDeviceName()) || !TextUtils.isEmpty(filter.getDeviceAddress()) ||
                    filter.getServiceUuid() != null) {
                list = new ArrayList<>();
                list.add(filter);
            }

            // Add other list
            if (mScanFilters != null && !mScanFilters.isEmpty()) {
                if (list == null) {
                    list = new ArrayList<>();
                }
                list.addAll(mScanFilters);
            }

            return list;
        }

        public long getScanPeriod() {
            return mScanPeriod;
        }

        public String getScanDeviceName() {
            return mScanFilterBuilder.build().getDeviceName();
        }

        public String getScanDeviceAddress() {
            return mScanFilterBuilder.build().getDeviceAddress();
        }

        public UUID[] getScanServiceUuids() {
            UUID uuid = mScanFilterBuilder.build().getServiceUuid();
            if (uuid == null) {
                return null;
            }
            return new UUID[]{uuid};
        }
    }

    public static final class ConnectionOptions {
        private long mConnectionPeriod = 10000;
        private int mRetryCount = 0;
        private long mRetryDelay = 3000;
        private boolean mAutoConnect = false;

        private ConnectionOptions() {

        }

        public static ConnectionOptions newInstance() {
            return new ConnectionOptions();
        }

        /**
         * Set connection period, i.e. connection timeout
         *
         * @param millis connection period,unit: millisecond
         * @return ConnectionOptions instance
         * @deprecated Use {@link #connectionTimeout(long)} instead
         */
        @Deprecated
        public ConnectionOptions connectionPeriod(long millis) {
            return connectionTimeout(millis);
        }

        /**
         * Set a connection timeout duration.
         * <p>
         * Note that if you have called {@link #autoConnect(boolean)} and pass true,
         * connection timeout will be meaningless.
         * </p>
         *
         * @param millis connection timeout
         */
        public ConnectionOptions connectionTimeout(long millis) {
            if (millis > 0) {
                mConnectionPeriod = millis;
            }
            return this;
        }

        /**
         * Auto-connection, if true, once device is in rang, system will connect to the target
         * device automatically.
         * <p>
         * Note that:
         * 1.If autoConnect is true, after calling any #connect() method, such as
         * {@link #connect(String, ConnectionOptions, BleConnectCallback)}, once target device
         * is in rang, system try to connect to it automatically, that means connection timeout
         * will never occur
         * 2.Auto-connection will not survive turning off the android device’s Bluetooth, we must
         * call any #connect() again after re-enabling Bluetooth. In other words, if bluetooth
         * is turned off, auto-connection will be invalid, so if you want to connect to target
         * device, do not forget to call #connect() again
         * 3.If you call any #disconnect(), such as {@link #disconnect(String)}, it will cancel
         * auto-connection
         * </p>
         *
         * @param autoConnect whether connect automatically
         */
        public ConnectionOptions autoConnect(boolean autoConnect) {
            this.mAutoConnect = autoConnect;
            return this;
        }

        /**
         * Retry connection if connection failed.
         * <p>
         * Note that only gatt error can trigger connection-retry. The following situations
         * will not trigger a connection retry.
         * 1.Auto-connect
         * 2.Connection timeout
         * 3.Bluetooth off
         * 4.Call any #disconnect() method, such as {@link #disconnect(String)}
         * </p>
         *
         * @param retryCount       retry count, from 0 to 8
         * @param retryDelayMillis retry delay (unit: millis), from 500 to 60000
         */
        public ConnectionOptions retryWhenConnectionFailed(int retryCount, long retryDelayMillis) {
            if (retryCount < 0) {
                retryCount = 0;
            }
            if (retryCount > 8) {
                retryCount = 8;
            }
            if (retryDelayMillis > 60 * 1000) {
                retryDelayMillis = 60 * 1000;
            }
            if (retryDelayMillis < 0) {
                retryDelayMillis = 500;
            }
            this.mRetryCount = retryCount;
            this.mRetryDelay = retryDelayMillis;
            return this;
        }

        public long getConnectionPeriod() {
            return mConnectionPeriod;
        }

        public int getRetryCount() {
            return mRetryCount;
        }

        public long getRetryDelay() {
            return mRetryDelay;
        }

        public boolean isAutoConnect() {
            return mAutoConnect;
        }
    }

    public static final class AccessKey {
        private AccessKey() {

        }
    }

    public interface BluetoothStateListen {
        void onBluetoothStateChanged(int state);
    }

    public interface BluetoothBondListen {
        void onBluetoothBondStateChanged(int newState, int previousState, BluetoothDevice device);
    }
}
