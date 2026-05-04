package com.ficat.easyble.gatt;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothGatt;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;

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
import com.ficat.easyble.utils.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;


/**
 * Created by pw on 2018/9/13.
 */
public final class BleGattImpl implements BleGatt, BleManager.BluetoothStateListen {
    private final Map<String, BleGattCommunicator> mBleGattCommunicatorMap;
    private final Handler mMainHandler;

    BleGattImpl() {
        mMainHandler = new Handler(Looper.getMainLooper());
        mBleGattCommunicatorMap = new ConcurrentHashMap<>();
    }

    @Override
    public void connect(long timeoutMillis, BleDevice device, BleConnectCallback callback) {
        // Check bluetooth and permission state
        boolean bluetoothOff = !BleManager.isBluetoothEnabled();
        boolean noPermissions = !BleManager.connectionPermissionGranted(BleManager.getInstance().getContext());
        if (bluetoothOff || noPermissions) {
            int code;
            if (bluetoothOff) {
                code = BleErrorCodes.BLUETOOTH_OFF;
            } else {
                code = BleErrorCodes.CONNECTION_PERMISSION_NOT_GRANTED;
            }
            mMainHandler.post(new Runnable() {
                @Override
                public void run() {
                    callback.onConnectionFailed(code, device);
                }
            });
            return;
        }

        // Check if connected device number reached max
        List<BleDevice> connectedDevices = getConnectedDevices();
        int connectedNum = connectedDevices.size();
        if (connectedNum >= BleGatt.MAX_CONNECTION_NUM) {
            // Exclude the target device
            for (BleDevice d : connectedDevices) {
                if (d == device || d.getAddress().equals(device.getAddress())) {
                    connectedNum -= 1;
                    break;
                }
            }
            if (connectedNum >= BleGatt.MAX_CONNECTION_NUM) {
                mMainHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        callback.onConnectionFailed(BleErrorCodes.CONNECTION_REACH_MAX_NUM, device);
                    }
                });
                return;
            }
        }

        // Start connection
        BleGattCommunicator communicator = mBleGattCommunicatorMap.get(device.getAddress());
        if (communicator == null) {
            communicator = new BleGattCommunicator(device);
            mBleGattCommunicatorMap.put(device.getAddress(), communicator);
        }
        if (communicator.getBleDevice() != device) {
            communicator.updateBleDevice(device);
        }

        // Android versions below 10 allow only one connection request at a time and queue all
        // subsequent requests. In Android 10 and higher, the system groups connection requests
        // for batched execution. That means, if Android versions below 10, we must wait for a
        // callback for a previous connection before we initiate a new connection request
        if (Build.VERSION.SDK_INT < 29) {
            for (BleDevice d : getConnectingDevices()) {
                if (!d.getAddress().equals(device.getAddress())) {
                    Logger.w("The current connection request may be blocked or fail" +
                            " because the local device is connecting to other devices");
                    break;
                }
            }
        }

        communicator.connectGatt(timeoutMillis, callback);
    }

    @Override
    public void disconnect(String address, boolean closeGattImmediately) {
        if (!BluetoothAdapter.checkBluetoothAddress(address)) {
            return;
        }
        BleGattCommunicator communicator = mBleGattCommunicatorMap.get(address);
        if (communicator == null) {
            return;
        }
        communicator.disconnect(closeGattImmediately, true);
    }

    @Override
    public void disconnectAll(boolean closeGattImmediately) {
        mMainHandler.removeCallbacksAndMessages(null);
        for (BleGattCommunicator communicator : mBleGattCommunicatorMap.values()) {
            communicator.disconnect(closeGattImmediately, true);
        }
    }

    @Override
    public void notify(BleDevice device, UUID serviceUuid, UUID notifyUuid, BleNotifyCallback callback) {
        BleGattCommunicator communicator = mBleGattCommunicatorMap.get(device.getAddress());
        if (communicator == null) {
            mMainHandler.post(new Runnable() {
                @Override
                public void run() {
                    callback.onNotifyFailed(BleErrorCodes.CONNECTION_NOT_ESTABLISHED, notifyUuid, device);
                }
            });
            return;
        }
        communicator.enableNotify(serviceUuid, notifyUuid, callback);
    }

    @Override
    public void cancelNotify(BleDevice device, UUID serviceUuid, UUID notifyUuid) {
        BleGattCommunicator communicator = mBleGattCommunicatorMap.get(device.getAddress());
        if (communicator == null) {
            return;
        }
        communicator.disableNotify(serviceUuid, notifyUuid);
    }

    @Override
    public void read(BleDevice device, UUID serviceUuid, UUID readUuid, BleReadCallback callback) {
        BleGattCommunicator communicator = mBleGattCommunicatorMap.get(device.getAddress());
        if (communicator == null) {
            mMainHandler.post(new Runnable() {
                @Override
                public void run() {
                    callback.onReadFailed(BleErrorCodes.CONNECTION_NOT_ESTABLISHED, readUuid, device);
                }
            });
            return;
        }
        communicator.read(serviceUuid, readUuid, callback);
    }

    @Override
    public void write(BleDevice device, UUID serviceUuid, UUID writeUuid, byte[] data, BleWriteCallback callback) {
        BleGattCommunicator communicator = mBleGattCommunicatorMap.get(device.getAddress());
        if (communicator == null) {
            mMainHandler.post(new Runnable() {
                @Override
                public void run() {
                    callback.onWriteFailed(BleErrorCodes.CONNECTION_NOT_ESTABLISHED, data, writeUuid, device);
                }
            });
            return;
        }
        communicator.write(serviceUuid, writeUuid, data, callback);
    }

    @Override
    public void writeByBatch(BleDevice device, UUID serviceUuid, UUID writeUuid, byte[] writeData,
                             int lengthPerBatch, long batchInterval, BleWriteByBatchCallback callback) {
        BleGattCommunicator communicator = mBleGattCommunicatorMap.get(device.getAddress());
        if (communicator == null) {
            mMainHandler.post(new Runnable() {
                @Override
                public void run() {
                    callback.onWriteBatchFailed(BleErrorCodes.CONNECTION_NOT_ESTABLISHED, 0, writeData, writeUuid, device);
                }
            });
            return;
        }
        communicator.writeByBatch(serviceUuid, writeUuid, writeData, lengthPerBatch, batchInterval, callback);
    }

    @Override
    public void readRssi(BleDevice device, BleRssiCallback callback) {
        BleGattCommunicator communicator = mBleGattCommunicatorMap.get(device.getAddress());
        if (communicator == null) {
            mMainHandler.post(new Runnable() {
                @Override
                public void run() {
                    callback.onRssiFailed(BleErrorCodes.CONNECTION_NOT_ESTABLISHED, device);
                }
            });
            return;
        }
        communicator.readRssi(callback);
    }

    @SuppressWarnings("NewApi")
    @Override
    public void setMtu(final BleDevice device, int mtu, final BleMtuCallback callback) {
        BleGattCommunicator communicator = mBleGattCommunicatorMap.get(device.getAddress());
        if (communicator == null) {
            mMainHandler.post(new Runnable() {
                @Override
                public void run() {
                    callback.onMtuFailed(BleErrorCodes.CONNECTION_NOT_ESTABLISHED, device);
                }
            });
            return;
        }
        communicator.requestMtu(mtu, callback);
    }

    @Override
    public List<BleDevice> getConnectedDevices() {
        List<BleDevice> deviceList = new ArrayList<>();
        for (BleGattCommunicator d : mBleGattCommunicatorMap.values()) {
            if (d.isConnected()) {
                deviceList.add(d.getBleDevice());
            }
        }
        return deviceList;
    }

    @Override
    public List<BleDevice> getConnectingDevices() {
        List<BleDevice> deviceList = new ArrayList<>();
        for (BleGattCommunicator d : mBleGattCommunicatorMap.values()) {
            if (d.isConnecting()) {
                deviceList.add(d.getBleDevice());
            }
        }
        return deviceList;
    }

    @Override
    public boolean isConnecting(String address) {
        for (BleGattCommunicator d : mBleGattCommunicatorMap.values()) {
            if (d.isConnecting() && d.getBleDevice().getAddress().equals(address)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean isConnected(String address) {
        for (BleGattCommunicator d : mBleGattCommunicatorMap.values()) {
            if (d.isConnected() && d.getBleDevice().getAddress().equals(address)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public BluetoothGatt getBluetoothGatt(String address) {
        for (BleGattCommunicator d : mBleGattCommunicatorMap.values()) {
            if (d.getBleDevice().getAddress().equals(address)) {
                return d.getBluetoothGatt();
            }
        }
        return null;
    }

    @Override
    public void onBluetoothStateChanged(int state) {
        if (state == BluetoothAdapter.STATE_OFF) {
            for (BleGattCommunicator d : mBleGattCommunicatorMap.values()) {
                d.onBluetoothOff();
            }
        }
    }

    @Override
    public void destroy(boolean callbackEnabledOnDestroy) {
        for (BleGattCommunicator communicator : mBleGattCommunicatorMap.values()) {
            communicator.disconnect(true, callbackEnabledOnDestroy);
        }
        mBleGattCommunicatorMap.clear();
    }
}
