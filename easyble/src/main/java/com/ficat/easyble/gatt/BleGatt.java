package com.ficat.easyble.gatt;


import android.bluetooth.BluetoothGatt;

import com.ficat.easyble.BleDevice;
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

import java.util.List;
import java.util.UUID;

public interface BleGatt {
    long DEFAULT_OPERATION_TIMEOUT_MILLIS = 600; // Default operation timeout
    int MAX_CONNECTION_NUM = 7;
    int MTU_MAX = 515;
    int MTU_MIN = 23;
    int ATT_OCCUPY_BYTES_NUM = 3;
    String CHARACTERISTIC_CONFIG_DESCRIPTOR_UUID = "00002902-0000-1000-8000-00805f9b34fb";

    void connect(long timeoutMillis, int retryCount, long retryDelay, boolean autoConnect,
                 BleDevice device, BleConnectCallback callback);

    void disconnect(String address, boolean closeGattImmediately);

    void disconnectAll(boolean closeGattImmediately);

    void notify(BleDevice device, UUID serviceUuid, UUID notifyUuid, BleNotifyCallback callback);

    void cancelNotify(BleDevice device, UUID serviceUuid, UUID characteristicUuid);

    void read(BleDevice device, UUID serviceUuid, UUID readUuid, BleReadCallback callback);

    void write(BleDevice device, UUID serviceUuid, UUID writeUuid, byte[] data, BleWriteCallback callback);

    void writeByBatch(BleDevice device, UUID serviceUuid, UUID writeUuid, byte[] data,
                      int lengthPerBatch, long batchInterval, BleWriteByBatchCallback callback);

    void readRssi(BleDevice device, BleRssiCallback callback);

    void setMtu(BleDevice device, int mtu, BleMtuCallback callback);

    void descriptorWrite(BleDevice device, UUID serviceUuid, UUID characteristicUuid,
                         UUID descriptorUuid, byte[] data, BleDescriptorWriteCallback callback);

    void descriptorRead(BleDevice device, UUID serviceUuid, UUID characteristicUuid,
                        UUID descriptorUuid, BleDescriptorReadCallback callback);

    void readPhy(BleDevice device, BlePhyReadCallback callback);

    void setPreferencePhy(BleDevice device, int txPhy, int rxPhy, int phyOptions,
                          BlePhyPreferenceCallback callback);

    void requestConnectionPriority(BleDevice device, int connPriority,
                                   BleConnectionPriorityCallback callback);

    List<BleDevice> getConnectedDevices();

    List<BleDevice> getConnectingDevices();

    BluetoothGatt getBluetoothGatt(String address);

    boolean isConnecting(String address);

    boolean isConnected(String address);

    void destroy(boolean callbackEnabledOnDestroy);
}
