package com.ficat.easyble.gatt;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;

import com.ficat.easyble.BleDevice;
import com.ficat.easyble.BleErrorCodes;
import com.ficat.easyble.BleManager;
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
import com.ficat.easyble.utils.BleDataUtils;
import com.ficat.easyble.utils.BluetoothGattUtils;
import com.ficat.easyble.utils.Logger;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Map;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

@SuppressLint("MissingPermission")
public final class BleGattCommunicator extends BluetoothGattCallback {
    /**
     * Connection not establish
     */
    private static final int DISCONNECTED = 1;

    /**
     * Connection is in progress
     */
    private static final int CONNECTING = 2;

    /**
     * Connection established but services not discovered
     */
    private static final int CONNECTED_BUT_SERVICES_NOT_DISCOVERED = 3;

    /**
     * Connection established and service discovery is in progress
     */
    private static final int CONNECTED_AND_SERVICES_DISCOVERING = 4;

    /**
     * Connection established and services discovered
     */
    private static final int CONNECTED_AND_SERVICES_DISCOVERED = 5;


    /**
     * Default connection timeout
     */
    private static final long DEFAULT_CONNECTION_TIMEOUT_MILLIS = 10 * 1000;


    /**
     * Bonding-process timeout
     */
    private static final long BONDING_TIMEOUT_MILLIS = 40 * 1000;

    /**
     * Write-batch delay msg
     */
    private static final int MSG_WHAT_WRITE_BATCH_DELAY = 100;

    /**
     * Connection timeout msg
     */
    private static final int MSG_WHAT_CONNECTION_TIMEOUT = 101;

    /**
     * Operation timeout msg
     */
    private static final int MSG_WHAT_OPERATION_TIMEOUT = 102;

    /**
     * Discover services delay msg
     */
    private static final int MSG_WHAT_DISCOVER_SERVICES_DELAY = 103;

    /**
     * Connection retry msg
     */
    private static final int MSG_WHAT_CONNECTION_RETRY = 104;


    /**
     * Failed to enable/disable notification
     */
    private static final int NOTIFY_COMPLETED_FAILURE = 0;

    /**
     * Enable/Disable-notification operation succeed
     */
    private static final int NOTIFY_COMPLETED_SUCCESS = 1;

    /**
     * Enable/Disable-notification operation started
     */
    private static final int NOTIFY_STARTED = 2;

    private final Handler mHandler;
    private BleDevice mDevice; // The target remote device
    private BleConnectCallback mConnectCallback; // Connection callback
    private final Map<OperationIdentify, BleNotifyCallback> mNotifyCallbackMap;

    /**
     * If the remote device updates the PHY, onPhyUpdate() is also triggered, so
     * this is used to listen phy change
     */
    private BlePhyPreferenceCallback mPhyPreferenceCallback;

    private BluetoothGatt mGatt;
    private long mConnTimeout;
    private int mConnRetryCount;
    private long mConnRetryDelay;

    /**
     * If true, once target device is in rang, system try to connect to it automatically。
     * <p>
     * 1.For auto-connection, connection timeout is meaningless.
     * 2.If auto-connection, connection retry will be invalid.
     * 3.Auto-connection does not work after bluetooth-off
     * 4.Calling #disconnect() will cancel current auto-connection
     * </p>
     */
    private volatile boolean mAutoConnect;
    private volatile int mConnState = DISCONNECTED; // Current connection state
    private volatile int mCurrentMtu = BleGatt.MTU_MIN; // Current MTU
    private final Object mConnectionLock = new Object(); // The lock used to connection
    private final Object mOperationLock = new Object(); // The lock used to operation
    private final Queue<BaseOperation> mOperationQueue; // Operation queue
    private boolean mOperationExecuting = false; // Current operation is in progress?

    BleGattCommunicator(BleDevice device) {
        if (device == null) {
            throw new IllegalArgumentException("BleDevice is null");
        }
        this.mDevice = device;
        this.mOperationQueue = new ConcurrentLinkedQueue<>();
        this.mNotifyCallbackMap = new ConcurrentHashMap<>();
        this.mHandler = new Handler(Looper.getMainLooper(), msg -> {
            switch (msg.what) {
                case MSG_WHAT_CONNECTION_TIMEOUT:
                    onConnectionTimeout();
                    return true;
                case MSG_WHAT_OPERATION_TIMEOUT:
                    if (msg.obj instanceof BaseOperation) {
                        onOperationTimeout((BaseOperation) msg.obj);
                    }
                    return true;
                case MSG_WHAT_DISCOVER_SERVICES_DELAY:
                    tryToDiscoverServices();
                    break;
                case MSG_WHAT_CONNECTION_RETRY:
                    if (msg.obj instanceof ConnectionRetry) {
                        onConnectionRetry((ConnectionRetry) msg.obj);
                    }
                    break;
            }
            return false;
        });
    }

    /**
     * Only connection established and services discovered we consider real connection
     * has finished
     *
     * @return true if connected, otherwise false
     */
    boolean isConnected() {
        return mConnState == CONNECTED_AND_SERVICES_DISCOVERED;
    }

    boolean isConnecting() {
        return mConnState >= CONNECTING && mConnState <= CONNECTED_AND_SERVICES_DISCOVERING;
    }

    boolean isDisconnected() {
        return mConnState == DISCONNECTED;
    }

    int getMtu() {
        return this.mCurrentMtu;
    }

    void updateBleDevice(BleDevice device) {
        if (device == null || !device.getAddress().equals(mDevice.getAddress())) {
            return;
        }
        this.mDevice = device;
    }

    BleDevice getBleDevice() {
        return this.mDevice;
    }

    BluetoothGatt getBluetoothGatt() {
        return this.mGatt;
    }

    void onBluetoothOff() {
        synchronized (mConnectionLock) {
            if (isDisconnected()) {
                return;
            }
            // AutoConnect will not survive turning off the phone’s Bluetooth, no matter what
            // autoConnect is true or false, we just need to close Gatt
            boolean connecting = isConnecting();
            boolean connected = isConnected();
            mHandler.removeMessages(MSG_WHAT_CONNECTION_TIMEOUT);
            mHandler.removeMessages(MSG_WHAT_WRITE_BATCH_DELAY);
            mHandler.removeMessages(MSG_WHAT_OPERATION_TIMEOUT);
            mHandler.removeMessages(MSG_WHAT_DISCOVER_SERVICES_DELAY);
            // Cancel connection-retry, during the connection retry wait period, connection
            // state is CONNECTING
            mHandler.removeMessages(MSG_WHAT_CONNECTION_RETRY);
            BleConnectCallback callback = mConnectCallback;
            if (mGatt != null) {
                mGatt.disconnect();
                refreshDeviceCache();
                mGatt.close();
                mGatt = null;
            }
            stopAndClearOperations();
            clearAllCallbacks();
            resetConnParamsAndMtu();
            mConnState = DISCONNECTED;
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    if (callback != null) {
                        if (connecting) {
                            callback.onConnectionFailed(BleErrorCodes.BLUETOOTH_OFF, mDevice);
                        } else if (connected) {
                            callback.onDisconnected(mDevice, BluetoothGatt.GATT_SUCCESS);
                        }
                    }
                }
            });
        }
    }

    private void onConnectionTimeout() {
        synchronized (mConnectionLock) {
            if (!isConnecting()) {
                return;
            }
            mHandler.removeMessages(MSG_WHAT_WRITE_BATCH_DELAY);
            mHandler.removeMessages(MSG_WHAT_OPERATION_TIMEOUT);
            mHandler.removeMessages(MSG_WHAT_DISCOVER_SERVICES_DELAY);
            mHandler.removeMessages(MSG_WHAT_CONNECTION_RETRY);
            BleConnectCallback callback = mConnectCallback;
            if (mGatt != null) {
                mGatt.disconnect();
                refreshDeviceCache();
                mGatt.close();
                mGatt = null;
            }
            stopAndClearOperations();
            clearAllCallbacks();
            resetConnParamsAndMtu();
            mConnState = DISCONNECTED;
            if (callback != null) {
                callback.onConnectionFailed(BleErrorCodes.TIMEOUT, mDevice);
            }
        }
    }

    private void onConnectionRetry(ConnectionRetry params) {
        synchronized (mConnectionLock) {
            if (params.mAutoConnect || isConnected()) {
                Logger.d(mAutoConnect ? "Retry connection but found auto-connect is true" :
                        "Retry connection but found connection has established");
                return;
            }
            params.mRetryCount--;
            BluetoothGatt gatt = connectGatt(false);
            if (gatt == null) {
                mConnState = DISCONNECTED;
                BleConnectCallback callback = params.mConnectCallback;
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        if (callback != null) {
                            callback.onConnectionFailed(BleErrorCodes.UNKNOWN, mDevice);
                        }
                    }
                });
                return;
            }
            mAutoConnect = false;
            mConnTimeout = params.mConnectionTimeout;
            mConnRetryCount = params.mRetryCount;
            mConnRetryDelay = params.mRetryDelay;
            mGatt = gatt;
            mConnectCallback = params.mConnectCallback;
            mConnState = CONNECTING;
            // Send timeout msg
            Message msg = Message.obtain();
            msg.what = MSG_WHAT_CONNECTION_TIMEOUT;
            mHandler.sendMessageDelayed(msg, mConnTimeout > 0 ? mConnTimeout : DEFAULT_CONNECTION_TIMEOUT_MILLIS);
        }
    }

    void connect(long timeoutMillis, int retryCount, long retryDelay, boolean autoConnect, BleConnectCallback callback) {
        synchronized (mConnectionLock) {
            if (isConnecting() || isConnected()) {
                if (mConnectCallback != callback) {
                    mHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            callback.onConnectionFailed(BleErrorCodes.CONNECTION_ALREADY_STARTED_OR_ESTABLISHED, mDevice);
                        }
                    });
                }
                return;
            }
            // Connect to GATT
            BluetoothGatt gatt = connectGatt(autoConnect);
            // Failed to connect GATT
            if (gatt == null) {
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        callback.onConnectionFailed(BleErrorCodes.UNKNOWN, mDevice);
                    }
                });
                return;
            }
            // Connection started
            mAutoConnect = autoConnect;
            mConnTimeout = timeoutMillis;
            mConnRetryCount = retryCount;
            mConnRetryDelay = retryDelay;
            mGatt = gatt;
            mConnectCallback = callback;
            mConnState = CONNECTING;
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    callback.onConnectionStarted(mDevice);
                }
            });
            if (autoConnect) {
                // Do nothing
                // After calling BluetoothDevice#connectGatt(), once target device is
                // in rang, system try to connect to it automatically, so for autoConnect,
                // it does not trigger connection timeout.
            } else {
                // Send connection timeout msg
                Message msg = Message.obtain();
                msg.what = MSG_WHAT_CONNECTION_TIMEOUT;
                mHandler.sendMessageDelayed(msg, timeoutMillis > 0 ? timeoutMillis : DEFAULT_CONNECTION_TIMEOUT_MILLIS);
            }
        }
    }

    private BluetoothGatt connectGatt(boolean autoConnect) {
        BluetoothGatt gatt;
        Context context = BleManager.getInstance().getContext();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            // If we use the address to connect device, the bluetooth type may be
            // DEVICE_TYPE_UNKNOWN (if not scan).To avoid this, no matter what the
            // bluetooth type is, we set 'transport' to BluetoothDevice.TRANSPORT_LE.
            Logger.d("autoConnect is " + autoConnect);
            gatt = mDevice.getBluetoothDevice().connectGatt(context, autoConnect, this, BluetoothDevice.TRANSPORT_LE);
        } else {
            gatt = mDevice.getBluetoothDevice().connectGatt(context, autoConnect, this);
        }
        return gatt;
    }

    void disconnect(boolean closeGattImmediatelyIfConnected, boolean callbackEnabled) {
        synchronized (mConnectionLock) {
            if (isDisconnected()) {
                return;
            }
            // Cancel connection-retry
            mHandler.removeMessages(MSG_WHAT_CONNECTION_RETRY);
            if (mGatt == null) {
                return;
            }
            mGatt.disconnect();
            boolean connecting = isConnecting();
            boolean connected = isConnected();
            boolean shouldCloseGattOnConnected = connected && closeGattImmediatelyIfConnected;
            // 1.If connecting, shouldCloseGattOnConnected, close gatt.
            // 2.For auto-connection, once we call disconnect() manually, it means we no longer
            // expect auto-connection, so close gatt and reset autoConnect
            if (connecting || shouldCloseGattOnConnected || mAutoConnect) {
                // Remove connection timeout message if connection is establishing
                mHandler.removeMessages(MSG_WHAT_CONNECTION_TIMEOUT);
                mHandler.removeMessages(MSG_WHAT_WRITE_BATCH_DELAY);
                mHandler.removeMessages(MSG_WHAT_OPERATION_TIMEOUT);
                mHandler.removeMessages(MSG_WHAT_DISCOVER_SERVICES_DELAY);
                BleConnectCallback callback = mConnectCallback;
                refreshDeviceCache();
                mGatt.close();
                mGatt = null;
                stopAndClearOperations();
                clearAllCallbacks();
                resetConnParamsAndMtu();
                mConnState = DISCONNECTED;
                if (!callbackEnabled) {
                    return;
                }
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        if (callback == null) {
                            return;
                        }
                        if (connecting) {
                            callback.onConnectionFailed(BleErrorCodes.CONNECTION_CANCELED, mDevice);
                        } else if (connected) {
                            callback.onDisconnected(mDevice, BluetoothGatt.GATT_SUCCESS);
                        }
                    }
                });
            }
        }
    }

    void enableNotify(UUID serviceUuid, UUID notifyUuid, BleNotifyCallback callback) {
        if (!isConnected()) {
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    callback.onNotifyFailed(BleErrorCodes.CONNECTION_NOT_ESTABLISHED, notifyUuid, mDevice);
                }
            });
            return;
        }
        enqueueOperation(new Notify(serviceUuid, notifyUuid, true, callback));
    }

    void disableNotify(UUID serviceUuid, UUID notifyUuid) {
        if (!isConnected()) {
            return;
        }
        enqueueOperation(new Notify(serviceUuid, notifyUuid, false, null));
    }

    void read(UUID serviceUuid, UUID readUuid, BleReadCallback callback) {
        if (!isConnected()) {
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    callback.onReadFailed(BleErrorCodes.CONNECTION_NOT_ESTABLISHED, readUuid, mDevice);
                }
            });
            return;
        }
        enqueueOperation(new Read(serviceUuid, readUuid, callback));
    }

    void write(UUID serviceUuid, UUID writeUuid, byte[] data, BleWriteCallback callback) {
        if (!isConnected()) {
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    callback.onWriteFailed(BleErrorCodes.CONNECTION_NOT_ESTABLISHED, data, writeUuid, mDevice);
                }
            });
            return;
        }
        enqueueOperation(new Write(serviceUuid, writeUuid, data, callback));
    }

    void writeByBatch(UUID serviceUuid, UUID writeUuid, byte[] writeData, int lengthPerBatch,
                      long batchInterval, BleWriteByBatchCallback callback) {
        if (!isConnected()) {
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    callback.onWriteBatchFailed(BleErrorCodes.CONNECTION_NOT_ESTABLISHED,
                            0, writeData, writeUuid, mDevice);
                }
            });
            return;
        }
        Queue<byte[]> queue = BleDataUtils.getBatchData(writeData, lengthPerBatch);
        if (queue.isEmpty()) {
            callback.onWriteBatchFailed(BleErrorCodes.UNKNOWN, 0, writeData, writeUuid, mDevice);
            return;
        }
        enqueueOperation(new WriteBatch(serviceUuid, writeUuid, writeData, lengthPerBatch,
                batchInterval, queue, callback));
    }

    void readRssi(BleRssiCallback callback) {
        if (!isConnected()) {
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    callback.onRssiFailed(BleErrorCodes.CONNECTION_NOT_ESTABLISHED, mDevice);
                }
            });
            return;
        }
        enqueueOperation(new RssiRead(callback));
    }

    void requestMtu(int mtu, BleMtuCallback callback) {
        if (!isConnected()) {
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    callback.onMtuFailed(BleErrorCodes.CONNECTION_NOT_ESTABLISHED, mDevice);
                }
            });
            return;
        }
        enqueueOperation(new MtuSet(mtu, callback));
    }

    void descriptorRead(UUID serviceUuid, UUID characteristicUuid, UUID descriptorUuid,
                        BleDescriptorReadCallback callback) {
        if (!isConnected()) {
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    callback.onDescriptorReadFailed(BleErrorCodes.CONNECTION_NOT_ESTABLISHED,
                            descriptorUuid, mDevice);
                }
            });
            return;
        }
        enqueueOperation(new DescriptorRead(serviceUuid, characteristicUuid, descriptorUuid, callback));
    }

    void descriptorWrite(UUID serviceUuid, UUID characteristicUuid, UUID descriptorUuid, byte[] data,
                         BleDescriptorWriteCallback callback) {
        if (!isConnected()) {
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    callback.onDescriptorWriteFailed(BleErrorCodes.CONNECTION_NOT_ESTABLISHED, data,
                            descriptorUuid, mDevice);
                }
            });
            return;
        }
        enqueueOperation(new DescriptorWrite(serviceUuid, characteristicUuid, descriptorUuid, data, callback));
    }

    void readPhy(BlePhyReadCallback callback) {
        if (!isConnected()) {
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    callback.onPhyReadFailed(BleErrorCodes.CONNECTION_NOT_ESTABLISHED, mDevice);
                }
            });
            return;
        }
        enqueueOperation(new PhyRead(callback));
    }

    void setPreferencePhy(int txPhy, int rxPhy, int phyOptions, BlePhyPreferenceCallback callback) {
        if (!isConnected()) {
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    callback.onPhyPreferenceSetFailed(BleErrorCodes.CONNECTION_NOT_ESTABLISHED, mDevice);
                }
            });
            return;
        }
        enqueueOperation(new PhyPreferenceSet(txPhy, rxPhy, phyOptions, callback));
    }

    void requestConnectionPriority(int connPriority, BleConnectionPriorityCallback callback) {
        if (!isConnected()) {
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    callback.onConnectionPriorityFailed(BleErrorCodes.CONNECTION_NOT_ESTABLISHED, mDevice);
                }
            });
            return;
        }
        enqueueOperation(new ConnectionPrioritySet(connPriority, callback));
    }

    private void enqueueOperation(BaseOperation operation) {
        if (!mOperationQueue.offer(operation)) {
            Logger.d("Failed to enqueue operation");
        }
        tryNextOperation();
    }

    private void tryNextOperation() {
        synchronized (mOperationLock) {
            if (mOperationExecuting) {
                return;
            }
            BaseOperation operation = mOperationQueue.peek();
            if (operation == null) {
                return;
            }
            mOperationExecuting = true;
            boolean started = false;
            long timeoutMillis = BleManager.getInstance().getGattOperationTimeout();
            if (operation instanceof Read) {
                started = read((Read) operation);
            } else if (operation instanceof Write) {
                started = write((Write) operation);
            } else if (operation instanceof WriteBatch) {
                WriteBatch wb = (WriteBatch) operation;
                started = writeByBatch(wb);
                // We should calculate a max timeout duration
                timeoutMillis = wb.mBatchNum * BleManager.getInstance().getGattOperationTimeout() +
                        (wb.mBatchNum - 1) * wb.mBatchInterval;
            } else if (operation instanceof MtuSet) {
                started = requestMtu((MtuSet) operation);
            } else if (operation instanceof RssiRead) {
                started = readRssi((RssiRead) operation);
            } else if (operation instanceof Notify) {
                int ret = notify((Notify) operation);
                started = ret == NOTIFY_STARTED;
            } else if (operation instanceof DescriptorWrite) {
                started = descriptorWrite((DescriptorWrite) operation);
            } else if (operation instanceof DescriptorRead) {
                started = descriptorRead((DescriptorRead) operation);
            } else if (operation instanceof PhyRead) {
                started = readPhy((PhyRead) operation);
            } else if (operation instanceof PhyPreferenceSet) {
                started = setPreferencePhy((PhyPreferenceSet) operation);
            } else if (operation instanceof ConnectionPrioritySet) {
                started = requestConnectionPriority((ConnectionPrioritySet) operation);
            }
            if (started) {
                Message msg = Message.obtain();
                msg.what = MSG_WHAT_OPERATION_TIMEOUT;
                msg.obj = operation;
                mHandler.sendMessageDelayed(msg, timeoutMillis);
            } else {
                mOperationQueue.poll();
                mOperationExecuting = false;
                mHandler.post(() -> tryNextOperation());
            }
        }
    }

    private void onOperationTimeout(BaseOperation operation) {
        synchronized (mOperationLock) {
            if (!mOperationExecuting) {
                return;
            }
            BaseOperation head = mOperationQueue.peek();
            if (head != operation) {
                return;
            }
            mOperationQueue.poll();
            mOperationExecuting = false;
            if (operation instanceof ConnectionPrioritySet) {
                // No any callback will be triggered after requesting connection
                // priority. so we consider this request succeed.
                ConnectionPrioritySet cps = (ConnectionPrioritySet) operation;
                cps.mBleConnectionPriorityCallback.onConnectionPriorityRequestSuccess(
                        cps.mConnectionPriority, mDevice);
            } else if (operation instanceof DescriptorRead) {
                DescriptorRead dr = ((DescriptorRead) operation);
                dr.mBleDescriptorReadCallback.onDescriptorReadFailed(BleErrorCodes.TIMEOUT,
                        dr.mDescriptorUuid, mDevice);
            } else if (operation instanceof DescriptorWrite) {
                DescriptorWrite dw = ((DescriptorWrite) operation);
                dw.mBleDescriptorWriteCallback.onDescriptorWriteFailed(BleErrorCodes.TIMEOUT,
                        dw.mData, dw.mDescriptorUuid, mDevice);
            } else if (operation instanceof MtuSet) {
                MtuSet ms = ((MtuSet) operation);
                ms.mBleMtuCallback.onMtuFailed(BleErrorCodes.TIMEOUT, mDevice);
            } else if (operation instanceof Notify) {
                Notify notify = ((Notify) operation);
                if (notify.mEnable) {
                    notify.mBleNotifyCallback.onNotifyFailed(BleErrorCodes.TIMEOUT,
                            notify.mNotifyUuid, mDevice);
                }
            } else if (operation instanceof PhyPreferenceSet) {
                PhyPreferenceSet pps = ((PhyPreferenceSet) operation);
                pps.mBlePhyPreferenceCallback.onPhyPreferenceSetFailed(BleErrorCodes.TIMEOUT, mDevice);
                mPhyPreferenceCallback = pps.mBlePhyPreferenceCallback;
            } else if (operation instanceof PhyRead) {
                PhyRead pr = ((PhyRead) operation);
                pr.mBlePhyReadCallback.onPhyReadFailed(BleErrorCodes.TIMEOUT, mDevice);
            } else if (operation instanceof Read) {
                Read read = ((Read) operation);
                read.mBleReadCallback.onReadFailed(BleErrorCodes.TIMEOUT, read.mReadUuid, mDevice);
            } else if (operation instanceof RssiRead) {
                RssiRead rr = ((RssiRead) operation);
                rr.mBleRssiCallback.onRssiFailed(BleErrorCodes.TIMEOUT, mDevice);
            } else if (operation instanceof Write) {
                Write write = ((Write) operation);
                write.mBleWriteCallback.onWriteFailed(BleErrorCodes.TIMEOUT, write.mData,
                        write.mWriteUuid, mDevice);
            } else if (operation instanceof WriteBatch) {
                WriteBatch wb = ((WriteBatch) operation);
                mHandler.removeMessages(MSG_WHAT_WRITE_BATCH_DELAY);
                // Current pack may not succeed, so - 1
                int writtenBatchCount = wb.mBatchNum - wb.mBatchQueue.size() - 1;
                int writtenLen = wb.mLengthPerBatch * writtenBatchCount;
                wb.mBleWriteByBatchCallback.onWriteBatchFailed(BleErrorCodes.TIMEOUT, writtenLen,
                        wb.mData, wb.mWriteUuid, mDevice);
            }
        }
        tryNextOperation();
    }

    private int notify(Notify operation) {
        UUID serviceUuid = operation.mServiceUuid;
        UUID notifyUuid = operation.mNotifyUuid;
        boolean enable = operation.mEnable;
        BleNotifyCallback callback = operation.mBleNotifyCallback;

        // Check conditions
        boolean connected = mGatt != null && isConnected();
        BluetoothGattService service = null;
        BluetoothGattCharacteristic characteristic = null;
        boolean notifiableOrIndicative = false;
        if (connected) {
            service = mGatt.getService(serviceUuid);
        }
        if (service != null) {
            characteristic = service.getCharacteristic(notifyUuid);
        }
        if (characteristic != null) {
            notifiableOrIndicative = BluetoothGattUtils.isCharacteristicNotifiable(characteristic) ||
                    BluetoothGattUtils.isCharacteristicIndicative(characteristic);
        }
        if (!connected || service == null || characteristic == null || !notifiableOrIndicative) {
            int code;
            if (!connected) {
                code = BleErrorCodes.CONNECTION_NOT_ESTABLISHED;
            } else if (service == null) {
                code = BleErrorCodes.SERVICE_NOT_FOUND;
            } else if (characteristic == null) {
                code = BleErrorCodes.CHARACTERISTIC_NOT_FOUND_IN_SERVICE;
            } else {
                code = BleErrorCodes.NOTIFICATION_OR_INDICATION_UNSUPPORTED;
            }
            if (enable) {
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        if (callback == null) return;
                        callback.onNotifyFailed(code, notifyUuid, mDevice);
                    }
                });
            }
            return NOTIFY_COMPLETED_FAILURE;
        }

        // Enable or disable characteristic notification
        boolean success = mGatt.setCharacteristicNotification(characteristic, enable);
        if (!success) {
            if (enable) {
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        if (callback == null) return;
                        callback.onNotifyFailed(BleErrorCodes.UNKNOWN, notifyUuid, mDevice);
                    }
                });
            }
            return NOTIFY_COMPLETED_FAILURE;
        }

        // Try to write descriptor to enable or disable notification
        BluetoothGattDescriptor descriptor = characteristic.getDescriptor(
                UUID.fromString(BleGatt.CHARACTERISTIC_CONFIG_DESCRIPTOR_UUID));
        if (descriptor == null) {
            OperationIdentify identify = getOperationIdentify(mNotifyCallbackMap, serviceUuid, notifyUuid);
            if (enable) {
                if (identify == null) {
                    identify = new OperationIdentify(serviceUuid, notifyUuid);
                }
                mNotifyCallbackMap.put(identify, callback);
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        if (callback == null) return;
                        callback.onNotifySuccess(notifyUuid, mDevice);
                    }
                });
            } else {
                if (identify != null) {
                    mNotifyCallbackMap.remove(identify);
                }
            }
            return NOTIFY_COMPLETED_SUCCESS;
        }
        if (enable) {
            if (BluetoothGattUtils.isCharacteristicNotifiable(characteristic)) {
                descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
            }
            if (BluetoothGattUtils.isCharacteristicIndicative(characteristic)) {
                descriptor.setValue(BluetoothGattDescriptor.ENABLE_INDICATION_VALUE);
            }
        } else {
            descriptor.setValue(BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE);
        }
        // If the write operation was initiated successfully, wait operation
        // result until BleGattCommunicator#onDescriptorWrite() is called back
        if (mGatt.writeDescriptor(descriptor)) {
            return NOTIFY_STARTED;
        } else {
            if (enable) {
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        if (callback == null) return;
                        callback.onNotifyFailed(BleErrorCodes.UNKNOWN, notifyUuid, mDevice);
                    }
                });
            }
            return NOTIFY_COMPLETED_FAILURE;
        }
    }

    private boolean read(Read operation) {
        UUID serviceUuid = operation.mServiceUuid;
        UUID readUuid = operation.mReadUuid;
        BleReadCallback callback = operation.mBleReadCallback;
        // Check conditions
        boolean connected = mGatt != null && isConnected();
        BluetoothGattService service = null;
        BluetoothGattCharacteristic characteristic = null;
        boolean readable = false;
        if (connected) {
            service = mGatt.getService(serviceUuid);
        }
        if (service != null) {
            characteristic = service.getCharacteristic(readUuid);
        }
        if (characteristic != null) {
            readable = BluetoothGattUtils.isCharacteristicReadable(characteristic);
        }
        if (!connected || service == null || characteristic == null || !readable) {
            int code;
            if (!connected) {
                code = BleErrorCodes.CONNECTION_NOT_ESTABLISHED;
            } else if (service == null) {
                code = BleErrorCodes.SERVICE_NOT_FOUND;
            } else if (characteristic == null) {
                code = BleErrorCodes.CHARACTERISTIC_NOT_FOUND_IN_SERVICE;
            } else {
                code = BleErrorCodes.READ_UNSUPPORTED;
            }
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    callback.onReadFailed(code, readUuid, mDevice);
                }
            });
            return false;
        }
        // Read data from characteristic
        if (!mGatt.readCharacteristic(characteristic)) {
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    callback.onReadFailed(BleErrorCodes.UNKNOWN, readUuid, mDevice);
                }
            });
            return false;
        }
        return true;
    }

    private boolean write(Write operation) {
        UUID serviceUuid = operation.mServiceUuid;
        UUID writeUuid = operation.mWriteUuid;
        byte[] data = operation.mData;
        BleWriteCallback callback = operation.mBleWriteCallback;
        // Check conditions
        boolean connected = mGatt != null && isConnected();
        BluetoothGattService service = null;
        BluetoothGattCharacteristic characteristic = null;
        boolean writable = false;
        if (connected) {
            service = mGatt.getService(serviceUuid);
        }
        if (service != null) {
            characteristic = service.getCharacteristic(writeUuid);
        }
        if (characteristic != null) {
            writable = BluetoothGattUtils.isCharacteristicWritable(characteristic);
        }
        if (!connected || service == null || characteristic == null || !writable) {
            int code;
            if (!connected) {
                code = BleErrorCodes.CONNECTION_NOT_ESTABLISHED;
            } else if (service == null) {
                code = BleErrorCodes.SERVICE_NOT_FOUND;
            } else if (characteristic == null) {
                code = BleErrorCodes.CHARACTERISTIC_NOT_FOUND_IN_SERVICE;
            } else {
                code = BleErrorCodes.WRITE_UNSUPPORTED;
            }
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    callback.onWriteFailed(code, data, writeUuid, mDevice);
                }
            });
            return false;
        }
        return writeData(characteristic, data, callback);
    }

    private boolean writeByBatch(WriteBatch operation) {
        UUID serviceUuid = operation.mServiceUuid;
        UUID writeUuid = operation.mWriteUuid;
        byte[] writeData = operation.mData;
        BleWriteByBatchCallback callback = operation.mBleWriteByBatchCallback;
        // Check conditions
        boolean connected = mGatt != null && isConnected();
        BluetoothGattService service = null;
        BluetoothGattCharacteristic characteristic = null;
        boolean writable = false;
        boolean validData = false;
        if (connected) {
            service = mGatt.getService(serviceUuid);
            validData = writeData != null && writeData.length > 0;
        }
        if (service != null) {
            characteristic = service.getCharacteristic(writeUuid);
        }
        if (characteristic != null) {
            writable = BluetoothGattUtils.isCharacteristicWritable(characteristic);
        }
        if (!connected || service == null || characteristic == null || !writable || !validData) {
            int code;
            if (!connected) {
                code = BleErrorCodes.CONNECTION_NOT_ESTABLISHED;
            } else if (service == null) {
                code = BleErrorCodes.SERVICE_NOT_FOUND;
            } else if (characteristic == null) {
                code = BleErrorCodes.CHARACTERISTIC_NOT_FOUND_IN_SERVICE;
            } else if (!writable) {
                code = BleErrorCodes.WRITE_UNSUPPORTED;
            } else {
                code = BleErrorCodes.DATA_LENGTH_GREATER_THAN_MTU;
            }
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    callback.onWriteBatchFailed(code, 0, writeData, writeUuid, mDevice);
                }
            });
            return false;
        }

        BluetoothGattCharacteristic gattChar = characteristic;
        BleWriteCallback writeCallback = new BleWriteCallback() {
            private boolean finishOperation() {
                synchronized (mOperationLock) {
                    if (!mOperationExecuting) {
                        return false;
                    }
                    BaseOperation baseOperation = mOperationQueue.peek();
                    if (baseOperation instanceof WriteBatch) {
                        WriteBatch operation = (WriteBatch) baseOperation;
                        if (operation.mBleWriteCallback != this) {
                            return false;
                        }
                        mHandler.removeMessages(MSG_WHAT_OPERATION_TIMEOUT, operation);
                        mOperationQueue.poll();
                        mOperationExecuting = false;
                        return true;
                    }
                    return false;
                }
            }

            @Override
            public void onWriteSuccess(byte[] data, UUID characteristicUuid, BleDevice device) {
                // Notify current progress
                int writtenBatchCount = operation.mBatchNum - operation.mBatchQueue.size();
                // The length of the last batch may be not equal 'lengthPerPackage'
                int writtenLen = (writtenBatchCount - 1) * operation.mLengthPerBatch + data.length;
                float progress = writtenLen / (float) operation.mData.length;
                operation.mBleWriteByBatchCallback.onWriteBatchProgress(progress, characteristicUuid, device);
                final byte[] next = operation.mBatchQueue.poll();
                // All batches have been written
                if (next == null) {
                    operation.mBleWriteByBatchCallback.onWriteBatchSuccess(operation.mData,
                            characteristicUuid, device);
                    return;
                }
                // Write the next batch
                if (!isConnected()) {
                    if (finishOperation()) {
                        operation.mBleWriteByBatchCallback.onWriteBatchFailed(
                                BleErrorCodes.CONNECTION_NOT_ESTABLISHED,
                                writtenLen, operation.mData, characteristicUuid, device);
                        tryNextOperation();
                    }
                    return;
                }
                if (operation.mBatchInterval <= 0) {
                    writeData(gattChar, next, operation.mBleWriteCallback);
                } else {
                    Message msg = Message.obtain(mHandler, new Runnable() {
                        @Override
                        public void run() {
                            if (!isConnected()) {
                                if (finishOperation()) {
                                    operation.mBleWriteByBatchCallback.onWriteBatchFailed(
                                            BleErrorCodes.CONNECTION_NOT_ESTABLISHED, writtenLen,
                                            operation.mData, characteristicUuid, device);
                                    tryNextOperation();
                                }
                                return;
                            }
                            writeData(gattChar, next, operation.mBleWriteCallback);
                        }
                    });
                    msg.what = MSG_WHAT_WRITE_BATCH_DELAY;
                    mHandler.sendMessageDelayed(msg, operation.mBatchInterval);
                }
            }

            @Override
            public void onWriteFailed(int errCode, byte[] data, UUID characteristicUuid, BleDevice device) {
                // Failed to sent current pack, so - 1
                int writtenBatchCount = operation.mBatchNum - operation.mBatchQueue.size() - 1;
                int writtenLen = operation.mLengthPerBatch * writtenBatchCount;
                if (finishOperation()) {
                    operation.mBleWriteByBatchCallback.onWriteBatchFailed(errCode, writtenLen,
                            operation.mData, characteristicUuid, device);
                    tryNextOperation();
                }
            }
        };
        operation.mBleWriteCallback = writeCallback;

        byte[] d = operation.mBatchQueue.poll();
        return d != null && writeData(characteristic, d, writeCallback);
    }

    private boolean writeData(BluetoothGattCharacteristic characteristic,
                              byte[] data, BleWriteCallback callback) {
        int minNumPerPack = mCurrentMtu - BleGatt.ATT_OCCUPY_BYTES_NUM;
        if (data.length > minNumPerPack) {
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    callback.onWriteFailed(BleErrorCodes.DATA_LENGTH_GREATER_THAN_MTU, data,
                            characteristic.getUuid(), mDevice);
                }
            });
            return false;
        }
        // Write data
        if (!characteristic.setValue(data) || !mGatt.writeCharacteristic(characteristic)) {
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    callback.onWriteFailed(BleErrorCodes.UNKNOWN, data, characteristic.getUuid(), mDevice);
                }
            });
            return false;
        }
        return true;
    }

    private boolean descriptorWrite(DescriptorWrite operation) {
        UUID serviceUuid = operation.mServiceUuid;
        UUID characteristicUuid = operation.mCharacteristicUuid;
        UUID descriptorUuid = operation.mDescriptorUuid;
        byte[] data = operation.mData;
        BleDescriptorWriteCallback callback = operation.mBleDescriptorWriteCallback;
        // Check conditions
        boolean connected = mGatt != null && isConnected();
        BluetoothGattService service = null;
        BluetoothGattCharacteristic characteristic = null;
        BluetoothGattDescriptor descriptor = null;
        if (connected) {
            service = mGatt.getService(serviceUuid);
        }
        if (service != null) {
            characteristic = service.getCharacteristic(characteristicUuid);
        }
        if (characteristic != null) {
            descriptor = characteristic.getDescriptor(descriptorUuid);
        }
        if (!connected || service == null || characteristic == null || descriptor == null) {
            int code;
            if (!connected) {
                code = BleErrorCodes.CONNECTION_NOT_ESTABLISHED;
            } else if (service == null) {
                code = BleErrorCodes.SERVICE_NOT_FOUND;
            } else if (characteristic == null) {
                code = BleErrorCodes.CHARACTERISTIC_NOT_FOUND_IN_SERVICE;
            } else {
                code = BleErrorCodes.DESCRIPTOR_NOT_FOUND_IN_CHARACTERISTIC;
            }
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    callback.onDescriptorWriteFailed(code, data, descriptorUuid, mDevice);
                }
            });
            return false;
        }
        if (!descriptor.setValue(data) || !mGatt.writeDescriptor(descriptor)) {
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    callback.onDescriptorWriteFailed(BleErrorCodes.UNKNOWN, data, descriptorUuid, mDevice);
                }
            });
            return false;
        }
        return true;
    }

    private boolean descriptorRead(DescriptorRead operation) {
        UUID serviceUuid = operation.mServiceUuid;
        UUID characteristicUuid = operation.mCharacteristicUuid;
        UUID descriptorUuid = operation.mDescriptorUuid;
        BleDescriptorReadCallback callback = operation.mBleDescriptorReadCallback;
        // Check conditions
        boolean connected = mGatt != null && isConnected();
        BluetoothGattService service = null;
        BluetoothGattCharacteristic characteristic = null;
        BluetoothGattDescriptor descriptor = null;
        if (connected) {
            service = mGatt.getService(serviceUuid);
        }
        if (service != null) {
            characteristic = service.getCharacteristic(characteristicUuid);
        }
        if (characteristic != null) {
            descriptor = characteristic.getDescriptor(descriptorUuid);
        }
        if (!connected || service == null || characteristic == null || descriptor == null) {
            int code;
            if (!connected) {
                code = BleErrorCodes.CONNECTION_NOT_ESTABLISHED;
            } else if (service == null) {
                code = BleErrorCodes.SERVICE_NOT_FOUND;
            } else if (characteristic == null) {
                code = BleErrorCodes.CHARACTERISTIC_NOT_FOUND_IN_SERVICE;
            } else {
                code = BleErrorCodes.DESCRIPTOR_NOT_FOUND_IN_CHARACTERISTIC;
            }
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    callback.onDescriptorReadFailed(code, descriptorUuid, mDevice);
                }
            });
            return false;
        }
        if (!mGatt.readDescriptor(descriptor)) {
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    callback.onDescriptorReadFailed(BleErrorCodes.UNKNOWN, descriptorUuid, mDevice);
                }
            });
            return false;
        }
        return true;
    }

    private boolean readRssi(RssiRead operation) {
        BleRssiCallback callback = operation.mBleRssiCallback;
        // Check connection
        if (mGatt == null || !isConnected()) {
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    callback.onRssiFailed(BleErrorCodes.CONNECTION_NOT_ESTABLISHED, mDevice);
                }
            });
            return false;
        }
        if (!mGatt.readRemoteRssi()) {
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    callback.onRssiFailed(BleErrorCodes.UNKNOWN, mDevice);
                }
            });
            return false;
        }
        return true;
    }

    @SuppressWarnings("NewApi")
    private boolean requestMtu(MtuSet operation) {
        int mtu = operation.mMtu;
        BleMtuCallback callback = operation.mBleMtuCallback;
        boolean connected = mGatt != null && isConnected();
        boolean versionSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP;
        if (!connected || !versionSupported) {
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    callback.onMtuFailed(!connected ? BleErrorCodes.CONNECTION_NOT_ESTABLISHED :
                            BleErrorCodes.API_VERSION_NOT_SUPPORTED, mDevice);
                }
            });
            return false;
        }
        if (mtu == mCurrentMtu) {
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    callback.onMtuChanged(mCurrentMtu, mDevice);
                }
            });
            return false;
        }
        if (mtu < BleGatt.MTU_MIN) {
            mtu = BleGatt.MTU_MIN;
        } else if (mtu > BleGatt.MTU_MAX) {
            mtu = BleGatt.MTU_MAX;
        }
        if (!mGatt.requestMtu(mtu)) {
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    callback.onMtuFailed(BleErrorCodes.UNKNOWN, mDevice);
                }
            });
            return false;
        }
        return true;
    }

    private boolean readPhy(PhyRead operation) {
        BlePhyReadCallback callback = operation.mBlePhyReadCallback;
        boolean connected = mGatt != null && isConnected();
        boolean versionSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O;
        if (!connected || !versionSupported) {
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    callback.onPhyReadFailed(!connected ? BleErrorCodes.CONNECTION_NOT_ESTABLISHED :
                            BleErrorCodes.API_VERSION_NOT_SUPPORTED, mDevice);
                }
            });
            return false;
        }
        mGatt.readPhy();
        return true;
    }

    private boolean setPreferencePhy(PhyPreferenceSet operation) {
        int txPhy = operation.mTxPhy;
        int rxPhy = operation.mRxPhy;
        int phyOptions = operation.mPhyOptions;
        BlePhyPreferenceCallback callback = operation.mBlePhyPreferenceCallback;
        boolean connected = mGatt != null && isConnected();
        boolean versionSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O;
        if (!connected || !versionSupported) {
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    callback.onPhyPreferenceSetFailed(!connected ? BleErrorCodes.CONNECTION_NOT_ESTABLISHED :
                            BleErrorCodes.API_VERSION_NOT_SUPPORTED, mDevice);
                }
            });
            return false;
        }
        mGatt.setPreferredPhy(txPhy, rxPhy, phyOptions);
        return true;
    }

    private boolean requestConnectionPriority(ConnectionPrioritySet operation) {
        int connPriority = operation.mConnectionPriority;
        boolean connected = mGatt != null && isConnected();
        boolean versionSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP;
        if (!connected || !versionSupported) {
            return false;
        }
        return mGatt.requestConnectionPriority(connPriority);
    }

    void onBluetoothBondStateChanged(int newState, int previousState) {
        Logger.d("onBluetoothBondStateChanged  --  newState=" + newState + "  previousState=" + previousState);
        // Bonding-process is triggered because of the following reasons:
        // 1.When some special devices connected, trigger bonding-process automatically
        // 2.Read/Write encrypted characteristics or descriptors
        // 3.Call BluetoothDevice#createBond()
        switch (newState) {
            case BluetoothDevice.BOND_NONE:
                if (previousState == BluetoothDevice.BOND_BONDING) {
                    // Failed to bond the device, If triggered by connection, we
                    // try to discover services
                    mHandler.removeMessages(MSG_WHAT_DISCOVER_SERVICES_DELAY);
                    tryToDiscoverServices();
                }
                break;
            case BluetoothDevice.BOND_BONDING:
                synchronized (mOperationLock) {
                    if (!mOperationExecuting) {
                        return;
                    }
                    // Check if current operation may trigger this bonding-process
                    BaseOperation baseOp = mOperationQueue.peek();
                    boolean mayTrigger = baseOp instanceof CanTriggerBonding;
                    if (!mayTrigger) {
                        return;
                    }
                    // Cancel previous timeout msg
                    mHandler.removeMessages(MSG_WHAT_OPERATION_TIMEOUT, baseOp);
                    // Calculate new timeout millis and send this timeout msg
                    long millis = BONDING_TIMEOUT_MILLIS + BleManager.getInstance().getGattOperationTimeout();
                    if (baseOp instanceof WriteBatch) {
                        WriteBatch wb = (WriteBatch) baseOp;
                        millis = BONDING_TIMEOUT_MILLIS +
                                wb.mBatchNum * BleManager.getInstance().getGattOperationTimeout() +
                                (wb.mBatchNum - 1) * wb.mBatchInterval;
                    }
                    Message msg = Message.obtain();
                    msg.what = MSG_WHAT_OPERATION_TIMEOUT;
                    msg.obj = baseOp;
                    mHandler.sendMessageDelayed(msg, millis);
                }
                break;
            case BluetoothDevice.BOND_BONDED:
                // If triggered by connection, now we can try to discover services
                mHandler.removeMessages(MSG_WHAT_DISCOVER_SERVICES_DELAY);
                tryToDiscoverServices();
                break;
        }
    }

    private void tryToDiscoverServices() {
        synchronized (mConnectionLock) {
            if (mConnState == CONNECTED_BUT_SERVICES_NOT_DISCOVERED && mGatt != null &&
                    mGatt.getDevice().getBondState() != BluetoothDevice.BOND_BONDING) {
                Logger.d("Start discovering services");
                mGatt.discoverServices();
                mConnState = CONNECTED_AND_SERVICES_DISCOVERING;
            }
        }
    }

    private void onAutoConnectionDisconnected(BluetoothGatt gatt, int gattStatus) {
        if (!mAutoConnect) {
            return;
        }
        boolean connecting = isConnecting();
        boolean connected = isConnected();
        BleConnectCallback callback = mConnectCallback;
        mHandler.removeMessages(MSG_WHAT_CONNECTION_TIMEOUT);
        mHandler.removeMessages(MSG_WHAT_WRITE_BATCH_DELAY);
        mHandler.removeMessages(MSG_WHAT_OPERATION_TIMEOUT);
        mHandler.removeMessages(MSG_WHAT_DISCOVER_SERVICES_DELAY);
        stopAndClearOperations();
        clearAllCallbacks();
        resetConnParamsAndMtu();
        mConnState = CONNECTING;
        // mAutoConnect has been reset to false in #resetConnParamsAndMtu(), restore it
        mAutoConnect = true;
        // Keep connection callback
        mConnectCallback = callback;
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                if (callback != null) {
                    if (connecting) {
                        // Do nothing
                    } else if (connected) {
                        callback.onDisconnected(mDevice, gattStatus);
                        callback.onConnectionStarted(mDevice);
                    }
                }
            }
        });
    }

    private void onDirectConnectionDisconnected(BluetoothGatt gatt, int gattStatus) {
        if (mAutoConnect) {
            return;
        }
        boolean connecting = isConnecting();
        boolean connected = isConnected();
        BleConnectCallback callback = mConnectCallback;
        mHandler.removeMessages(MSG_WHAT_CONNECTION_TIMEOUT);
        mHandler.removeMessages(MSG_WHAT_WRITE_BATCH_DELAY);
        mHandler.removeMessages(MSG_WHAT_OPERATION_TIMEOUT);
        mHandler.removeMessages(MSG_WHAT_DISCOVER_SERVICES_DELAY);
        // Before clearing params, create ConnectionRetry used to retry connection
        ConnectionRetry connRetry = new ConnectionRetry(mConnTimeout,
                mConnRetryCount, mConnRetryDelay, mAutoConnect, callback);
        refreshDeviceCache();
        gatt.close();
        mGatt = null;
        stopAndClearOperations();
        clearAllCallbacks();
        resetConnParamsAndMtu();
        mConnState = DISCONNECTED;
        if (connecting) {
            boolean retry = connRetry.mRetryCount > 0 && connRetry.mConnectCallback != null;
            if (retry) {
                // During the connection retry wait period, connection state remains
                // CONNECTING, and do not call #onConnectionFailed()
                mConnState = CONNECTING;
                Message msg = Message.obtain();
                msg.what = MSG_WHAT_CONNECTION_RETRY;
                msg.obj = connRetry;
                mHandler.sendMessageDelayed(msg, mConnRetryDelay);
            } else {
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        if (callback != null) {
                            callback.onConnectionFailed(gattStatus, mDevice);
                        }
                    }
                });
            }
        } else if (connected) {
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    if (callback != null) {
                        callback.onDisconnected(mDevice, gattStatus);
                    }
                }
            });
        }
    }

    @Override
    public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
        super.onConnectionStateChange(gatt, status, newState);
        Logger.d("onConnectionStateChange --  status=" + status + "   newState=" + newState);
        String address = gatt.getDevice().getAddress();
        if (mDevice == null || !address.equals(mDevice.getAddress())) {
            return;
        }
        synchronized (mConnectionLock) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                switch (newState) {
                    case BluetoothProfile.STATE_CONNECTED:
                        mConnState = CONNECTED_BUT_SERVICES_NOT_DISCOVERED;
                        int bondState = gatt.getDevice().getBondState();
                        if (bondState == BluetoothDevice.BOND_BONDING) {
                            // After connection established, some remote devices trigger bonding
                            // process automatically, so wait this process complete, once bonded
                            // successfully, we start discovering services
                        } else {
                            // For bonded devices, once connection established, the encryption
                            // will be re-established，so here we delay for a while before
                            // discovering services. Also, if the device has Service Change
                            // Characteristic, this delay is necessary.
                            long delayMillis = bondState == BluetoothDevice.BOND_BONDED ? 800L : 400L;
                            Message msg = Message.obtain();
                            msg.what = MSG_WHAT_DISCOVER_SERVICES_DELAY;
                            mHandler.sendMessageDelayed(msg, delayMillis);
                        }
                        break;
                    case BluetoothProfile.STATE_DISCONNECTED:
                        if (mAutoConnect) {
                            onAutoConnectionDisconnected(gatt, status);
                        } else {
                            onDirectConnectionDisconnected(gatt, status);
                        }
                        break;
                }
                return;
            }
            if (mAutoConnect) {
                if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    onAutoConnectionDisconnected(gatt, status);
                } else {
                    // do nothing
                }
            } else {
                // If not auto-connect and gatt status is not GATT_SUCCESS (regardless
                // of newState), close gatt
                onDirectConnectionDisconnected(gatt, status);
            }
        }
    }

    @Override
    public void onServicesDiscovered(BluetoothGatt gatt, int status) {
        super.onServicesDiscovered(gatt, status);
        Logger.d("onServicesDiscovered");
        String address = gatt.getDevice().getAddress();
        if (mDevice == null || !address.equals(mDevice.getAddress())) {
            return;
        }
        synchronized (mConnectionLock) {
            if (!isConnecting()) {
                return;
            }
            mHandler.removeMessages(MSG_WHAT_CONNECTION_TIMEOUT);
            boolean success = status == BluetoothGatt.GATT_SUCCESS;
            if (success) {
                mConnState = CONNECTED_AND_SERVICES_DISCOVERED;
                BleConnectCallback callback = mConnectCallback;
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        if (callback != null) {
                            callback.onConnected(mDevice);
                        }
                    }
                });
            } else {
                gatt.disconnect();
            }
        }
    }

    @Override
    public void onServiceChanged(BluetoothGatt gatt) {
        super.onServiceChanged(gatt);
        Logger.d("onServiceChanged, now disconnect from the remote device");
        // Now all services and characteristics are invalid, disconnect current connection
        disconnect(true, true);
    }

    @Override
    public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
        super.onCharacteristicRead(gatt, characteristic, status);
        String address = gatt.getDevice().getAddress();
        if (mDevice == null || !address.equals(mDevice.getAddress())) {
            return;
        }
        UUID serviceUuid = characteristic.getService().getUuid();
        UUID charUuid = characteristic.getUuid();
        boolean success = status == BluetoothGatt.GATT_SUCCESS;
        byte[] data = characteristic.getValue();

        synchronized (mOperationLock) {
            if (!mOperationExecuting) {
                return;
            }
            BaseOperation baseOperation = mOperationQueue.peek();
            if (baseOperation instanceof Read) {
                Read operation = (Read) baseOperation;
                if (!operation.mServiceUuid.equals(serviceUuid) || !operation.mReadUuid.equals(charUuid)) {
                    return;
                }
                mHandler.removeMessages(MSG_WHAT_OPERATION_TIMEOUT, operation);
                mOperationQueue.poll();
                mOperationExecuting = false;
                BleReadCallback callback = operation.mBleReadCallback;
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        if (success) {
                            callback.onReadSuccess(data, charUuid, mDevice);
                        } else {
                            callback.onReadFailed(status, charUuid, mDevice);
                        }
                        tryNextOperation();
                    }
                });
            }
        }
    }

    @Override
    public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
        super.onCharacteristicWrite(gatt, characteristic, status);
        String address = gatt.getDevice().getAddress();
        if (mDevice == null || !address.equals(mDevice.getAddress())) {
            return;
        }
        UUID serviceUuid = characteristic.getService().getUuid();
        UUID charUuid = characteristic.getUuid();
        boolean success = status == BluetoothGatt.GATT_SUCCESS;
        byte[] data = characteristic.getValue();
        synchronized (mOperationLock) {
            if (!mOperationExecuting) {
                return;
            }
            BaseOperation baseOperation = mOperationQueue.peek();
            if (baseOperation instanceof BaseWriteOperation) {
                BaseWriteOperation operation = (BaseWriteOperation) baseOperation;
                if (!operation.mServiceUuid.equals(serviceUuid) || !operation.mWriteUuid.equals(charUuid)) {
                    return;
                }
                if (baseOperation instanceof Write) { // just write once
                    // Current operation finished, so remove it from the queue
                    mHandler.removeMessages(MSG_WHAT_OPERATION_TIMEOUT, operation);
                    mOperationQueue.poll();
                    mOperationExecuting = false;
                } else if (baseOperation instanceof WriteBatch) { // write multiple times
                    // Do nothing
                }
                BleWriteCallback callback = operation.getBleWriteCallback();
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        if (success) {
                            callback.onWriteSuccess(data, charUuid, mDevice);
                        } else {
                            callback.onWriteFailed(status, data, charUuid, mDevice);
                        }
                        if (operation instanceof Write) {
                            tryNextOperation();
                        }
                    }
                });
            }
        }
    }

    @Override
    public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
        super.onCharacteristicChanged(gatt, characteristic);
        String address = gatt.getDevice().getAddress();
        if (mDevice == null || !address.equals(mDevice.getAddress())) {
            return;
        }
        UUID serviceUuid = characteristic.getService().getUuid();
        UUID charUuid = characteristic.getUuid();
        OperationIdentify identify = getOperationIdentify(mNotifyCallbackMap, serviceUuid, charUuid);
        if (identify == null) {
            return;
        }
        BleNotifyCallback callback = mNotifyCallbackMap.get(identify);
        byte[] data = characteristic.getValue();
        if (callback != null) {
            callback.onCharacteristicChanged(data, charUuid, mDevice);
        }
    }

    @Override
    public void onDescriptorRead(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
        super.onDescriptorRead(gatt, descriptor, status);
        String address = gatt.getDevice().getAddress();
        if (mDevice == null || !address.equals(mDevice.getAddress())) {
            return;
        }
        UUID serviceUuid = descriptor.getCharacteristic().getService().getUuid();
        UUID charUuid = descriptor.getCharacteristic().getUuid();
        UUID desUuid = descriptor.getUuid();
        byte[] data = descriptor.getValue();
        boolean success = status == BluetoothGatt.GATT_SUCCESS;

        synchronized (mOperationLock) {
            if (!mOperationExecuting) {
                return;
            }
            BaseOperation baseOperation = mOperationQueue.peek();
            if (baseOperation instanceof DescriptorRead) {
                DescriptorRead operation = (DescriptorRead) baseOperation;
                if (!operation.mServiceUuid.equals(serviceUuid) ||
                        !operation.mCharacteristicUuid.equals(charUuid) ||
                        !operation.mDescriptorUuid.equals(desUuid)) {
                    return;
                }
                mHandler.removeMessages(MSG_WHAT_OPERATION_TIMEOUT, operation);
                mOperationQueue.poll();
                mOperationExecuting = false;
                BleDescriptorReadCallback callback = operation.mBleDescriptorReadCallback;
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        if (success) {
                            callback.onDescriptorReadSuccess(data, desUuid, mDevice);
                        } else {
                            callback.onDescriptorReadFailed(status, desUuid, mDevice);
                        }
                        tryNextOperation();
                    }
                });
            }
        }
    }

    @Override
    public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
        super.onDescriptorWrite(gatt, descriptor, status);
        String address = gatt.getDevice().getAddress();
        if (mDevice == null || !address.equals(mDevice.getAddress())) {
            return;
        }
        UUID serviceUuid = descriptor.getCharacteristic().getService().getUuid();
        UUID charUuid = descriptor.getCharacteristic().getUuid();
        UUID desUuid = descriptor.getUuid();
        byte[] data = descriptor.getValue();
        boolean success = status == BluetoothGatt.GATT_SUCCESS;

        synchronized (mOperationLock) {
            if (!mOperationExecuting) {
                return;
            }
            BaseOperation baseOperation = mOperationQueue.peek();
            if (baseOperation instanceof DescriptorWrite) {
                DescriptorWrite operation = (DescriptorWrite) baseOperation;
                if (!operation.mServiceUuid.equals(serviceUuid) ||
                        !operation.mCharacteristicUuid.equals(charUuid) ||
                        !operation.mDescriptorUuid.equals(desUuid)) {
                    return;
                }
                mHandler.removeMessages(MSG_WHAT_OPERATION_TIMEOUT, operation);
                mOperationQueue.poll();
                mOperationExecuting = false;
                BleDescriptorWriteCallback callback = operation.mBleDescriptorWriteCallback;
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        if (success) {
                            callback.onDescriptorWriteSuccess(data, desUuid, mDevice);
                        } else {
                            callback.onDescriptorWriteFailed(status, data, desUuid, mDevice);
                        }
                        tryNextOperation();
                    }
                });
            } else if (baseOperation instanceof Notify) {
                Notify operation = (Notify) baseOperation;
                if (!operation.mServiceUuid.equals(serviceUuid) ||
                        !operation.mNotifyUuid.equals(charUuid) ||
                        !descriptor.getUuid().equals(UUID.fromString(BleGatt.CHARACTERISTIC_CONFIG_DESCRIPTOR_UUID))) {
                    return;
                }
                boolean isEnabledValue = Arrays.equals(descriptor.getValue(), BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE) ||
                        Arrays.equals(descriptor.getValue(), BluetoothGattDescriptor.ENABLE_INDICATION_VALUE);
                boolean isDisabledValue = Arrays.equals(descriptor.getValue(), BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE);
                if (!isEnabledValue && !isDisabledValue) {
                    return;
                }
                OperationIdentify identify = getOperationIdentify(mNotifyCallbackMap, serviceUuid, charUuid);
                if (operation.mEnable) {
                    if (!isEnabledValue) {
                        return;
                    }
                    if (identify == null) {
                        identify = new OperationIdentify(serviceUuid, charUuid);
                    }
                    mNotifyCallbackMap.put(identify, operation.mBleNotifyCallback);
                } else {
                    if (!isDisabledValue) {
                        return;
                    }
                    if (identify != null) {
                        mNotifyCallbackMap.remove(identify);
                    }
                }
                mHandler.removeMessages(MSG_WHAT_OPERATION_TIMEOUT, operation);
                mOperationQueue.poll();
                mOperationExecuting = false;
                final BleNotifyCallback callback = operation.mBleNotifyCallback;
                final boolean enabled = operation.mEnable;
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        if (enabled && callback != null) {
                            if (success) {
                                callback.onNotifySuccess(charUuid, mDevice);
                            } else {
                                callback.onNotifyFailed(status, charUuid, mDevice);
                            }
                        }
                        tryNextOperation();
                    }
                });
            }
        }
    }

    @Override
    public void onReadRemoteRssi(BluetoothGatt gatt, final int rssi, int status) {
        super.onReadRemoteRssi(gatt, rssi, status);
        String address = gatt.getDevice().getAddress();
        if (mDevice == null || !address.equals(mDevice.getAddress())) {
            return;
        }
        synchronized (mOperationLock) {
            if (!mOperationExecuting) {
                return;
            }
            BaseOperation baseOperation = mOperationQueue.peek();
            if (baseOperation instanceof RssiRead) {
                RssiRead operation = (RssiRead) baseOperation;
                mHandler.removeMessages(MSG_WHAT_OPERATION_TIMEOUT, operation);
                mOperationQueue.poll();
                mOperationExecuting = false;
                BleRssiCallback callback = operation.mBleRssiCallback;
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        if (status == BluetoothGatt.GATT_SUCCESS) {
                            callback.onRssiSuccess(rssi, mDevice);
                        } else {
                            callback.onRssiFailed(status, mDevice);
                        }
                        tryNextOperation();
                    }
                });
            }
        }
    }

    @Override
    public void onMtuChanged(BluetoothGatt gatt, final int mtu, int status) {
        super.onMtuChanged(gatt, mtu, status);
        String address = gatt.getDevice().getAddress();
        if (mDevice == null || !address.equals(mDevice.getAddress())) {
            return;
        }
        boolean success = status == BluetoothGatt.GATT_SUCCESS;
        int newMtu = Math.min(mtu, BleGatt.MTU_MAX);
        if (success) {
            mCurrentMtu = newMtu;
        }
        synchronized (mOperationLock) {
            if (!mOperationExecuting) {
                return;
            }
            BaseOperation baseOperation = mOperationQueue.peek();
            if (baseOperation instanceof MtuSet) {
                MtuSet operation = (MtuSet) baseOperation;
                mHandler.removeMessages(MSG_WHAT_OPERATION_TIMEOUT, operation);
                mOperationQueue.poll();
                mOperationExecuting = false;
                BleMtuCallback callback = operation.mBleMtuCallback;
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        if (success) {
                            callback.onMtuChanged(newMtu, mDevice);
                        } else {
                            callback.onMtuFailed(status, mDevice);
                        }
                        tryNextOperation();
                    }
                });
            }
        }
    }

    @Override
    public void onPhyUpdate(BluetoothGatt gatt, int txPhy, int rxPhy, int status) {
        super.onPhyUpdate(gatt, txPhy, rxPhy, status);
        String address = gatt.getDevice().getAddress();
        if (mDevice == null || !address.equals(mDevice.getAddress())) {
            return;
        }
        boolean success = status == BluetoothGatt.GATT_SUCCESS;

        // If the remote device updates the PHY, onPhyUpdate() is also triggered,
        // in this case, we should send notification that PHY changed, but before
        // doing this, make sure that current onPhyUpdate() is not triggered by
        // calling BluetoothGatt#setPhyPreference()
        if (success && mPhyPreferenceCallback != null && !(mOperationQueue.peek() instanceof PhyPreferenceSet)) {
            BlePhyPreferenceCallback callback = mPhyPreferenceCallback;
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    callback.onPhyChanged(txPhy, rxPhy, mDevice);
                }
            });
        }

        synchronized (mOperationLock) {
            if (!mOperationExecuting) {
                return;
            }
            BaseOperation baseOperation = mOperationQueue.peek();
            if (baseOperation instanceof PhyPreferenceSet) {
                PhyPreferenceSet operation = (PhyPreferenceSet) baseOperation;
                mHandler.removeMessages(MSG_WHAT_OPERATION_TIMEOUT, operation);
                mOperationQueue.poll();
                mOperationExecuting = false;
                mPhyPreferenceCallback = operation.mBlePhyPreferenceCallback;
                BlePhyPreferenceCallback callback = operation.mBlePhyPreferenceCallback;
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        if (success) {
                            callback.onPhyChanged(txPhy, rxPhy, mDevice);
                        } else {
                            callback.onPhyPreferenceSetFailed(status, mDevice);
                        }
                        tryNextOperation();
                    }
                });
            }
        }
    }

    @Override
    public void onPhyRead(BluetoothGatt gatt, int txPhy, int rxPhy, int status) {
        super.onPhyRead(gatt, txPhy, rxPhy, status);
        String address = gatt.getDevice().getAddress();
        if (mDevice == null || !address.equals(mDevice.getAddress())) {
            return;
        }
        boolean success = status == BluetoothGatt.GATT_SUCCESS;
        synchronized (mOperationLock) {
            if (!mOperationExecuting) {
                return;
            }
            BaseOperation baseOperation = mOperationQueue.peek();
            if (baseOperation instanceof PhyRead) {
                PhyRead operation = (PhyRead) baseOperation;
                mHandler.removeMessages(MSG_WHAT_OPERATION_TIMEOUT, operation);
                mOperationQueue.poll();
                mOperationExecuting = false;
                BlePhyReadCallback callback = operation.mBlePhyReadCallback;
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        if (success) {
                            callback.onPhyReadSuccess(txPhy, rxPhy, mDevice);
                        } else {
                            callback.onPhyReadFailed(status, mDevice);
                        }
                        tryNextOperation();
                    }
                });
            }
        }
    }

    private void stopAndClearOperations() {
        synchronized (mOperationLock) {
            mOperationQueue.clear();
            mOperationExecuting = false;
        }
    }

    private void clearAllCallbacks() {
        mNotifyCallbackMap.clear();
        mConnectCallback = null;
        mPhyPreferenceCallback = null;
    }

    private void resetConnParamsAndMtu() {
        mAutoConnect = false;
        mConnRetryCount = 0;
        mConnRetryDelay = 0;
        mConnTimeout = DEFAULT_CONNECTION_TIMEOUT_MILLIS;
        mCurrentMtu = BleGatt.MTU_MIN;
    }

    private <T> OperationIdentify getOperationIdentify(Map<OperationIdentify, T> map,
                                                       UUID serviceUuid, UUID characteristicUuid) {
        for (OperationIdentify ui : map.keySet()) {
            if (ui.mServiceUuid.equals(serviceUuid) && ui.mCharacteristicUuid.equals(characteristicUuid)) {
                return ui;
            }
        }
        return null;
    }

    /**
     * Clears the ble device's internal cache and forces a refresh of the services from the
     * ble device.
     */
    private boolean refreshDeviceCache() {
        if (mGatt == null) {
            return false;
        }
        try {
            Method refresh = BluetoothGatt.class.getMethod("refresh");
            return refresh != null && (boolean) refresh.invoke(mGatt);
        } catch (Exception e) {
            Logger.i("encounter an exception while refreshing device cache: " + e.getMessage());
            return false;
        }
    }

    private static final class OperationIdentify {
        UUID mServiceUuid;
        UUID mCharacteristicUuid;

        OperationIdentify(UUID serviceUuid, UUID characteristicUuid) {
            this.mServiceUuid = serviceUuid;
            this.mCharacteristicUuid = characteristicUuid;
        }
    }

    private static final class ConnectionRetry {
        final long mConnectionTimeout;
        int mRetryCount;
        final long mRetryDelay;
        final boolean mAutoConnect;
        final BleConnectCallback mConnectCallback;

        public ConnectionRetry(long connectionTimeoutMillis, int retryCount, long retryDelay,
                               boolean autoConnect, BleConnectCallback callback) {
            this.mConnectionTimeout = connectionTimeoutMillis;
            this.mRetryCount = retryCount;
            this.mRetryDelay = retryDelay;
            this.mAutoConnect = autoConnect;
            this.mConnectCallback = callback;
        }
    }

    private interface CanTriggerBonding {

    }

    private static abstract class BaseOperation {

    }

    private static abstract class BaseWriteOperation extends BaseOperation implements CanTriggerBonding {
        final UUID mServiceUuid;
        final UUID mWriteUuid;
        final byte[] mData;

        BaseWriteOperation(UUID serviceUuid, UUID writeUuid, byte[] data) {
            this.mServiceUuid = serviceUuid;
            this.mWriteUuid = writeUuid;
            this.mData = data;
        }

        abstract BleWriteCallback getBleWriteCallback();
    }

    private static class ConnectionPrioritySet extends BaseOperation {
        final int mConnectionPriority;
        final BleConnectionPriorityCallback mBleConnectionPriorityCallback;

        ConnectionPrioritySet(int connPriority, BleConnectionPriorityCallback callback) {
            this.mConnectionPriority = connPriority;
            this.mBleConnectionPriorityCallback = callback;
        }
    }

    private static class DescriptorRead extends BaseOperation implements CanTriggerBonding {
        final UUID mServiceUuid;
        final UUID mCharacteristicUuid;
        final UUID mDescriptorUuid;
        final BleDescriptorReadCallback mBleDescriptorReadCallback;

        DescriptorRead(UUID serviceUuid, UUID characteristicUuid, UUID descriptorUuid, BleDescriptorReadCallback callback) {
            this.mServiceUuid = serviceUuid;
            this.mCharacteristicUuid = characteristicUuid;
            this.mDescriptorUuid = descriptorUuid;
            this.mBleDescriptorReadCallback = callback;
        }
    }

    private static class DescriptorWrite extends BaseOperation implements CanTriggerBonding {
        final UUID mServiceUuid;
        final UUID mCharacteristicUuid;
        final UUID mDescriptorUuid;
        final byte[] mData;
        final BleDescriptorWriteCallback mBleDescriptorWriteCallback;

        DescriptorWrite(UUID serviceUuid, UUID characteristicUuid, UUID descriptorUuid,
                        byte[] data, BleDescriptorWriteCallback callback) {
            this.mServiceUuid = serviceUuid;
            this.mCharacteristicUuid = characteristicUuid;
            this.mDescriptorUuid = descriptorUuid;
            this.mData = data;
            this.mBleDescriptorWriteCallback = callback;
        }
    }

    private static class MtuSet extends BaseOperation {
        final int mMtu;
        final BleMtuCallback mBleMtuCallback;

        MtuSet(int mtu, BleMtuCallback callback) {
            this.mMtu = mtu;
            this.mBleMtuCallback = callback;
        }
    }

    private static class Notify extends BaseOperation implements CanTriggerBonding {
        final UUID mServiceUuid;
        final UUID mNotifyUuid;
        final boolean mEnable;
        final BleNotifyCallback mBleNotifyCallback;

        Notify(UUID serviceUuid, UUID notifyUuid, boolean enable, BleNotifyCallback callback) {
            this.mServiceUuid = serviceUuid;
            this.mNotifyUuid = notifyUuid;
            this.mEnable = enable;
            this.mBleNotifyCallback = callback;
        }
    }

    private static class PhyPreferenceSet extends BaseOperation {
        final int mTxPhy;
        final int mRxPhy;
        final int mPhyOptions;
        final BlePhyPreferenceCallback mBlePhyPreferenceCallback;

        PhyPreferenceSet(int txPhy, int rxPhy, int phyOptions, BlePhyPreferenceCallback callback) {
            this.mTxPhy = txPhy;
            this.mRxPhy = rxPhy;
            this.mPhyOptions = phyOptions;
            this.mBlePhyPreferenceCallback = callback;
        }
    }

    private static class PhyRead extends BaseOperation {
        final BlePhyReadCallback mBlePhyReadCallback;

        PhyRead(BlePhyReadCallback callback) {
            this.mBlePhyReadCallback = callback;
        }
    }

    private static class Read extends BaseOperation implements CanTriggerBonding {
        final UUID mServiceUuid;
        final UUID mReadUuid;
        final BleReadCallback mBleReadCallback;

        Read(UUID serviceUuid, UUID readUuid, BleReadCallback callback) {
            this.mServiceUuid = serviceUuid;
            this.mReadUuid = readUuid;
            this.mBleReadCallback = callback;
        }
    }

    private static class RssiRead extends BaseOperation {
        final BleRssiCallback mBleRssiCallback;

        RssiRead(BleRssiCallback callback) {
            this.mBleRssiCallback = callback;
        }
    }

    private static class Write extends BaseWriteOperation {
        final BleWriteCallback mBleWriteCallback;

        Write(UUID serviceUuid, UUID writeUuid, byte[] data, BleWriteCallback callback) {
            super(serviceUuid, writeUuid, data);
            this.mBleWriteCallback = callback;
        }

        @Override
        BleWriteCallback getBleWriteCallback() {
            return mBleWriteCallback;
        }
    }

    private static class WriteBatch extends BaseWriteOperation {
        final int mLengthPerBatch;
        final long mBatchInterval;
        final Queue<byte[]> mBatchQueue;
        final int mBatchNum;
        final BleWriteByBatchCallback mBleWriteByBatchCallback;
        BleWriteCallback mBleWriteCallback;

        WriteBatch(UUID serviceUuid, UUID writeUuid, byte[] data, int lengthPerBatch,
                   long batchInterval, Queue<byte[]> batchQueue, BleWriteByBatchCallback callback) {
            super(serviceUuid, writeUuid, data);
            if (batchQueue == null) {
                throw new IllegalArgumentException("BatchQueue is null");
            }
            this.mLengthPerBatch = lengthPerBatch;
            this.mBatchInterval = batchInterval;
            this.mBatchQueue = batchQueue;
            this.mBatchNum = batchQueue.size();
            this.mBleWriteByBatchCallback = callback;
        }

        @Override
        BleWriteCallback getBleWriteCallback() {
            return mBleWriteCallback;
        }
    }
}