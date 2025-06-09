package com.ficat.easyble.gatt;

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
import com.ficat.easyble.gatt.callback.BleMtuCallback;
import com.ficat.easyble.gatt.callback.BleNotifyCallback;
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

public final class BleGattCommunicator extends BluetoothGattCallback {
    /**
     * Connection state constants
     */
    private static final int DISCONNECTED = 1;
    private static final int CONNECTING = 2;
    private static final int CONNECTED = 3;

    private static final int DEFAULT_TIME_OUT_MILLS = 10000;//default 10s

    private final Handler mHandler;
    private BleDevice mDevice;
    private BleConnectCallback mConnectCallback;
    private BleMtuCallback mMtuCallback;
    private BleRssiCallback mRssiCallback;
    private final Map<OperationIdentify, BleNotifyCallback> mNotifyCallbackMap;
    private final Map<OperationIdentify, BleReadCallback> mReadCallbackMap;
    private final Map<OperationIdentify, BleWriteCallback> mWriteCallbackMap;
    private BluetoothGatt mGatt;
    private volatile int mConnState = DISCONNECTED;
    private volatile int mCurrentMtu = BleGatt.MTU_MIN;
    private final Object mKeyConnection = new Object();

    BleGattCommunicator(BleDevice device) {
        if (device == null) {
            throw new IllegalArgumentException("BleDevice is null");
        }
        this.mHandler = new Handler(Looper.getMainLooper());
        this.mDevice = device;
        this.mNotifyCallbackMap = new ConcurrentHashMap<>();
        this.mReadCallbackMap = new ConcurrentHashMap<>();
        this.mWriteCallbackMap = new ConcurrentHashMap<>();
    }

    boolean isConnected() {
        return mConnState == CONNECTED;
    }

    boolean isConnecting() {
        return mConnState == CONNECTING;
    }

    int getConnectionState() {
        return this.mConnState;
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

    void connectGatt(int timeoutMills, BleConnectCallback callback) {
        synchronized (mKeyConnection) {
            if (isConnecting() || isConnected()) {
                mConnectCallback = callback;
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        if (isConnecting()) {
                            callback.onConnectionStarted(mDevice);
                        } else {
                            callback.onConnected(mDevice);
                        }
                    }
                });
                return;
            }
            // Connect to GATT
            BluetoothGatt gatt;
            Context context = BleManager.getInstance().getContext();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && mDevice.getBluetoothDevice().getType() == BluetoothDevice.DEVICE_TYPE_DUAL) {
                gatt = mDevice.getBluetoothDevice().connectGatt(context, false,
                        BleGattCommunicator.this, BluetoothDevice.TRANSPORT_LE);
            } else {
                gatt = mDevice.getBluetoothDevice().connectGatt(context, false,
                        BleGattCommunicator.this);
            }
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
            mGatt = gatt;
            mConnectCallback = callback;
            mConnState = CONNECTING;
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    mConnectCallback.onConnectionStarted(mDevice);
                }
            });
            // Send connection timeout msg
            Message msg = Message.obtain(mHandler, new Runnable() {
                @Override
                public void run() {
                    if (mGatt != null) {
                        mGatt.disconnect();
                        refreshDeviceCache();
                        mGatt.close();
                    }
                    mConnState = DISCONNECTED;
                    mConnectCallback.onConnectionFailed(BleErrorCodes.CONNECTION_TIMEOUT, mDevice);
                    clearAndResetAll();
                }
            });
            msg.obj = mDevice.getAddress();
            mHandler.sendMessageDelayed(msg, timeoutMills > 0 ? timeoutMills : DEFAULT_TIME_OUT_MILLS);
        }
    }

    void disconnect() {
        synchronized (mKeyConnection) {
            if (mConnState == DISCONNECTED || mGatt == null) {
                return;
            }
            mGatt.disconnect();
            if (isConnecting()) {
                // Remove connection timeout message if connection is establishing
                mHandler.removeCallbacksAndMessages(mDevice.getAddress());
                refreshDeviceCache();
                mGatt.close();
                mConnState = DISCONNECTED;
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        if (mConnectCallback != null) {
                            mConnectCallback.onConnectionFailed(BleErrorCodes.CONNECTION_CANCELED, mDevice);
                        }
                    }
                });
                clearAndResetAll();
            }
        }
    }

    void enableNotify(UUID serviceUuid, UUID notifyUuid, BleNotifyCallback callback) {
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
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    callback.onNotifyFailed(code, notifyUuid, mDevice);
                }
            });
            return;
        }

        // Add callback to communicator
        OperationIdentify identify = getOperationIdentify(mNotifyCallbackMap, serviceUuid, notifyUuid);
        if (identify == null) {
            identify = new OperationIdentify(serviceUuid, notifyUuid);
        }
        mNotifyCallbackMap.put(identify, callback);
        // Enable notification
        if (mGatt.setCharacteristicNotification(characteristic, true)) {
            BluetoothGattDescriptor descriptor = characteristic.getDescriptor(UUID.fromString(BleGatt.CHARACTERISTIC_CONFIG));
            if (descriptor == null) {
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        callback.onNotifySuccess(notifyUuid, mDevice);
                    }
                });
                return;
            }
            if (BluetoothGattUtils.isCharacteristicNotifiable(characteristic)) {
                descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
            }
            if (BluetoothGattUtils.isCharacteristicIndicative(characteristic)) {
                descriptor.setValue(BluetoothGattDescriptor.ENABLE_INDICATION_VALUE);
            }
            // If the write operation was initiated successfully, wait operation
            // result until BleGattCommunicator#onDescriptorWrite() is called back
            if (mGatt.writeDescriptor(descriptor)) {
                return;
            }
        }
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                callback.onNotifyFailed(BleErrorCodes.UNKNOWN, notifyUuid, mDevice);
            }
        });
        mNotifyCallbackMap.remove(identify);
    }

    void disableNotify(UUID serviceUuid, UUID notifyUuid) {
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
            return;
        }
        OperationIdentify identify = getOperationIdentify(mNotifyCallbackMap, serviceUuid, notifyUuid);
        if (identify != null) {
            mNotifyCallbackMap.remove(identify);
        }

        if (mGatt.setCharacteristicNotification(characteristic, false)) {
            BluetoothGattDescriptor descriptor = characteristic.getDescriptor(UUID.fromString(BleGatt.CHARACTERISTIC_CONFIG));
            if (descriptor == null) {
                return;
            }
            descriptor.setValue(BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE);
            mGatt.writeDescriptor(descriptor);
        }
    }

    void read(UUID serviceUuid, UUID readUuid, BleReadCallback callback) {
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
            return;
        }

        // Add callback to communicator
        OperationIdentify identify = getOperationIdentify(mReadCallbackMap, serviceUuid, readUuid);
        if (identify == null) {
            identify = new OperationIdentify(serviceUuid, readUuid);
        }
        mReadCallbackMap.put(identify, callback);
        // Read data from characteristic
        if (!mGatt.readCharacteristic(characteristic)) {
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    callback.onReadFailed(BleErrorCodes.UNKNOWN, readUuid, mDevice);
                }
            });
            mReadCallbackMap.remove(identify);
        }
    }

    void write(UUID serviceUuid, UUID writeUuid, byte[] data, BleWriteCallback callback) {
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
            return;
        }
        writeData(service, characteristic, data, callback);
    }


    void writeByBatch(UUID serviceUuid, UUID writeUuid, byte[] writeData, int lengthPerPackage,
                      long writeDelay, BleWriteByBatchCallback callback) {
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
            return;
        }

        // Create a data queue and write it in sequence
        final Queue<byte[]> queue = BleDataUtils.getBatchData(writeData, lengthPerPackage);
        if (queue.size() <= 0) return;
        BluetoothGattService gattService = service;
        BluetoothGattCharacteristic gattChar = characteristic;
        int totalNum = queue.size();
        BleWriteCallback writeCallback = new BleWriteCallback() {
            @Override
            public void onWriteSuccess(byte[] data, UUID characteristicUuid, BleDevice device) {
                // Notify current progress
                int writtenBatchCount = totalNum - queue.size();
                // The length of the last batch may be not equal 'lengthPerPackage'
                int writtenLen = (writtenBatchCount - 1) * lengthPerPackage + data.length;
                float progress = writtenLen / (float) writeData.length;
                callback.onWriteBatchProgress(progress, characteristicUuid, device);
                final BleWriteCallback c = this;
                final byte[] next = queue.poll();
                // All batches have been written
                if (next == null) {
                    callback.onWriteBatchSuccess(writeData, characteristicUuid, device);
                    return;
                }
                // Write the next batch
                if (writeDelay <= 0) {
                    writeData(gattService, gattChar, next, c);
                } else {
                    mHandler.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            writeData(gattService, gattChar, next, c);
                        }
                    }, writeDelay);
                }
            }

            @Override
            public void onWriteFailed(int errCode, byte[] data, UUID characteristicUuid, BleDevice device) {
                // Failed to sent current pack, so - 1
                int writtenBatchCount = totalNum - queue.size() - 1;
                int writtenLen = lengthPerPackage * writtenBatchCount;
                callback.onWriteBatchFailed(errCode, writtenLen, writeData, characteristicUuid, device);
            }
        };
        byte[] d = queue.poll();
        if (d != null) {
            writeData(service, characteristic, d, writeCallback);
        }
    }

    private void writeData(BluetoothGattService service, BluetoothGattCharacteristic characteristic,
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
            return;
        }
        UUID serviceUuid = service.getUuid();
        UUID charUuid = characteristic.getUuid();
        // Add callback to communicator
        OperationIdentify identify = getOperationIdentify(mWriteCallbackMap, serviceUuid, charUuid);
        if (identify == null) {
            identify = new OperationIdentify(serviceUuid, charUuid);
        }
        mWriteCallbackMap.put(identify, callback);
        // Write data
        if (!characteristic.setValue(data) || !mGatt.writeCharacteristic(characteristic)) {
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    callback.onWriteFailed(BleErrorCodes.UNKNOWN, data, characteristic.getUuid(), mDevice);
                }
            });
            mWriteCallbackMap.remove(identify);
        }
    }

    void readRssi(BleRssiCallback callback) {
        // Check connection
        if (mGatt == null || !isConnected()) {
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    callback.onRssiFailed(BleErrorCodes.CONNECTION_NOT_ESTABLISHED, mDevice);
                }
            });
            return;
        }
        mRssiCallback = callback;
        if (!mGatt.readRemoteRssi()) {
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    callback.onRssiFailed(BleErrorCodes.UNKNOWN, mDevice);
                }
            });
            mRssiCallback = null;
        }
    }

    @SuppressWarnings("NewApi")
    void requestMtu(int mtu, BleMtuCallback callback) {
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
            return;
        }
        if (mtu == mCurrentMtu) {
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    callback.onMtuChanged(mCurrentMtu, mDevice);
                }
            });
            return;
        }
        mMtuCallback = callback;
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
            mMtuCallback = null;
        }
    }

    @Override
    public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
        super.onConnectionStateChange(gatt, status, newState);
        String address = gatt.getDevice().getAddress();
        if (mDevice == null || !address.equals(mDevice.getAddress())) {
            return;
        }
        synchronized (mKeyConnection) {
            BleConnectCallback callback = mConnectCallback;
            if (status == BluetoothGatt.GATT_SUCCESS) {
                switch (newState) {
                    case BluetoothProfile.STATE_CONNECTED:
                        // We consider the connection successful only after the service is found
                        gatt.discoverServices();
                        break;
                    case BluetoothProfile.STATE_DISCONNECTED:
                        refreshDeviceCache();
                        gatt.close();
                        int previousState = mConnState;
                        mConnState = DISCONNECTED;
                        runOrQueueCallback(new Runnable() {
                            @Override
                            public void run() {
                                if (callback != null && previousState != DISCONNECTED) {
                                    //The connection has been disconnected
                                    callback.onDisconnected(mDevice, status);
                                }
                            }
                        });
                        clearAndResetAll();
                        break;
                }
                return;
            }
            refreshDeviceCache();
            gatt.close();
            boolean connectFailed = isConnecting();
            boolean disconnectedAbnormally = isConnected() && newState == BluetoothProfile.STATE_DISCONNECTED;
            if (connectFailed) {
                mHandler.removeCallbacksAndMessages(address);
            }
            mConnState = DISCONNECTED;
            runOrQueueCallback(new Runnable() {
                @Override
                public void run() {
                    if (callback != null) {
                        if (connectFailed) {
                            callback.onConnectionFailed(BleErrorCodes.UNKNOWN, mDevice);
                        } else if (disconnectedAbnormally) {
                            callback.onDisconnected(mDevice, status);
                        }
                    }
                }
            });
            clearAndResetAll();
        }
    }

    @Override
    public void onServicesDiscovered(BluetoothGatt gatt, int status) {
        super.onServicesDiscovered(gatt, status);
        String address = gatt.getDevice().getAddress();
        if (mDevice == null || !address.equals(mDevice.getAddress())) {
            return;
        }
        synchronized (mKeyConnection) {
            // Remove connection timeout message
            mHandler.removeCallbacksAndMessages(address);
            boolean success = status == BluetoothGatt.GATT_SUCCESS;
            // If connecting, notify connection state changed, otherwise return directly
            if (!isConnecting()) {
                return;
            }
            if (success) {
                mConnState = CONNECTED;
            } else {
                // Disconnect current connection and do not call back BleConnectCallback#onDsiconnected()
                mConnState = DISCONNECTED;
                gatt.disconnect();
            }
            runOrQueueCallback(new Runnable() {
                @Override
                public void run() {
                    if (mConnectCallback == null) {
                        return;
                    }
                    if (success) {
                        mConnectCallback.onConnected(mDevice);
                    } else {
                        mConnectCallback.onConnectionFailed(BleErrorCodes.UNKNOWN, mDevice);
                    }
                }
            });
        }
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
        OperationIdentify identify = getOperationIdentify(mReadCallbackMap, serviceUuid, charUuid);
        if (identify == null) {
            return;
        }
        BleReadCallback callback = mReadCallbackMap.get(identify);
        boolean success = status == BluetoothGatt.GATT_SUCCESS;
        byte[] data = characteristic.getValue();
        runOrQueueCallback(new Runnable() {
            @Override
            public void run() {
                if (callback == null) {
                    return;
                }
                if (success) {
                    callback.onReadSuccess(data, charUuid, mDevice);
                } else {
                    callback.onReadFailed(BleErrorCodes.UNKNOWN, charUuid, mDevice);
                }
                mReadCallbackMap.remove(identify);
            }
        });
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
        OperationIdentify identify = getOperationIdentify(mWriteCallbackMap, serviceUuid, charUuid);
        if (identify == null) {
            return;
        }
        BleWriteCallback callback = mWriteCallbackMap.get(identify);
        boolean success = status == BluetoothGatt.GATT_SUCCESS;
        byte[] data = characteristic.getValue();
        runOrQueueCallback(new Runnable() {
            @Override
            public void run() {
                if (callback == null) {
                    return;
                }
                if (success) {
                    callback.onWriteSuccess(data, charUuid, mDevice);
                } else {
                    callback.onWriteFailed(BleErrorCodes.UNKNOWN, data, charUuid, mDevice);
                }
                mWriteCallbackMap.remove(identify);
            }
        });
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
    public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
        super.onDescriptorWrite(gatt, descriptor, status);
        String address = gatt.getDevice().getAddress();
        if (mDevice == null || !address.equals(mDevice.getAddress())) {
            return;
        }
        UUID serviceUuid = descriptor.getCharacteristic().getService().getUuid();
        UUID charUuid = descriptor.getCharacteristic().getUuid();
        OperationIdentify identify = getOperationIdentify(mNotifyCallbackMap, serviceUuid, charUuid);
        if (identify == null) {
            return;
        }
        final BleNotifyCallback callback = mNotifyCallbackMap.get(identify);
        boolean notifiable = Arrays.equals(descriptor.getValue(), BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
                || Arrays.equals(descriptor.getValue(), BluetoothGattDescriptor.ENABLE_INDICATION_VALUE);
        if (!notifiable) {
            return;
        }
        runOrQueueCallback(new Runnable() {
            @Override
            public void run() {
                if (callback == null) {
                    return;
                }
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    callback.onNotifySuccess(charUuid, mDevice);
                } else {
                    callback.onNotifyFailed(BleErrorCodes.UNKNOWN, charUuid, mDevice);
                    mNotifyCallbackMap.remove(identify);
                }
            }
        });
    }

    @Override
    public void onReadRemoteRssi(BluetoothGatt gatt, final int rssi, int status) {
        super.onReadRemoteRssi(gatt, rssi, status);
        String address = gatt.getDevice().getAddress();
        if (mDevice == null || !address.equals(mDevice.getAddress())) {
            return;
        }
        runOrQueueCallback(new Runnable() {
            @Override
            public void run() {
                if (mRssiCallback == null) {
                    return;
                }
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    mRssiCallback.onRssiSuccess(rssi, mDevice);
                } else {
                    mRssiCallback.onRssiFailed(BleErrorCodes.UNKNOWN, mDevice);
                }
                mRssiCallback = null;
            }
        });
    }

    @Override
    public void onMtuChanged(BluetoothGatt gatt, final int mtu, int status) {
        super.onMtuChanged(gatt, mtu, status);
        String address = gatt.getDevice().getAddress();
        if (mDevice == null || !address.equals(mDevice.getAddress())) {
            return;
        }
        boolean success = status == BluetoothGatt.GATT_SUCCESS;
        if (success) {
            mCurrentMtu = mtu;
        }
        runOrQueueCallback(new Runnable() {
            @Override
            public void run() {
                if (mMtuCallback == null) {
                    return;
                }
                if (success) {
                    mMtuCallback.onMtuChanged(mtu, mDevice);
                } else {
                    mMtuCallback.onMtuFailed(BleErrorCodes.UNKNOWN, mDevice);
                }
                mMtuCallback = null;
            }
        });
    }


    private <T> OperationIdentify getOperationIdentify(Map<OperationIdentify, T> map,
                                                       UUID serviceUuid, UUID characteristicUuid) {
        for (OperationIdentify ui : map.keySet()) {
            if (ui.serviceUuid.equals(serviceUuid) &&
                    ui.characteristicUuid.equals(characteristicUuid)) {
                return ui;
            }
        }
        return null;
    }

    private void clearAndResetAll() {
        mHandler.removeCallbacksAndMessages(null);
        mReadCallbackMap.clear();
        mWriteCallbackMap.clear();
        mNotifyCallbackMap.clear();
        mConnectCallback = null;
        mMtuCallback = null;
        mRssiCallback = null;
        mGatt = null;
        // Reset MTU
        mCurrentMtu = BleGatt.MTU_MIN;
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

    private void runOrQueueCallback(Runnable r) {
        mHandler.post(r);
    }

    private static final class OperationIdentify {
        UUID serviceUuid;
        UUID characteristicUuid;

        OperationIdentify(UUID serviceUuid, UUID characteristicUuid) {
            this.serviceUuid = serviceUuid;
            this.characteristicUuid = characteristicUuid;
        }
    }
}