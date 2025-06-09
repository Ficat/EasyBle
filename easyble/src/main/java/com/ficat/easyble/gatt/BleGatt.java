package com.ficat.easyble.gatt;


import android.bluetooth.BluetoothGatt;

import com.ficat.easyble.BleDevice;
import com.ficat.easyble.gatt.callback.BleConnectCallback;
import com.ficat.easyble.gatt.callback.BleMtuCallback;
import com.ficat.easyble.gatt.callback.BleNotifyCallback;
import com.ficat.easyble.gatt.callback.BleReadCallback;
import com.ficat.easyble.gatt.callback.BleRssiCallback;
import com.ficat.easyble.gatt.callback.BleWriteByBatchCallback;
import com.ficat.easyble.gatt.callback.BleWriteCallback;

import java.util.List;
import java.util.UUID;

public interface BleGatt {
    int MAX_CONNECTION_NUM = 7;
    int MTU_MAX = 512;
    int MTU_MIN = 23;
    int ATT_OCCUPY_BYTES_NUM = 3;
    String CHARACTERISTIC_CONFIG = "00002902-0000-1000-8000-00805f9b34fb";

    void connect(int timeoutMills, BleDevice device, BleConnectCallback callback);

    void disconnect(String address);

    void disconnectAll();

    void notify(BleDevice device, UUID serviceUuid, UUID notifyUuid, BleNotifyCallback callback);

    void cancelNotify(BleDevice device, UUID serviceUuid, UUID characteristicUuid);

    void read(BleDevice device, UUID serviceUuid, UUID readUuid, BleReadCallback callback);

    void write(BleDevice device, UUID serviceUuid, UUID writeUuid, byte[] data, BleWriteCallback callback);

    void writeByBatch(BleDevice device, UUID serviceUuid, UUID writeUuid, byte[] data,
                      int lengthPerPackage, long writeDelay, BleWriteByBatchCallback callback);

    void readRssi(BleDevice device, BleRssiCallback callback);

    void setMtu(BleDevice device, int mtu, BleMtuCallback callback);

    List<BleDevice> getConnectedDevices();

    BluetoothGatt getBluetoothGatt(String address);

    boolean isConnecting(String address);

    boolean isConnected(String address);

    void destroy();
}
