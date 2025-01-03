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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;


/**
 * Created by pw on 2018/9/13.
 */
public class BleGattImpl implements BleGatt {
    private static final String CHARACTERISTIC_CONFIG = "00002902-0000-1000-8000-00805f9b34fb";
    private static final int MAX_CONNECTION_NUM = 7;
    private static final int MTU_MAX = 512;
    private static final int MTU_MIN = 23;
    private static final int ATT_OCCUPY_BYTES_NUM = 3;

    private Context mContext;
    private int mConnectTimeout = 10000;//default 10s
    private Handler mHandler;
    private Map<BleDevice, BleConnectCallback> mConnectCallbackMap;
    private Map<String, BleMtuCallback> mMtuCallbackMap;
    private Map<String, BleRssiCallback> mRssiCallbackMap;
    private Map<String, BluetoothGatt> mGattMap;
    private Map<String, Map<ServiceInfo, List<CharacteristicInfo>>> mServicesMap;
    private Map<OperationIdentify, BleNotifyCallback> mNotifyCallbackMap;
    private Map<OperationIdentify, BleReadCallback> mReadCallbackMap;
    private Map<OperationIdentify, BleWriteCallback> mWriteCallbackMap;

    public BleGattImpl(Context context) {
        mContext = context;
        mHandler = new Handler(Looper.getMainLooper());
        mConnectCallbackMap = new ConcurrentHashMap<>();
        mMtuCallbackMap = new ConcurrentHashMap<>();
        mNotifyCallbackMap = new ConcurrentHashMap<>();
        mReadCallbackMap = new ConcurrentHashMap<>();
        mWriteCallbackMap = new ConcurrentHashMap<>();
        mRssiCallbackMap = new ConcurrentHashMap<>();
        mGattMap = new ConcurrentHashMap<>();
        mServicesMap = new ConcurrentHashMap<>();
    }

    private BluetoothGattCallback mGattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, final int status, int newState) {
            super.onConnectionStateChange(gatt, status, newState);
            String address = gatt.getDevice().getAddress();
            Map.Entry<BleDevice, BleConnectCallback> entry = findMapEntry(mConnectCallbackMap, address);
            if (entry == null) {
                gatt.close();
                return;
            }
            final BleConnectCallback callback = entry.getValue();
            final BleDevice device = entry.getKey();
            if (status == BluetoothGatt.GATT_SUCCESS) {
                switch (newState) {
                    case BluetoothProfile.STATE_CONNECTED:
                        //start discovering services, only services are found do we deem
                        //connection is successful
                        gatt.discoverServices();
                        break;
                    case BluetoothProfile.STATE_DISCONNECTED:
                        refreshDeviceCache(gatt);
                        gatt.close();
                        removeDevice(device);
                        device.setConnectionState(BleDevice.DISCONNECTED);
                        mHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                if (callback != null) {
                                    callback.onDisconnected("The connection has been disconnected", status, device);
                                }
                            }
                        });
                        break;
                }
            } else {
                gatt.close();
                removeDevice(device);
                if (device.isConnecting()) {
                    mHandler.removeCallbacksAndMessages(address);
                    device.setConnectionState(BleDevice.DISCONNECTED);
                    mHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            callback.onFailure(BleCallback.FAIL_OTHER, "Connection failed,  status = " + status, device);
                        }
                    });
                } else if (device.isConnected() && newState == BluetoothProfile.STATE_DISCONNECTED) {
                    device.setConnectionState(BleDevice.DISCONNECTED);
                    mHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            String tips = "The connection with remote device(mac=" + device.getDevice().getAddress() +
                                    ") has been disconnected abnormally, see more details from status code";
                            callback.onDisconnected(tips, status, device);
                        }
                    });
                }
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            super.onServicesDiscovered(gatt, status);
            if (status == BluetoothGatt.GATT_SUCCESS) {
                String address = gatt.getDevice().getAddress();
                Map.Entry<BleDevice, BleConnectCallback> entry = findMapEntry(mConnectCallbackMap, address);
                if (entry == null) {
                    return;
                }
                List<BluetoothGattService> gattServices = gatt.getServices();
                Map<ServiceInfo, List<CharacteristicInfo>> servicesInfoMap = getServiceInfoMap(gattServices);
                mServicesMap.put(address, servicesInfoMap);

                final BleConnectCallback callback = entry.getValue();
                final BleDevice device = entry.getKey();
                //remove connection timeout message
                mHandler.removeCallbacksAndMessages(address);
                device.setConnectionState(BleDevice.CONNECTED);
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        if (callback != null) {
                            callback.onConnected(device);
                        }
                    }
                });
            }
        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            super.onCharacteristicRead(gatt, characteristic, status);
            if (status == BluetoothGatt.GATT_SUCCESS) {
                String address = gatt.getDevice().getAddress();
                String serviceUuid = characteristic.getService().getUuid().toString();
                String charUuid = characteristic.getUuid().toString();
                OperationIdentify identify = getOperationIdentifyFromMap(mReadCallbackMap, address, serviceUuid, charUuid);
                if (identify == null) {
                    return;
                }
                final BleReadCallback callback = mReadCallbackMap.get(identify);
                final BleDevice device = getBleDeviceFromMap(address, mConnectCallbackMap);
                final byte[] data = characteristic.getValue();
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        if (callback != null) {
                            callback.onReadSuccess(data, device);
                        }
                    }
                });
            }
        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            super.onCharacteristicWrite(gatt, characteristic, status);
            if (status == BluetoothGatt.GATT_SUCCESS) {
                String address = gatt.getDevice().getAddress();
                String serviceUuid = characteristic.getService().getUuid().toString();
                String charUuid = characteristic.getUuid().toString();
                OperationIdentify identify = getOperationIdentifyFromMap(mWriteCallbackMap, address, serviceUuid, charUuid);
                if (identify == null) {
                    return;
                }
                final BleWriteCallback callback = mWriteCallbackMap.get(identify);
                final BleDevice device = getBleDeviceFromMap(address, mConnectCallbackMap);
                final byte[] data = characteristic.getValue();
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        if (callback != null) {
                            callback.onWriteSuccess(data, device);
                        }
                    }
                });
            }
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            super.onCharacteristicChanged(gatt, characteristic);
            String address = gatt.getDevice().getAddress();
            String serviceUuid = characteristic.getService().getUuid().toString();
            String charUuid = characteristic.getUuid().toString();
            OperationIdentify identify = getOperationIdentifyFromMap(mNotifyCallbackMap, address, serviceUuid, charUuid);
            if (identify == null) {
                return;
            }
            final BleNotifyCallback callback = mNotifyCallbackMap.get(identify);
            final BleDevice device = getBleDeviceFromMap(address, mConnectCallbackMap);
            final byte[] data = characteristic.getValue();
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    if (callback != null) {
                        callback.onCharacteristicChanged(data, device);
                    }
                }
            });
        }

        @Override
        public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
            super.onDescriptorWrite(gatt, descriptor, status);
            if (status == BluetoothGatt.GATT_SUCCESS) {
                String address = gatt.getDevice().getAddress();
                String serviceUuid = descriptor.getCharacteristic().getService().getUuid().toString();
                final String charUuid = descriptor.getCharacteristic().getUuid().toString();
                boolean notifiable = Arrays.equals(descriptor.getValue(), BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
                        || Arrays.equals(descriptor.getValue(), BluetoothGattDescriptor.ENABLE_INDICATION_VALUE);
                if (!notifiable) {
                    return;
                }
                OperationIdentify identify = getOperationIdentifyFromMap(mNotifyCallbackMap, address, serviceUuid, charUuid);
                if (identify == null) {
                    return;
                }
                final BleNotifyCallback callback = mNotifyCallbackMap.get(identify);
                final BleDevice device = getBleDeviceFromMap(address, mConnectCallbackMap);
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        if (callback != null) {
                            callback.onNotifySuccess(charUuid, device);
                        }
                    }
                });
            }
        }

        @Override
        public void onReadRemoteRssi(BluetoothGatt gatt, final int rssi, int status) {
            super.onReadRemoteRssi(gatt, rssi, status);
            if (status == BluetoothGatt.GATT_SUCCESS) {
                String address = gatt.getDevice().getAddress();
                final BleRssiCallback callback = mRssiCallbackMap.get(address);
                final BleDevice device = getBleDeviceFromMap(address, mConnectCallbackMap);
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        if (callback != null) {
                            callback.onRssi(rssi, device);
                        }
                    }
                });
            }
        }

        @Override
        public void onMtuChanged(BluetoothGatt gatt, final int mtu, int status) {
            super.onMtuChanged(gatt, mtu, status);
            if (status == BluetoothGatt.GATT_SUCCESS) {
                String address = gatt.getDevice().getAddress();
                final BleMtuCallback callback = mMtuCallbackMap.get(address);
                final BleDevice device = getBleDeviceFromMap(address, mConnectCallbackMap);
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        if (callback != null) {
                            callback.onMtuChanged(mtu, device);
                        }
                    }
                });
            }
        }
    };

    @Override
    public synchronized void connect(int connectTimeout, final BleDevice device, final BleConnectCallback callback) {
        checkNotNull(callback, BleConnectCallback.class);
        checkNotNull(device, BleDevice.class);
        if (!isBluetoothEnable() || reachConnectionMaxNum()) {
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    String tips = reachConnectionMaxNum() ?
                            "The master device has reached maximum connection number" :
                            "Turn on bluetooth before starting connecting device";
                    callback.onStart(false, tips, device);
                }
            });
            return;
        }
        final BleDevice d = getBleDeviceFromMap(device.getAddress(), mConnectCallbackMap);
        if (d != null) {
            if (d.isConnecting() || d.isConnected()) {
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        String tips = d.isConnecting() ? "Connection is establishing" :
                                "Connection has been established already";
                        callback.onStart(false, tips, d);
                    }
                });
                return;
            }
            if (d != device) {//use the newest BleDevice object
                mConnectCallbackMap.remove(d);
            }
        }
        mConnectCallbackMap.put(device, callback);

        final BluetoothGatt gatt = device.getDevice().connectGatt(mContext, false, mGattCallback);

        if (gatt != null) {
            device.setConnectionState(BleDevice.CONNECTING);
            mGattMap.put(device.getAddress(), gatt);
            if (connectTimeout > 0) {
                mConnectTimeout = connectTimeout;
            }
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    callback.onStart(true, "Connection started successfully", device);
                }
            });
            Message msg = Message.obtain(mHandler, new Runnable() {
                @Override
                public void run() {
                    refreshDeviceCache(gatt);
                    gatt.close();
                    mGattMap.remove(device.getAddress());
                    device.setConnectionState(BleDevice.DISCONNECTED);
                    callback.onFailure(BleCallback.FAIL_CONNECTION_TIMEOUT, "Connection timed out", device);
                }
            });
            msg.obj = device.getAddress();
            mHandler.sendMessageDelayed(msg, mConnectTimeout);
        } else {
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    callback.onStart(false, "Unknown reason", device);
                }
            });
        }
    }

    @Override
    public void disconnect(String address) {
        if (!BluetoothAdapter.checkBluetoothAddress(address)) {
            return;
        }
        BluetoothGatt gatt = mGattMap.get(address);
        if (gatt == null) {
            return;
        }
        gatt.disconnect();
        //Remove connection timeout message if connection is establishing
        mHandler.removeCallbacksAndMessages(address);
        Map.Entry<BleDevice, BleConnectCallback> entry = findMapEntry(mConnectCallbackMap, address);
        if (entry != null) {
            BleDevice d = entry.getKey();
            if (d.isConnecting()) {
                removeDevice(d);
                d.setConnectionState(BleDevice.DISCONNECTED);
                BleConnectCallback callback = entry.getValue();
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        if (callback != null) {
                            callback.onFailure(BleCallback.FAIL_CONNECTION_CANCELED,
                                    "You have canceled this connection by calling disconnect() or disconnectAll()", d);
                        }
                    }
                });
            }
        }
    }

    @Override
    public void disconnectAll() {
        for (String address : mGattMap.keySet()) {
            disconnect(address);
        }
    }

    @Override
    public void notify(final BleDevice device, String serviceUuid, final String notifyUuid, final BleNotifyCallback callback) {
        checkNotNull(callback, BleNotifyCallback.class);
        if (!checkConnection(device, callback)) {
            return;
        }
        BluetoothGatt gatt = mGattMap.get(device.getAddress());
        if (!checkUuid(serviceUuid, notifyUuid, gatt, device, callback)) {
            return;
        }

        OperationIdentify identify = getOperationIdentifyFromMap(mNotifyCallbackMap, device.getAddress(), serviceUuid, notifyUuid);
        if (identify != null) {
            mNotifyCallbackMap.put(identify, callback);
        } else {
            mNotifyCallbackMap.put(new OperationIdentify(device.getAddress(), serviceUuid, notifyUuid), callback);
        }

        BluetoothGattService service = gatt.getService(UUID.fromString(serviceUuid));
        BluetoothGattCharacteristic characteristic = service.getCharacteristic(UUID.fromString(notifyUuid));
        boolean notifiable = (characteristic.getProperties() & BluetoothGattCharacteristic.PROPERTY_NOTIFY) > 0;
        boolean indicative = (characteristic.getProperties() & BluetoothGattCharacteristic.PROPERTY_INDICATE) > 0;
        if (!notifiable && !indicative) {
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    callback.onFailure(BleNotifyCallback.FAIL_NOTIFICATION_OR_INDICATION_UNSUPPORTED,
                            "This characteristic doesn't support notification or indication", device);
                }
            });
            return;
        }
        if (gatt.setCharacteristicNotification(characteristic, true)) {
            BluetoothGattDescriptor descriptor = characteristic.getDescriptor(UUID.fromString(CHARACTERISTIC_CONFIG));
            if (descriptor == null) {
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        callback.onNotifySuccess(notifyUuid, device);
                    }
                });
                return;
            }
            if (notifiable) {
                descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
            }
            if (indicative) {
                descriptor.setValue(BluetoothGattDescriptor.ENABLE_INDICATION_VALUE);
            }
            if (gatt.writeDescriptor(descriptor)) {
                return;
            }
        }
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                callback.onFailure(BleCallback.FAIL_OTHER, "Failed to setting characteristic notification", device);
            }
        });
    }

    @Override
    public void cancelNotify(BleDevice device, String serviceUuid, String notifyUuid) {
        OperationIdentify identify = getOperationIdentifyFromMap(mNotifyCallbackMap, device.getAddress(), serviceUuid, notifyUuid);
        if (identify != null) {
            mNotifyCallbackMap.remove(identify);
        }
        BluetoothGatt gatt = mGattMap.get(device.getAddress());
        if (gatt == null) {
            return;
        }
        BluetoothGattService service = gatt.getService(UUID.fromString(serviceUuid));
        if (service == null) {
            return;
        }
        BluetoothGattCharacteristic characteristic = service.getCharacteristic(UUID.fromString(notifyUuid));
        if (characteristic == null) {
            return;
        }
        boolean notifiable = (characteristic.getProperties() & BluetoothGattCharacteristic.PROPERTY_NOTIFY) > 0;
        boolean indicative = (characteristic.getProperties() & BluetoothGattCharacteristic.PROPERTY_INDICATE) > 0;
        if (notifiable || indicative) {
            if (gatt.setCharacteristicNotification(characteristic, false)) {
                BluetoothGattDescriptor descriptor = characteristic.getDescriptor(UUID.fromString(CHARACTERISTIC_CONFIG));
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
        checkNotNull(callback, BleReadCallback.class);
        if (!checkConnection(device, callback)) {
            return;
        }
        BluetoothGatt gatt = mGattMap.get(device.getAddress());
        if (!checkUuid(serviceUuid, readUuid, gatt, device, callback)) {
            return;
        }

        OperationIdentify identify = getOperationIdentifyFromMap(mReadCallbackMap, device.getAddress(), serviceUuid, readUuid);
        if (identify != null) {
            mReadCallbackMap.put(identify, callback);
        } else {
            mReadCallbackMap.put(new OperationIdentify(device.getAddress(), serviceUuid, readUuid), callback);
        }

        BluetoothGattService service = gatt.getService(UUID.fromString(serviceUuid));
        BluetoothGattCharacteristic characteristic = service.getCharacteristic(UUID.fromString(readUuid));
        boolean readable = (characteristic.getProperties() & BluetoothGattCharacteristic.PROPERTY_READ) > 0;
        if (!readable) {
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    callback.onFailure(BleCallback.FAIL_READ_UNSUPPORTED, "The characteristic is not readable", device);
                }
            });
            return;
        }
        if (!gatt.readCharacteristic(characteristic)) {
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    callback.onFailure(BleCallback.FAIL_OTHER, "Failed to initiate the read operation", device);
                }
            });
        }
    }

    @Override
    public void write(final BleDevice device, String serviceUuid, String writeUuid, byte[] data, final BleWriteCallback callback) {
        checkNotNull(callback, BleWriteCallback.class);
        int maxNumPerPack = MTU_MAX - ATT_OCCUPY_BYTES_NUM;
        int minNumPerPack = MTU_MIN - ATT_OCCUPY_BYTES_NUM;
        if (data == null || data.length < 1 || data.length > maxNumPerPack) {
            callback.onFailure(BleCallback.FAIL_OTHER, data == null ? "Data is null" :
                    "Data length must range from 1 to " + maxNumPerPack, device);
            return;
        }
        if (data.length > minNumPerPack) {
            Logger.w("Data length is greater than the default(20 Bytes), make sure  MTU >= " + (data.length + 3));
        }
        if (!checkConnection(device, callback)) {
            return;
        }
        BluetoothGatt gatt = mGattMap.get(device.getAddress());
        if (!checkUuid(serviceUuid, writeUuid, gatt, device, callback)) {
            return;
        }

        OperationIdentify identify = getOperationIdentifyFromMap(mWriteCallbackMap, device.getAddress(), serviceUuid, writeUuid);
        if (identify != null) {
            mWriteCallbackMap.put(identify, callback);
        } else {
            mWriteCallbackMap.put(new OperationIdentify(device.getAddress(), serviceUuid, writeUuid), callback);
        }

        BluetoothGattService service = gatt.getService(UUID.fromString(serviceUuid));
        BluetoothGattCharacteristic characteristic = service.getCharacteristic(UUID.fromString(writeUuid));
        boolean writable = (characteristic.getProperties() & (BluetoothGattCharacteristic.PROPERTY_WRITE |
                BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE)) > 0;
        if (!writable) {
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    callback.onFailure(BleCallback.FAIL_WRITE_UNSUPPORTED, "The characteristic is not writable", device);
                }
            });
            return;
        }
        if (!characteristic.setValue(data) || !gatt.writeCharacteristic(characteristic)) {
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    callback.onFailure(BleCallback.FAIL_OTHER, "Failed to initiate the write operation", device);
                }
            });
        }
    }

    @Override
    public void writeByBatch(BleDevice device, final String serviceUuid, final String writeUuid,
                             final byte[] writeData, int lengthPerPackage, final long writeDelay, final BleWriteByBatchCallback callback) {
        checkNotNull(callback, BleWriteByBatchCallback.class);
        if (writeData == null || writeData.length == 0) {
            callback.onFailure(BleCallback.FAIL_OTHER, "WriteData is null or no writing data", device);
            return;
        }
        int maxNumPerPack = MTU_MAX - ATT_OCCUPY_BYTES_NUM;
        if (lengthPerPackage < 1 || lengthPerPackage > maxNumPerPack) {
            callback.onFailure(BleCallback.FAIL_OTHER, "LengthPerPackage is invalid, it must range from 1 to " + maxNumPerPack, device);
            return;
        }
        final Queue<byte[]> queue = SplitBleDataUtils.getBatchData(writeData, lengthPerPackage);
        if (queue.size() <= 0) return;
        BleWriteCallback writeCallback = new BleWriteCallback() {
            @Override
            public void onWriteSuccess(byte[] data, final BleDevice device) {
                final BleWriteCallback c = this;
                final byte[] next = queue.poll();
                if (next != null) {
                    if (writeDelay <= 0) {
                        write(device, serviceUuid, writeUuid, next, c);
                    } else {
                        mHandler.postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                write(device, serviceUuid, writeUuid, next, c);
                            }
                        }, writeDelay);
                    }
                } else {
                    callback.writeByBatchSuccess(writeData, device);
                }
            }

            @Override
            public void onFailure(int failCode, String info, BleDevice device) {
                callback.onFailure(failCode, info, device);
            }
        };
        write(device, serviceUuid, writeUuid, queue.poll(), writeCallback);
    }

    @Override
    public void readRssi(final BleDevice device, final BleRssiCallback callback) {
        checkNotNull(callback, BleRssiCallback.class);
        if (!checkConnection(device, callback)) {
            return;
        }
        mRssiCallbackMap.put(device.getAddress(), callback);
        BluetoothGatt gatt = mGattMap.get(device.getAddress());
        if (gatt == null || !gatt.readRemoteRssi()) {
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    callback.onFailure(BleCallback.FAIL_OTHER, "Failed to request RSSI value", device);
                }
            });
        }
    }

    @SuppressWarnings("NewApi")
    @Override
    public void setMtu(final BleDevice device, int mtu, final BleMtuCallback callback) {
        checkNotNull(callback, BleMtuCallback.class);
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    callback.onFailure(BleCallback.FAIL_OTHER,
                            "The minimum android api version which setMtu supports is 21", device);
                }
            });
            return;
        }
        if (!checkConnection(device, callback)) {
            return;
        }
        mMtuCallbackMap.put(device.getAddress(), callback);
        BluetoothGatt gatt = mGattMap.get(device.getAddress());
        if (mtu < MTU_MIN) {
            mtu = MTU_MIN;
        } else if (mtu > MTU_MAX) {
            mtu = MTU_MAX;
        }
        if (gatt == null || !gatt.requestMtu(mtu)) {
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    callback.onFailure(BleCallback.FAIL_OTHER, "Failed to request MTU value", device);
                }
            });
        }
    }

    @Override
    public List<BleDevice> getConnectedDevices() {
        List<BleDevice> deviceList = new ArrayList<>();
        for (BleDevice d : mConnectCallbackMap.keySet()) {
            if (d.isConnected()) {
                deviceList.add(d);
            }
        }
        return deviceList;
    }

    @Override
    public boolean isConnecting(String address) {
        for (BleDevice d : mConnectCallbackMap.keySet()) {
            if (d.isConnecting() && d.getAddress().equals(address)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean isConnected(String address) {
        for (BleDevice d : mConnectCallbackMap.keySet()) {
            if (d.isConnected() && d.getAddress().equals(address)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public Map<ServiceInfo, List<CharacteristicInfo>> getDeviceServices(String address) {
        if (!BluetoothAdapter.checkBluetoothAddress(address)) {
            return null;
        }
        return mServicesMap.get(address);
    }

    @Override
    public BluetoothGatt getBluetoothGatt(String address) {
        return mGattMap.get(address);
    }

    @Override
    public void destroy() {
        mHandler.removeCallbacksAndMessages(null);
        disconnectAll();
        clearAllCallbacks();
    }

    private void checkNotNull(Object object, Class<?> clasz) {
        if (object == null) {
            throw new IllegalArgumentException(clasz.getSimpleName() + " is null");
        }
    }

    private boolean checkConnection(final BleDevice device, final BleCallback callback) {
        checkNotNull(device, BleDevice.class);
        boolean connected = device.isConnected();
        if (!connected) {
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    callback.onFailure(BleCallback.FAIL_DISCONNECTED,
                            "Connection is established yet", device);
                }
            });
        }
        return connected;
    }

    private boolean checkUuid(String serviceUuid, String charUuid, BluetoothGatt gatt,
                              final BleDevice device, final BleCallback callback) {
        if (gatt == null) {
            return false;
        }
        BluetoothGattService service = gatt.getService(UUID.fromString(serviceUuid));
        if (service == null) {
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    callback.onFailure(BleCallback.FAIL_SERVICE_NOT_FOUND,
                            "The specified service was not found in the remote device, " +
                                    "make sure service uuid is correct", device);
                }
            });
            return false;
        }
        BluetoothGattCharacteristic characteristic = service.getCharacteristic(UUID.fromString(charUuid));
        if (characteristic == null) {
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    callback.onFailure(BleCallback.FAIL_CHARACTERISTIC_NOT_FOUND_IN_SERVICE,
                            "The specified characteristic was not found in the service," +
                                    " make sure characteristic and service uuid is correct", device);
                }
            });
            return false;
        }
        return true;
    }

    private <T> Map.Entry<BleDevice, T> findMapEntry(Map<BleDevice, T> map, String address) {
        if (map == null || !BluetoothAdapter.checkBluetoothAddress(address)) {
            return null;
        }
        Set<Map.Entry<BleDevice, T>> set = map.entrySet();
        for (Map.Entry<BleDevice, T> entry : set) {
            if (entry.getKey().getAddress().equals(address)) {
                return entry;
            }
        }
        return null;
    }

    private <T> BleDevice getBleDeviceFromMap(String address, Map<BleDevice, T> map) {
        Map.Entry<BleDevice, T> entry = findMapEntry(map, address);
        if (entry != null) {
            return entry.getKey();
        }
        return null;
    }

    private <T> OperationIdentify getOperationIdentifyFromMap(Map<OperationIdentify, T> map, String address,
                                                              String serviceUuid, String characteristicUuid) {
        if (TextUtils.isEmpty(address) || TextUtils.isEmpty(serviceUuid) || TextUtils.isEmpty(characteristicUuid)) {
            return null;
        }
        for (OperationIdentify ui : map.keySet()) {
            if (ui.address.equalsIgnoreCase(address)
                    && ui.characteristicUuid.equalsIgnoreCase(characteristicUuid)
                    && ui.serviceUuid.equalsIgnoreCase(serviceUuid)) {
                return ui;
            }
        }
        return null;
    }

    private Map<ServiceInfo, List<CharacteristicInfo>> getServiceInfoMap(List<BluetoothGattService> gattServices) {
        Map<ServiceInfo, List<CharacteristicInfo>> servicesInfoMap = new HashMap<>();
        for (BluetoothGattService service : gattServices) {
            ServiceInfo serviceInfo = new ServiceInfo(service.getUuid());
            List<CharacteristicInfo> charactInfos = new ArrayList<>();
            for (BluetoothGattCharacteristic ch : service.getCharacteristics()) {
                boolean readable = (ch.getProperties() & BluetoothGattCharacteristic.PROPERTY_READ) > 0;
                boolean writable = (ch.getProperties() & (BluetoothGattCharacteristic.PROPERTY_WRITE |
                        BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE)) > 0;
                boolean notifiable = (ch.getProperties() & BluetoothGattCharacteristic.PROPERTY_NOTIFY) > 0;
                boolean indicate = (ch.getProperties() & BluetoothGattCharacteristic.PROPERTY_INDICATE) > 0;
                CharacteristicInfo charactInfo = new CharacteristicInfo(ch.getUuid(), readable, writable, notifiable, indicate);
                charactInfos.add(charactInfo);
            }
            servicesInfoMap.put(serviceInfo, charactInfos);
        }
        return servicesInfoMap;
    }

    private void removeDevice(BleDevice device) {
        mConnectCallbackMap.remove(device);
        mMtuCallbackMap.remove(device.getAddress());
        mRssiCallbackMap.remove(device.getAddress());
        mGattMap.remove(device.getAddress());
        mServicesMap.remove(device.getAddress());
        removeOperationIdentify(device.getAddress(), mReadCallbackMap);
        removeOperationIdentify(device.getAddress(), mWriteCallbackMap);
        removeOperationIdentify(device.getAddress(), mNotifyCallbackMap);
    }

    private <T> void removeOperationIdentify(String address, Map<OperationIdentify, T> map) {
        for (OperationIdentify ui : map.keySet()) {
            if (ui.address.equals(address)) {
                map.remove(ui);
            }
        }
    }

    private void clearAllCallbacks() {
        mConnectCallbackMap.clear();
        mMtuCallbackMap.clear();
        mNotifyCallbackMap.clear();
        mReadCallbackMap.clear();
        mWriteCallbackMap.clear();
        mRssiCallbackMap.clear();
        mGattMap.clear();
        mServicesMap.clear();
    }

    /**
     * Clears the ble device's internal cache and forces a refresh of the services from the
     * ble device.
     */
    private boolean refreshDeviceCache(BluetoothGatt gatt) {
        try {
            Method refresh = BluetoothGatt.class.getMethod("refresh");
            return refresh != null && (boolean) refresh.invoke(gatt);
        } catch (Exception e) {
            Logger.i("encounter an exception while refreshing device cache: " + e.getMessage());
            return false;
        }
    }

    private boolean isBluetoothEnable() {
        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        return adapter != null && adapter.isEnabled();
    }

    private boolean reachConnectionMaxNum() {
        return getConnectedDevices().size() >= MAX_CONNECTION_NUM;
    }

    private static final class OperationIdentify {
        String address;
        String serviceUuid;
        String characteristicUuid;

        OperationIdentify(String address, String serviceUuid, String characteristicUuid) {
            this.address = address;
            this.serviceUuid = serviceUuid;
            this.characteristicUuid = characteristicUuid;
        }
    }
}
