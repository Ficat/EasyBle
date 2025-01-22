package com.ficat.easyble.gatt;

import android.bluetooth.BluetoothAdapter;
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
import android.text.TextUtils;

import com.ficat.easyble.BleDevice;
import com.ficat.easyble.BleDeviceAccessor;
import com.ficat.easyble.BleManager;
import com.ficat.easyble.Logger;
import com.ficat.easyble.gatt.data.CharacteristicInfo;
import com.ficat.easyble.gatt.data.ServiceInfo;
import com.ficat.easyble.gatt.callback.BleCallback;
import com.ficat.easyble.gatt.callback.BleConnectCallback;
import com.ficat.easyble.gatt.callback.BleMtuCallback;
import com.ficat.easyble.gatt.callback.BleNotifyCallback;
import com.ficat.easyble.gatt.callback.BleReadCallback;
import com.ficat.easyble.gatt.callback.BleRssiCallback;
import com.ficat.easyble.gatt.callback.BleWriteByBatchCallback;
import com.ficat.easyble.gatt.callback.BleWriteCallback;
import com.ficat.easyble.utils.SplitBleDataUtils;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;


/**
 * Created by pw on 2018/9/13.
 */
public final class BleGattImpl implements BleGatt {
    private static final String CHARACTERISTIC_CONFIG = "00002902-0000-1000-8000-00805f9b34fb";
    private static final int MAX_CONNECTION_NUM = 7;
    private static final int MTU_MAX = 512;
    private static final int MTU_MIN = 23;
    private static final int ATT_OCCUPY_BYTES_NUM = 3;
    private static final int DEFAULT_TIME_OUT_MILLS = 10000;//default 10s

    private final Context mContext;
    private final Map<String, BleGattCommunicator> mBleGattCommunicatorMap;
    private final Handler mMainHandler;

    BleGattImpl(Context context) {
        mContext = context;
        mMainHandler = new Handler(Looper.getMainLooper());
        mBleGattCommunicatorMap = new ConcurrentHashMap<>();
    }

    @Override
    public void connect(int timeoutMills, final BleDevice device, final BleConnectCallback callback,
                        BleHandlerThread bleHandlerThread) {
        BleGattCommunicator c = mBleGattCommunicatorMap.get(device.getAddress());
        if (c == null) {
            c = new BleGattCommunicator(device, new AccessKey());
            mBleGattCommunicatorMap.put(device.getAddress(), c);
        }
        final BleGattCommunicator communicator = c;

        // If select a specified thread, all callbacks run in this thread,
        // otherwise run in main thread
        Handler handler = mMainHandler;
        if (bleHandlerThread != null) {
            if (!bleHandlerThread.isLooperPrepared()) {
                bleHandlerThread.start();
            }
            handler = new Handler(bleHandlerThread.getLooperInThread());
        }

        // Check if the connection can start
        boolean bluetoothOff = !BleManager.isBluetoothOn();
        boolean noPermission = !BleManager.connectionPermissionGranted(mContext);
        boolean reachConnectionMaxNum = reachConnectionMaxNum();
        boolean isConnecting = communicator.mDevice.isConnecting();
        boolean isConnected = communicator.mDevice.isConnected();
        if (bluetoothOff || noPermission || reachConnectionMaxNum || isConnecting || isConnected) {
            handler.post(new Runnable() {
                @Override
                public void run() {
                    String tips = "";
                    if (bluetoothOff) {
                        tips = "Bluetooth is not turned on";
                    } else if (noPermission) {
                        tips = "No connection permission(BLUETOOTH_CONNECT)";
                    } else if (reachConnectionMaxNum) {
                        tips = "The master device has reached maximum connection number";
                    } else if (isConnecting) {
                        tips = "Previous connection attempt not ended yet";
                    } else {
                        tips = "Connection has been established already";
                    }
                    callback.onStart(false, tips, device);
                }
            });
            // Failed to start connection, so quit looper
            if (bleHandlerThread != null) {
                bleHandlerThread.quitLooperSafely();
            }
            return;
        }

        // Connect to GATT
        BluetoothGatt gatt;
//        if (Build.VERSION.SDK_INT >= 26) {
//            gatt = device.getBluetoothDevice().connectGatt(mContext, false, communicator,
//                    BluetoothDevice.TRANSPORT_AUTO, BluetoothDevice.PHY_LE_1M_MASK, communicator.mHandler);
//        } else {
//            gatt = device.getBluetoothDevice().connectGatt(mContext, false, communicator);
//        }
        gatt = device.getBluetoothDevice().connectGatt(mContext, false, communicator);

        // Failed to connect GATT
        if (gatt == null) {
            handler.post(new Runnable() {
                @Override
                public void run() {
                    callback.onStart(false, "Failed to call BluetoothDevice#connectGatt()", device);
                }
            });
            //Failed to start connection, so quit looper
            if (bleHandlerThread != null) {
                bleHandlerThread.quitLooperSafely();
            }
            return;
        }

        // Connected with GATT, update communicator
        if (communicator.mBleHandlerThread != bleHandlerThread) {
            communicator.resetThreadAndHandler();
            communicator.mBleHandlerThread = bleHandlerThread;
            if (bleHandlerThread != null) {
                communicator.mHandler = new Handler(bleHandlerThread.getLooperInThread());
            }
        }
        if (communicator.mDevice != device) {// Use the newest BleDevice object
            communicator.mDevice = device;
        }
        communicator.mGatt = gatt;
        communicator.mConnectCallback = callback;
        communicator.setBleDeviceConnectionState(BleDevice.CONNECTING);
        communicator.mHandler.post(new Runnable() {
            @Override
            public void run() {
                communicator.mConnectCallback.onStart(true,
                        "Connection started successfully", communicator.mDevice);
            }
        });

        // Send connection timeout msg
        Message msg = Message.obtain(communicator.mHandler, new Runnable() {
            @Override
            public void run() {
                if (communicator.mGatt != null) {
                    communicator.mGatt.disconnect();
                    communicator.refreshDeviceCache();
                    communicator.mGatt.close();
                }
                communicator.setBleDeviceConnectionState(BleDevice.DISCONNECTED);
                communicator.mConnectCallback.onFailure(BleCallback.FAILURE_CONNECTION_TIMEOUT,
                        "Connection timed out", communicator.mDevice);
                communicator.clearAndResetAll();
            }
        });
        msg.obj = communicator.mDevice.getAddress();
        communicator.mHandler.sendMessageDelayed(msg, timeoutMills > 0 ? timeoutMills : DEFAULT_TIME_OUT_MILLS);
    }

    @Override
    public void disconnect(String address) {
        if (!BluetoothAdapter.checkBluetoothAddress(address)) {
            return;
        }
        BleGattCommunicator communicator = mBleGattCommunicatorMap.get(address);
        if (communicator == null || communicator.mGatt == null) {
            return;
        }
        BleConnectCallback callback = communicator.mConnectCallback;
        communicator.mGatt.disconnect();
        // Remove connection timeout message if connection is establishing
        communicator.mHandler.removeCallbacksAndMessages(address);
        if (communicator.mDevice.isConnecting()) {
            communicator.refreshDeviceCache();
            communicator.mGatt.close();
            communicator.setBleDeviceConnectionState(BleDevice.DISCONNECTED);
            communicator.mHandler.post(new Runnable() {
                @Override
                public void run() {
                    if (callback != null) {
                        callback.onFailure(BleCallback.FAILURE_CONNECTION_CANCELED,
                                "Connection canceled by calling disconnect()", communicator.mDevice);
                    }
                }
            });
            communicator.clearAndResetAll();
        }
    }

    @Override
    public void disconnectAll() {
        mMainHandler.removeCallbacksAndMessages(null);
        for (String address : mBleGattCommunicatorMap.keySet()) {
            disconnect(address);
        }
    }

    @Override
    public void notify(final BleDevice device, String serviceUuid, final String notifyUuid, final BleNotifyCallback callback) {
        BleGattCommunicator communicator = mBleGattCommunicatorMap.get(device.getAddress());
        // Check connection
        if (communicator == null || communicator.mGatt == null || !communicator.mDevice.isConnected()) {
            Handler handler = communicator == null ? mMainHandler : communicator.mHandler;
            handler.post(new Runnable() {
                @Override
                public void run() {
                    callback.onFailure(BleCallback.FAILURE_CONNECTION_NOT_ESTABLISHED, "Connection is not established yet", device);
                }
            });
            return;
        }

        BluetoothGatt gatt = communicator.mGatt;
        Handler handler = communicator.mHandler;
        // Check service
        ServiceInfo service = communicator.getService(serviceUuid);
        if (service == null) {
            handler.post(new Runnable() {
                @Override
                public void run() {
                    callback.onFailure(BleCallback.FAILURE_SERVICE_NOT_FOUND, "The specified service was not" +
                            " found in the remote device, make sure service uuid is correct", device);
                }
            });
            return;
        }
        // Check characteristic uuid and property
        CharacteristicInfo characteristic = service.getCharacteristic(notifyUuid);
        if (characteristic == null || (!characteristic.isNotifiable() && !characteristic.isIndicative())) {
            int failureCode = characteristic == null ?
                    BleCallback.FAILURE_CHARACTERISTIC_NOT_FOUND_IN_SERVICE :
                    BleCallback.FAILURE_NOTIFICATION_OR_INDICATION_UNSUPPORTED;
            String tips = characteristic == null ?
                    "The specified characteristic was not found in the service, make sure their uuids are correct" :
                    "This characteristic doesn't support notification or indication";
            handler.post(new Runnable() {
                @Override
                public void run() {
                    callback.onFailure(failureCode, tips, device);
                }
            });
            return;
        }
        // Add callback to communicator
        OperationIdentify identify = communicator.getOperationIdentify(communicator.mNotifyCallbackMap, serviceUuid, notifyUuid);
        communicator.mNotifyCallbackMap.put(identify == null ? new OperationIdentify(serviceUuid, notifyUuid) : identify, callback);
        // Enable notification
        BluetoothGattCharacteristic gattCh = characteristic.getBluetoothGattCharacteristic();
        if (gatt.setCharacteristicNotification(gattCh, true)) {
            BluetoothGattDescriptor descriptor = gattCh.getDescriptor(UUID.fromString(CHARACTERISTIC_CONFIG));
            if (descriptor == null) {
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        callback.onNotifySuccess(notifyUuid, device);
                    }
                });
                return;
            }
            if (characteristic.isNotifiable()) {
                descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
            }
            if (characteristic.isIndicative()) {
                descriptor.setValue(BluetoothGattDescriptor.ENABLE_INDICATION_VALUE);
            }
            // If the write operation was initiated successfully, wait operation
            // result until BleGattCommunicator#onDescriptorWrite() is called back
            if (gatt.writeDescriptor(descriptor)) {
                return;
            }
        }
        handler.post(new Runnable() {
            @Override
            public void run() {
                callback.onFailure(BleCallback.FAILURE_OTHER, "Failed to set characteristic notification", device);
            }
        });
    }

    @Override
    public void cancelNotify(BleDevice device, String serviceUuid, String notifyUuid) {
        BleGattCommunicator communicator = mBleGattCommunicatorMap.get(device.getAddress());
        // Check connection
        if (communicator == null || communicator.mGatt == null || !communicator.mDevice.isConnected()) {
            return;
        }
        OperationIdentify identify = communicator.getOperationIdentify(communicator.mNotifyCallbackMap, serviceUuid, notifyUuid);
        if (identify != null) {
            communicator.mNotifyCallbackMap.remove(identify);
        }
        BluetoothGatt gatt = communicator.mGatt;
        ServiceInfo service = communicator.getService(serviceUuid);
        if (service == null) {
            return;
        }
        CharacteristicInfo characteristic = service.getCharacteristic(notifyUuid);
        if (characteristic == null) {
            return;
        }
        if (characteristic.isNotifiable() || characteristic.isIndicative()) {
            BluetoothGattCharacteristic gattCh = characteristic.getBluetoothGattCharacteristic();
            if (gatt.setCharacteristicNotification(gattCh, false)) {
                BluetoothGattDescriptor descriptor = gattCh.getDescriptor(UUID.fromString(CHARACTERISTIC_CONFIG));
                if (descriptor == null) {
                    return;
                }
                descriptor.setValue(BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE);
                gatt.writeDescriptor(descriptor);
            }
        }
    }

    @Override
    public void read(final BleDevice device, String serviceUuid, String readUuid, final BleReadCallback callback) {
        BleGattCommunicator communicator = mBleGattCommunicatorMap.get(device.getAddress());
        // Check connection
        if (communicator == null || communicator.mGatt == null || !communicator.mDevice.isConnected()) {
            Handler handler = communicator == null ? mMainHandler : communicator.mHandler;
            handler.post(new Runnable() {
                @Override
                public void run() {
                    callback.onFailure(BleCallback.FAILURE_CONNECTION_NOT_ESTABLISHED, "Connection is not established yet", device);
                }
            });
            return;
        }

        BluetoothGatt gatt = communicator.mGatt;
        Handler handler = communicator.mHandler;
        // Check service
        ServiceInfo service = communicator.getService(serviceUuid);
        if (service == null) {
            handler.post(new Runnable() {
                @Override
                public void run() {
                    callback.onFailure(BleCallback.FAILURE_SERVICE_NOT_FOUND, "The specified service was not" +
                            " found in the remote device, make sure service uuid is correct", device);
                }
            });
            return;
        }
        // Check characteristic uuid and property
        CharacteristicInfo characteristic = service.getCharacteristic(readUuid);
        if (characteristic == null || !characteristic.isReadable()) {
            int failureCode = characteristic == null ?
                    BleCallback.FAILURE_CHARACTERISTIC_NOT_FOUND_IN_SERVICE :
                    BleCallback.FAILURE_READ_UNSUPPORTED;
            String tips = characteristic == null ?
                    "The specified characteristic was not found in the service, make sure their uuids are correct" :
                    "The characteristic is not readable";
            handler.post(new Runnable() {
                @Override
                public void run() {
                    callback.onFailure(failureCode, tips, device);
                }
            });
            return;
        }
        // Add callback to communicator
        OperationIdentify identify = communicator.getOperationIdentify(communicator.mReadCallbackMap, serviceUuid, readUuid);
        communicator.mReadCallbackMap.put(identify == null ? new OperationIdentify(serviceUuid, readUuid) : identify, callback);
        // Read data from characteristic
        if (!gatt.readCharacteristic(characteristic.getBluetoothGattCharacteristic())) {
            handler.post(new Runnable() {
                @Override
                public void run() {
                    callback.onFailure(BleCallback.FAILURE_OTHER, "Failed to initiate the read operation", device);
                }
            });
        }
    }

    @Override
    public void write(final BleDevice device, String serviceUuid, String writeUuid, byte[] data, final BleWriteCallback callback) {
        BleGattCommunicator communicator = mBleGattCommunicatorMap.get(device.getAddress());
        // Check connection
        if (communicator == null || communicator.mGatt == null || !communicator.mDevice.isConnected()) {
            Handler handler = communicator == null ? mMainHandler : communicator.mHandler;
            handler.post(new Runnable() {
                @Override
                public void run() {
                    callback.onFailure(BleCallback.FAILURE_CONNECTION_NOT_ESTABLISHED, "Connection is not established yet", device);
                }
            });
            return;
        }
        // Check data and length
        Handler handler = communicator.mHandler;
        int maxNumPerPack = communicator.mCurrentMtu - ATT_OCCUPY_BYTES_NUM;
        if (data == null || data.length < 1 || data.length > maxNumPerPack) {
            handler.post(new Runnable() {
                @Override
                public void run() {
                    callback.onFailure(BleCallback.FAILURE_OTHER, data == null ? "Data is null" :
                            "Data length must range from 1 to " + maxNumPerPack, device);
                }
            });
            return;
        }
        // Check service
        ServiceInfo service = communicator.getService(serviceUuid);
        if (service == null) {
            handler.post(new Runnable() {
                @Override
                public void run() {
                    callback.onFailure(BleCallback.FAILURE_SERVICE_NOT_FOUND, "The specified service was not" +
                            " found in the remote device, make sure service uuid is correct", device);
                }
            });
            return;
        }
        // Check characteristic uuid and property
        CharacteristicInfo characteristic = service.getCharacteristic(writeUuid);
        if (characteristic == null || !characteristic.isWritable()) {
            int failureCode = characteristic == null ?
                    BleCallback.FAILURE_CHARACTERISTIC_NOT_FOUND_IN_SERVICE :
                    BleCallback.FAILURE_WRITE_UNSUPPORTED;
            String tips = characteristic == null ?
                    "The specified characteristic was not found in the service, make sure their uuids are correct" :
                    "The characteristic is not writable";
            handler.post(new Runnable() {
                @Override
                public void run() {
                    callback.onFailure(failureCode, tips, device);
                }
            });
            return;
        }
        writeData(device, serviceUuid, writeUuid, data, callback, communicator);
    }

    @Override
    public void writeByBatch(BleDevice device, final String serviceUuid, final String writeUuid,
                             final byte[] writeData, int lengthPerPackage, final long writeDelay, final BleWriteByBatchCallback callback) {
        BleGattCommunicator communicator = mBleGattCommunicatorMap.get(device.getAddress());
        // Check connection
        if (communicator == null || communicator.mGatt == null || !communicator.mDevice.isConnected()) {
            Handler handler = communicator == null ? mMainHandler : communicator.mHandler;
            handler.post(new Runnable() {
                @Override
                public void run() {
                    callback.onFailure(BleCallback.FAILURE_CONNECTION_NOT_ESTABLISHED, "Connection is not established yet", device);
                }
            });
            return;
        }
        Handler handler = communicator.mHandler;
        // Check data and data length
        int maxNumPerPack = communicator.mCurrentMtu - ATT_OCCUPY_BYTES_NUM;
        boolean invalidData = writeData == null || writeData.length == 0;
        boolean invalidPackLen = lengthPerPackage < 1 || lengthPerPackage > maxNumPerPack;
        if (invalidData || invalidPackLen) {
            handler.post(new Runnable() {
                @Override
                public void run() {
                    callback.onFailure(BleCallback.FAILURE_OTHER, invalidData ?
                            "WriteData is null or no writing data" :
                            "LengthPerPackage is invalid, it must range from 1 to " + maxNumPerPack, device);
                }
            });
            return;
        }
        // Check service
        ServiceInfo service = communicator.getService(serviceUuid);
        if (service == null) {
            handler.post(new Runnable() {
                @Override
                public void run() {
                    callback.onFailure(BleCallback.FAILURE_SERVICE_NOT_FOUND, "The specified service was not" +
                            " found in the remote device, make sure service uuid is correct", device);
                }
            });
            return;
        }
        // Check characteristic uuid and property
        CharacteristicInfo characteristic = service.getCharacteristic(writeUuid);
        if (characteristic == null || !characteristic.isWritable()) {
            int failureCode = characteristic == null ?
                    BleCallback.FAILURE_CHARACTERISTIC_NOT_FOUND_IN_SERVICE :
                    BleCallback.FAILURE_WRITE_UNSUPPORTED;
            String tips = characteristic == null ?
                    "The specified characteristic was not found in the service, make sure their uuids are correct" :
                    "The characteristic is not writable";
            handler.post(new Runnable() {
                @Override
                public void run() {
                    callback.onFailure(failureCode, tips, device);
                }
            });
            return;
        }

        // Create a data queue and write it in sequence
        final Queue<byte[]> queue = SplitBleDataUtils.getBatchData(writeData, lengthPerPackage);
        if (queue.size() <= 0) return;
        BleWriteCallback writeCallback = new BleWriteCallback() {
            @Override
            public void onWriteSuccess(byte[] data, final BleDevice device) {
                final BleWriteCallback c = this;
                final byte[] next = queue.poll();
                if (next != null) {
                    if (writeDelay <= 0) {
                        writeData(device, serviceUuid, writeUuid, next, c, communicator);
                    } else {
                        mMainHandler.postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                writeData(device, serviceUuid, writeUuid, next, c, communicator);
                            }
                        }, writeDelay);
                    }
                } else {
                    callback.writeByBatchSuccess(writeData, device);
                }
            }

            @Override
            public void onFailure(int failureCode, String info, BleDevice device) {
                callback.onFailure(failureCode, info, device);
            }
        };
        byte[] d = queue.poll();
        if (d != null) {
            writeData(device, serviceUuid, writeUuid, d, writeCallback, communicator);
        }
    }

    private void writeData(final BleDevice device, String serviceUuid, String writeUuid, byte[] data,
                           final BleWriteCallback callback, BleGattCommunicator communicator) {
        int minNumPerPack = communicator.mCurrentMtu - ATT_OCCUPY_BYTES_NUM;
        if (data.length > minNumPerPack) {
            Logger.w("Data length is greater than the default(" + minNumPerPack + " Bytes), make sure  MTU >= " +
                    (data.length + ATT_OCCUPY_BYTES_NUM));
        }
        // Add callback to communicator
        OperationIdentify identify = communicator.getOperationIdentify(communicator.mWriteCallbackMap, serviceUuid, writeUuid);
        communicator.mWriteCallbackMap.put(identify == null ? new OperationIdentify(serviceUuid, writeUuid) : identify, callback);
        // Write data
        ServiceInfo service = communicator.getService(serviceUuid);
        if (service == null) {
            return;
        }
        BluetoothGattCharacteristic gattCh = service.getCharacteristic(writeUuid).getBluetoothGattCharacteristic();
        if (!gattCh.setValue(data) || !communicator.mGatt.writeCharacteristic(gattCh)) {
            communicator.mHandler.post(new Runnable() {
                @Override
                public void run() {
                    callback.onFailure(BleCallback.FAILURE_OTHER, "Failed to initiate the write operation", device);
                }
            });
        }
    }

    @Override
    public void readRssi(final BleDevice device, final BleRssiCallback callback) {
        BleGattCommunicator communicator = mBleGattCommunicatorMap.get(device.getAddress());
        // Check connection
        if (communicator == null || communicator.mGatt == null || !communicator.mDevice.isConnected()) {
            Handler handler = communicator == null ? mMainHandler : communicator.mHandler;
            handler.post(new Runnable() {
                @Override
                public void run() {
                    callback.onFailure(BleCallback.FAILURE_CONNECTION_NOT_ESTABLISHED, "Connection is not established yet", device);
                }
            });
            return;
        }
        BluetoothGatt gatt = communicator.mGatt;
        Handler handler = communicator.mHandler;
        communicator.mRssiCallback = callback;
        if (gatt == null || !gatt.readRemoteRssi()) {
            handler.post(new Runnable() {
                @Override
                public void run() {
                    callback.onFailure(BleCallback.FAILURE_OTHER, "Failed to request RSSI value", device);
                }
            });
        }
    }

    @SuppressWarnings("NewApi")
    @Override
    public void setMtu(final BleDevice device, int mtu, final BleMtuCallback callback) {
        BleGattCommunicator communicator = mBleGattCommunicatorMap.get(device.getAddress());
        // Check connection
        if (communicator == null || communicator.mGatt == null || !communicator.mDevice.isConnected()) {
            Handler handler = communicator == null ? mMainHandler : communicator.mHandler;
            handler.post(new Runnable() {
                @Override
                public void run() {
                    callback.onFailure(BleCallback.FAILURE_CONNECTION_NOT_ESTABLISHED, "Connection is not established yet", device);
                }
            });
            return;
        }
        BluetoothGatt gatt = communicator.mGatt;
        Handler handler = communicator.mHandler;
        // Check supported version
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            handler.post(new Runnable() {
                @Override
                public void run() {
                    callback.onFailure(BleCallback.FAILURE_OTHER,
                            "The minimum android api version that supports MTU settings is 21", device);
                }
            });
            return;
        }
        communicator.mMtuCallback = callback;
        if (mtu < MTU_MIN) {
            mtu = MTU_MIN;
        } else if (mtu > MTU_MAX) {
            mtu = MTU_MAX;
        }
        if (gatt == null || !gatt.requestMtu(mtu)) {
            handler.post(new Runnable() {
                @Override
                public void run() {
                    callback.onFailure(BleCallback.FAILURE_OTHER, "Failed to request MTU due to unknown reason", device);
                }
            });
        }
    }

    @Override
    public List<BleDevice> getConnectedDevices() {
        List<BleDevice> deviceList = new ArrayList<>();
        for (BleGattCommunicator d : mBleGattCommunicatorMap.values()) {
            if (d.mDevice.isConnected()) {
                deviceList.add(d.mDevice);
            }
        }
        return deviceList;
    }

    @Override
    public boolean isConnecting(String address) {
        for (BleGattCommunicator d : mBleGattCommunicatorMap.values()) {
            if (d.mDevice.isConnecting() && d.mDevice.getAddress().equals(address)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean isConnected(String address) {
        for (BleGattCommunicator d : mBleGattCommunicatorMap.values()) {
            if (d.mDevice.isConnected() && d.mDevice.getAddress().equals(address)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public List<ServiceInfo> getDeviceServices(String address) {
        if (!BluetoothAdapter.checkBluetoothAddress(address)) {
            return null;
        }
        for (BleGattCommunicator d : mBleGattCommunicatorMap.values()) {
            if (d.mDevice.getAddress().equals(address)) {
                return d.mServiceList;
            }
        }
        return null;
    }

    @Override
    public BluetoothGatt getBluetoothGatt(String address) {
        for (BleGattCommunicator d : mBleGattCommunicatorMap.values()) {
            if (d.mDevice.getAddress().equals(address)) {
                return d.mGatt;
            }
        }
        return null;
    }

    @Override
    public void destroy() {
        disconnectAll();
        mBleGattCommunicatorMap.clear();
    }

    private boolean reachConnectionMaxNum() {
        return getConnectedDevices().size() >= MAX_CONNECTION_NUM;
    }

    private static final class BleGattCommunicator extends BluetoothGattCallback {
        private Handler mHandler;
        private BleHandlerThread mBleHandlerThread;
        private BleDevice mDevice;
        private final AccessKey mAccessKey;
        private BleConnectCallback mConnectCallback;
        private BleMtuCallback mMtuCallback;
        private BleRssiCallback mRssiCallback;
        private final List<ServiceInfo> mServiceList;
        private final Map<OperationIdentify, BleNotifyCallback> mNotifyCallbackMap;
        private final Map<OperationIdentify, BleReadCallback> mReadCallbackMap;
        private final Map<OperationIdentify, BleWriteCallback> mWriteCallbackMap;
        private volatile int mCurrentMtu;
        private BluetoothGatt mGatt;

        private BleGattCommunicator(BleDevice device, AccessKey key) {
            if (device == null) {
                throw new IllegalArgumentException("BleDevice is null");
            }
            if (key == null) {
                throw new IllegalArgumentException("AccessKey is null");
            }
            this.mHandler = new Handler(Looper.getMainLooper());
            this.mDevice = device;
            this.mAccessKey = key;
            this.mNotifyCallbackMap = new ConcurrentHashMap<>();
            this.mReadCallbackMap = new ConcurrentHashMap<>();
            this.mWriteCallbackMap = new ConcurrentHashMap<>();
            this.mServiceList = new ArrayList<>();
            setCurrentMtu(BleGattImpl.MTU_MIN);
        }

        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, final int status, int newState) {
            super.onConnectionStateChange(gatt, status, newState);
            String address = gatt.getDevice().getAddress();
            if (mDevice == null || !address.equals(mDevice.getAddress())) {
                return;
            }
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
                        setBleDeviceConnectionState(BleDevice.DISCONNECTED);
                        runOrQueueCallback(new Runnable() {
                            @Override
                            public void run() {
                                if (callback != null) {
                                    callback.onDisconnected("The connection has been disconnected", status, mDevice);
                                }
                            }
                        });
                        clearAndResetAll();
                        break;
                }
            } else {
                refreshDeviceCache();
                gatt.close();
                if (mDevice.isConnecting()) {
                    //Remove connection timeout message
                    mHandler.removeCallbacksAndMessages(address);
                    setBleDeviceConnectionState(BleDevice.DISCONNECTED);
                    runOrQueueCallback(new Runnable() {
                        @Override
                        public void run() {
                            if (callback != null) {
                                callback.onFailure(BleCallback.FAILURE_CONNECTION_FAILED,
                                        "Connection failed,  status = " + status, mDevice);
                            }
                        }
                    });
                } else if (mDevice.isConnected() && newState == BluetoothProfile.STATE_DISCONNECTED) {
                    setBleDeviceConnectionState(BleDevice.DISCONNECTED);
                    runOrQueueCallback(new Runnable() {
                        @Override
                        public void run() {
                            String tips = "Disconnected from the remote device(mac=" + mDevice.getAddress() +
                                    ") abnormally, see more details from status code";
                            if (callback != null) {
                                callback.onDisconnected(tips, status, mDevice);
                            }
                        }
                    });
                }
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
            if (status == BluetoothGatt.GATT_SUCCESS) {
                List<BluetoothGattService> gattServices = gatt.getServices();
                for (BluetoothGattService service : gattServices) {
                    mServiceList.add(new ServiceInfo(service));
                }
                //Remove connection timeout message
                mHandler.removeCallbacksAndMessages(address);
                setBleDeviceConnectionState(BleDevice.CONNECTED);
                runOrQueueCallback(new Runnable() {
                    @Override
                    public void run() {
                        if (mConnectCallback != null) {
                            mConnectCallback.onConnected(mDevice);
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
            if (status == BluetoothGatt.GATT_SUCCESS) {
                String serviceUuid = characteristic.getService().getUuid().toString();
                String charUuid = characteristic.getUuid().toString();
                OperationIdentify identify = getOperationIdentify(mReadCallbackMap, serviceUuid, charUuid);
                if (identify == null) {
                    return;
                }
                final BleReadCallback callback = mReadCallbackMap.get(identify);
                final byte[] data = characteristic.getValue();
                runOrQueueCallback(new Runnable() {
                    @Override
                    public void run() {
                        if (callback != null) {
                            callback.onReadSuccess(data, mDevice);
                        }
                    }
                });
            }
        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            super.onCharacteristicWrite(gatt, characteristic, status);
            String address = gatt.getDevice().getAddress();
            if (mDevice == null || !address.equals(mDevice.getAddress())) {
                return;
            }
            if (status == BluetoothGatt.GATT_SUCCESS) {
                String serviceUuid = characteristic.getService().getUuid().toString();
                String charUuid = characteristic.getUuid().toString();
                OperationIdentify identify = getOperationIdentify(mWriteCallbackMap, serviceUuid, charUuid);
                if (identify == null) {
                    return;
                }
                final BleWriteCallback callback = mWriteCallbackMap.get(identify);
                final byte[] data = characteristic.getValue();
                runOrQueueCallback(new Runnable() {
                    @Override
                    public void run() {
                        if (callback != null) {
                            callback.onWriteSuccess(data, mDevice);
                        }
                    }
                });
            }
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            super.onCharacteristicChanged(gatt, characteristic);
            String address = gatt.getDevice().getAddress();
            if (mDevice == null || !address.equals(mDevice.getAddress())) {
                return;
            }
            String serviceUuid = characteristic.getService().getUuid().toString();
            String charUuid = characteristic.getUuid().toString();
            OperationIdentify identify = getOperationIdentify(mNotifyCallbackMap, serviceUuid, charUuid);
            if (identify == null) {
                return;
            }
            final BleNotifyCallback callback = mNotifyCallbackMap.get(identify);
            final byte[] data = characteristic.getValue();
            runOrQueueCallback(new Runnable() {
                @Override
                public void run() {
                    if (callback != null) {
                        callback.onCharacteristicChanged(data, mDevice);
                    }
                }
            });
        }

        @Override
        public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
            super.onDescriptorWrite(gatt, descriptor, status);
            String address = gatt.getDevice().getAddress();
            if (mDevice == null || !address.equals(mDevice.getAddress())) {
                return;
            }
            if (status == BluetoothGatt.GATT_SUCCESS) {
                String serviceUuid = descriptor.getCharacteristic().getService().getUuid().toString();
                final String charUuid = descriptor.getCharacteristic().getUuid().toString();
                OperationIdentify identify = getOperationIdentify(mNotifyCallbackMap, serviceUuid, charUuid);
                if (identify == null) {
                    return;
                }
                boolean notifiable = Arrays.equals(descriptor.getValue(), BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
                        || Arrays.equals(descriptor.getValue(), BluetoothGattDescriptor.ENABLE_INDICATION_VALUE);
                if (!notifiable) {
                    return;
                }
                final BleNotifyCallback callback = mNotifyCallbackMap.get(identify);
                runOrQueueCallback(new Runnable() {
                    @Override
                    public void run() {
                        if (callback != null) {
                            callback.onNotifySuccess(charUuid, mDevice);
                        }
                    }
                });
            }
        }

        @Override
        public void onReadRemoteRssi(BluetoothGatt gatt, final int rssi, int status) {
            super.onReadRemoteRssi(gatt, rssi, status);
            String address = gatt.getDevice().getAddress();
            if (mDevice == null || !address.equals(mDevice.getAddress())) {
                return;
            }
            if (status == BluetoothGatt.GATT_SUCCESS) {
                runOrQueueCallback(new Runnable() {
                    @Override
                    public void run() {
                        if (mRssiCallback != null) {
                            mRssiCallback.onRssi(rssi, mDevice);
                        }
                    }
                });
            }
        }

        @Override
        public void onMtuChanged(BluetoothGatt gatt, final int mtu, int status) {
            super.onMtuChanged(gatt, mtu, status);
            String address = gatt.getDevice().getAddress();
            if (mDevice == null || !address.equals(mDevice.getAddress())) {
                return;
            }
            if (status == BluetoothGatt.GATT_SUCCESS) {
                setCurrentMtu(mtu);
                runOrQueueCallback(new Runnable() {
                    @Override
                    public void run() {
                        if (mMtuCallback != null) {
                            mMtuCallback.onMtuChanged(mtu, mDevice);
                        }
                    }
                });
            }
        }

        private <T> OperationIdentify getOperationIdentify(Map<OperationIdentify, T> map,
                                                           String serviceUuid, String characteristicUuid) {
            if (TextUtils.isEmpty(serviceUuid) || TextUtils.isEmpty(characteristicUuid)) {
                return null;
            }
            for (OperationIdentify ui : map.keySet()) {
                if (ui.characteristicUuid.equalsIgnoreCase(characteristicUuid)
                        && ui.serviceUuid.equalsIgnoreCase(serviceUuid)) {
                    return ui;
                }
            }
            return null;
        }

        private ServiceInfo getService(String serviceUuid) {
            for (ServiceInfo s : mServiceList) {
                if (s.getUuid().toString().equals(serviceUuid)) {
                    return s;
                }
            }
            return null;
        }

        private void clearAndResetAll() {
            mReadCallbackMap.clear();
            mWriteCallbackMap.clear();
            mNotifyCallbackMap.clear();
            mServiceList.clear();
            mConnectCallback = null;
            mMtuCallback = null;
            mRssiCallback = null;
            mGatt = null;
            // Reset MTU
            setCurrentMtu(BleGattImpl.MTU_MIN);
            // Reset BleHandlerThread and Handler
            resetThreadAndHandler();
        }

        private void resetThreadAndHandler() {
            // Stop BleHandlerThread
            if (mBleHandlerThread != null) {
                mBleHandlerThread.quitLooperSafely();
                mBleHandlerThread = null;
            }
            // Reset handler
            if (mHandler.getLooper() != Looper.getMainLooper()) {
                mHandler = new Handler(Looper.getMainLooper());
            }
        }

        private void setCurrentMtu(int mtu) {
            synchronized (BleGattCommunicator.this) {
                mCurrentMtu = mtu;
            }
        }

        private void setBleDeviceConnectionState(int newConnState) {
            BleDeviceAccessor.setBleDeviceConnection(mDevice, newConnState, mAccessKey);
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
//            if (Build.VERSION.SDK_INT >= 26) {
//                r.run();
//            } else {
//                mHandler.post(r);
//            }
        }
    }


    private static final class OperationIdentify {
        String serviceUuid;
        String characteristicUuid;

        OperationIdentify(String serviceUuid, String characteristicUuid) {
            this.serviceUuid = serviceUuid;
            this.characteristicUuid = characteristicUuid;
        }
    }

    public static final class AccessKey {
        private AccessKey() {

        }
    }
}
