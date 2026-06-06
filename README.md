# EasyBle
EasyBle is a lightweight Android BLE library designed to simplify Bluetooth Low Energy development. It has the following features:
- BLE scanning with configurable scan filters.
- Automatic connection and connection retry support.
- Comprehensive GATT operations, including RSSI reading, MTU configuration, Characteristic and Descriptor read/write, and notification/indication support.
- Sequential GATT operation queue with timeout protection (v3.4.0+ supports)
>On android12 or higher devices, BLE requires some permissions, and to use the latest features, please upgrade to version 3.3.0 or later

[中文文档](doc/README_CN.md)

## Gradle dependency
```gradle
allprojects {
    repositories {
        maven { url 'https://jitpack.io' }
    }
}

dependencies {
    implementation 'com.github.Ficat:EasyBle:v3.4.0'
}
```

## Permissions
|API version|Required Permissions|
|------|-----------|
|API31+|*"android.permission.BLUETOOTH_SCAN"*<br>*"android.permission.BLUETOOTH_CONNECT"*<br>*"android.permission.BLUETOOTH_ADVERTISE"*|
|API29+|*"android.permission.ACCESS_FINE_LOCATION"*<br>*"android.permission.ACCESS_FINE_LOCATION"*|
|API23+|*"android.permission.ACCESS_COARSE_LOCATION"* or <br>*"android.permission.ACCESS_FINE_LOCATION"*|
|API22-| None|

## Usage
### 1.Check if the device supports BLE, request BLE required permissions and turn on bluetooth.<br>

```java
        // Check if the device supports BLE
        boolean supportBle = BleManager.supportBle(context);

        // Request BLE permissions.
        List<String> permissions = BleManager.getBleRequiredPermissions();
        if (list.size() > 0) {
            requestPermissions(permissions);
        }
        
        // Turn on bluetooth
        boolean requestStart = BleManager.enableBluetooth(activity,requestCode);
        if(!requestStart) {
            // No BLE permissions or not support BLE
        }
```

### 2.Get ble manager and initialization

```java

        BleManager bleManager = BleManager
                        .getInstance()
                        .setGattOperationTimeout(600) // Gatt operation(like read/write) timeout, unit:ms
                        .setLog(true, "TAG")
                        .init(this.getApplication());

```

### 3.Scan
```java
        // Config scanOptions
        BleManager.ScanOptions scanOptions = BleManager.ScanOptions
                .newInstance()
                .scanPeriod(10000)// scan timeout, unit:ms
                //.scanDeviceName("deviceName", true) // The second param indicates whether to enable fuzzy match
                .scanDeviceName(null);

        bleManager.startScan(scanOptions, new BleScanCallback() {
            @Override
            public void onScanning(BleDevice device, int rssi, byte[] scanRecord) {
                String name = device.getName();
                String address = device.getAddress();
                
                // We can use BleManager#parseScanRecord to parse scanRecord
                //BleScanRecord bleScanRecord = BleManager.parseScanRecord(scanRecord);
            }

            @Override
            public void onScanStarted() {

            }

            @Override
            public void onScanFinished() {

            }

            @Override
            public void onScanFailed(int code) {
                switch (code) {
                    case BleErrorCodes.BLUETOOTH_OFF:
                        // Bluetooth turned off
                        break;
                    case BleErrorCodes.PERMISSION_MISSING:
                        // Scan permissions not granted
                        break;
                    case BleErrorCodes.SCAN_ALREADY_STARTED:
                        // Previous scan not finished
                        break;
                    case BleErrorCodes.SCAN_TOO_FREQUENTLY:
                        // Scan too frequently
                        break;
                    case BleErrorCodes.UNKNOWN:
                        // Failed to start scan because of unknown reason
                        break;
                }
            }
        });


        // Or use default ScanOptions to scan, you can call BleManager#setScanOptions() to set a default option
        bleManager.startScan(bleScanCallback);

```

Once target remote device has been discovered you can use stopScan() to stop scanning
```java
        bleManager.stopScan();
```

### 4.Connect
You can connect to remote device by device address or BleDevice object.<br>
**Note:** Android versions below 10 allow only one connection request at a time and queue all subsequent requests. In Android 10 and higher, the system groups connection requests for batched execution. That means, if Android versions below 10, we must wait for a callback for a previous connection before we initiate a new connection request
```java
       BleManager.ConnectionOptions connOptions = BleManager.ConnectionOptions
               .newInstance()
               .autoConnect(false) // auto-connection
               .retryWhenConnectionFailed(3, 5000) // retry if failed
               .connectionTimeout(12000);// connection timeout

       BleConnectCallback bleConnectCallback = new BleConnectCallback() {
            @Override
            public void onConnectionStarted(BleDevice device) {

            }

            @Override
            public void onConnected(BleDevice device) {

            }

            @Override
            public void onDisconnected(BleDevice device, int gattOperationStatus) {

            }

            @Override
            public void onConnectionFailed(int errCode, BleDevice device) {
                switch (errCode) {
                    case BleErrorCodes.BLUETOOTH_OFF:
                        // Bluetooth turned off
                        break;
                    case BleErrorCodes.PERMISSION_MISSING:
                        // Connection permissions not granted
                        break;
                    case BleErrorCodes.CONNECTION_REACH_MAX_NUM:
                        // Max connection num reached
                        break;
                    case BleErrorCodes.TIMEOUT:
                        // Connection timed out
                        break;
                    case BleErrorCodes.CONNECTION_CANCELED:
                        // Connection canceled
                        break;
                    case BleErrorCodes.CONNECTION_ALREADY_STARTED_OR_ESTABLISHED:
                        // Connection already started or established
                        break;
                    case BleErrorCodes.UNKNOWN:
                        // Unknown
                        break;
                    default:// GATT error code, like BleErrorCodes.GATT_FAILURE
                        break;
                }
            }
        };

       bleManager.connect(bleDevice, connOptions, bleConnectCallback);
       
       bleManager.connect(address, connOptions, bleConnectCallback);

       // Or use default ConnectionOptions, you can call BleManager#setConnectionOptions() to set a default option
       bleManager.connect(bleDevice, connectCallback);
```

Call one of the following methods to disconnect from remote device
```java

       // Disconnect from the remote device with BleDevice object, or mac address
       bleManager.disconnect(bleDevice);

       // Disconnect from the remote device by address. The second param indicates whether 
       // to close BluetoothGatt immediately without waiting for the system disconnection 
       // callback if the device is already connected
       bleManager.disconnect(address, true);

       // Disconnect all connected devices
       bleManager.disconnectAll();
```


### 5.Notify
Both notification and indication use the following method to set notification or indication
```java
       bleManager.notify(bleDevice, serviceUuid, notifyUuid, new BleNotifyCallback() {
            @Override
            public void onCharacteristicChanged(byte[] receivedData, UUID characteristicUuid, BleDevice device) {
                // Note that this is called from a non-UI thread
            }

            @Override
            public void onNotifySuccess(UUID characteristicUuid, BleDevice device) {

            }

            @Override
            public void onNotifyFailed(int errorCode, UUID characteristicUuid, BleDevice device) {
                switch (errorCode) {
                    case BleErrorCodes.CONNECTION_NOT_ESTABLISHED:
                        // Connection not established yet
                        break;
                    case BleErrorCodes.SERVICE_NOT_FOUND:
                        // Service not found in the remote device
                        break;
                    case BleErrorCodes.CHARACTERISTIC_NOT_FOUND_IN_SERVICE:
                        // Characteristic not found in specified service
                        break;
                    case BleErrorCodes.NOTIFICATION_OR_INDICATION_UNSUPPORTED:
                        // Characteristic not support notification or indication
                        break;
                    case BleErrorCodes.TIMEOUT:
                        // Enable-notification operation timed out
                        break;
                    case BleErrorCodes.UNKNOWN:
                        // Unknown
                        break;
                    default:
                        // GATT error code, like BleErrorCodes.GATT_FAILURE
                        break;
                }
            }
        });
```
Call cancelNotify() to cancel notification or indication
```java
       bleManager.cancelNotify(bleDevice, notifyUuid);
```

### 6.Write
```java
       bleManager.write(bleDevice, serviceUuid, writeUuid, data, new BleWriteCallback() {
            @Override
            public void onWriteSuccess(byte[] data, UUID characteristicUuid, BleDevice device) {

            }

            @Override
            public void onWriteFailed(int errCode, byte[] data, UUID characteristicUuid, BleDevice device) {
                switch (errCode) {
                    case BleErrorCodes.CONNECTION_NOT_ESTABLISHED:
                        // Connection not established yet
                        break;
                    case BleErrorCodes.SERVICE_NOT_FOUND:
                        // Service not found in the remote device
                        break;
                    case BleErrorCodes.CHARACTERISTIC_NOT_FOUND_IN_SERVICE:
                        // Characteristic not found in specified service
                        break;
                    case BleErrorCodes.WRITE_UNSUPPORTED:
                        // Characteristic not support writing
                        break;
                    case BleErrorCodes.DATA_LENGTH_GREATER_THAN_MTU:
                        // Data length is greater than MTU
                        break;
                    case BleErrorCodes.TIMEOUT:
                        // Write-characteristic operation timed out
                        break;
                    case BleErrorCodes.UNKNOWN:
                        // Unknown
                        break;
                    default:
                        // GATT error code, like BleErrorCodes.GATT_FAILURE
                        break;
                }
            }
        });
```

If the length of the data you wanna deliver to remote device is larger than MTU(default 20), call the following method to write by batch
```java
       bleManager.writeByBatch(bleDevice, serviceUuid, writeUuid, data, lengthPerPackage, new  BleWriteByBatchCallback() {
            @Override
            public void onWriteBatchSuccess(byte[] originalData, UUID characteristicUuid, BleDevice device) {

            }

            @Override
            public void onWriteBatchProgress(float progress, UUID characteristicUuid, BleDevice device) {

            }

            @Override
            public void onWriteBatchFailed(int errCode, int writtenLength, byte[] originalData, UUID characteristicUuid, BleDevice device) {
                switch (errCode) {
                    case BleErrorCodes.CONNECTION_NOT_ESTABLISHED:
                        // Connection not established yet
                        break;
                    case BleErrorCodes.SERVICE_NOT_FOUND:
                        // Service not found in the remote device
                        break;
                    case BleErrorCodes.CHARACTERISTIC_NOT_FOUND_IN_SERVICE:
                        // Characteristic not found in specified service
                        break;
                    case BleErrorCodes.WRITE_UNSUPPORTED:
                        // Characteristic not support writing
                        break;
                    case BleErrorCodes.DATA_LENGTH_GREATER_THAN_MTU:
                        // Data length(lengthPerPackage) is greater than MTU
                        break;
                    case BleErrorCodes.TIMEOUT:
                        // Write-characteristic operation timed out
                        break;
                    case BleErrorCodes.UNKNOWN:
                        // Unknown
                        break;
                    default:
                        // GATT error code, like BleErrorCodes.GATT_FAILURE
                        break;
                }
            }
        });
```

### 7.Read
```java
       bleManager.read(bleDevice, serviceUuid, writeUuid, data, new BleReadCallback() {
            @Override
            public void onReadSuccess(byte[] readData, UUID characteristicUuid, BleDevice device) {

            }

            @Override
            public void onReadFailed(int errorCode, UUID characteristicUuid, BleDevice device) {
                switch (errorCode) {
                    case BleErrorCodes.CONNECTION_NOT_ESTABLISHED:
                        // Connection not established yet
                        break;
                    case BleErrorCodes.SERVICE_NOT_FOUND:
                        // Service not found in the remote device
                        break;
                    case BleErrorCodes.CHARACTERISTIC_NOT_FOUND_IN_SERVICE:
                        // Characteristic not found in specified service
                        break;
                    case BleErrorCodes.READ_UNSUPPORTED:
                        // Characteristic not support reading
                        break;
                    case BleErrorCodes.UNKNOWN:
                        // Unknown
                        break;
                    case BleErrorCodes.TIMEOUT:
                        // Read-characteristic operation timed out
                        break;
                    default:
                        // GATT error code, like BleErrorCodes.GATT_FAILURE
                        break;
                }
            }
        });
```

### 8.Destroy
You must call destroy() to release some resources after BLE communication end
```java
       bleManager.destroy();

       // The first params indicates whether scan-callbacks should be invoked during the 
       // destroy process. The second params indicates whether gatt-operation-callbacks(such 
       // as connect, read, write) should be invoked during the destroy process.Usually, in 
       // these callback, we will perform some operations such as updating UI and reconnecting.
       // Before doing this, especially the params is true, we must ensure BleManger is not 
       // destroyed by checking that BleManager#getContext() does not return null
       bleManager.destroy(true, true);
```

### Other APIs
|Method|Description|
|------|-----------|
|**readRssi**(BleDevice device, BleRssiCallback callback)|Read the remote device rssi(Received Signal Strength Indication)|
|**setMtu**(BleDevice device, int mtu, BleMtuCallback callback)|Set MTU (Maximum Transmission Unit)|
|**descriptorRead**(BleDevice device, UUID serviceUuid, UUID characteristicUuid, UUID descriptorUuid, BleDescriptorReadCallback callback)|Reads the value for a given descriptor from the associated remote device|
|**descriptorWrite**(BleDevice device, UUID serviceUuid, UUID characteristicUuid, UUID descriptorUuid, byte[] data, BleDescriptorWriteCallback callback)|Write the value of a given descriptor to the associated remote device|
|**readPhy**(BleDevice device, BlePhyReadCallback callback)|Read the current transmitter PHY and receiver PHY of the connection|
|**setPreferencePhy**(BleDevice device, int txPhy, int rxPhy, int phyOptions, BlePhyPreferenceCallback callback)|Set the preferred connection PHY. This is just a recommendation, whether the PHY change will happen depends on other applications preferences, local and remote controller capabilities|
|**requestConnectionPriority**(BleDevice device, int connPriority, BleConnectionPriorityCallback callback)|Request a connection parameter update|
|isScanning()|Is Scanning?|
|isConnected(String address)|Check if the local bluetooth has connected to the remote device|
|isConnecting(String address)|Check if local device is connecting with the remote device|
|getConnectedDevices()|Get connected devices|
|getConnectingDevices()|Get connecting devices|
|getDeviceServices(String address)|Get all services that remote device supports,note that it may return null. [See example](doc/README_MORE.md)|
|*supportBle(Context context)*|Check if this device supports ble|
|*isBluetoothOn()*|Check if local bluetooth is enabled. From 3.3.0, please use isBluetoothEnabled()|
|*parseScanRecord(byte[] scanRecord)*|Parse scan-record bytes, return a BleScanRecord instance|
|*isCharacteristicReadable(BluetoothGattCharacteristic ch)*|Is the characteristic readable?|
|*isCharacteristicWritable(BluetoothGattCharacteristic ch)*|Is the characteristic writable?|
|*isCharacteristicNotifiable(BluetoothGattCharacteristic ch)*|Does the characteristic support notification?|
|*isCharacteristicIndicative(BluetoothGattCharacteristic ch)*|Does the characteristic support indication?|
|*isAddressValid(String address)*|Check if the address is valid. From 3.3.0, please use isValidAddress()|
|*getValidMtuRange()*|Get valid MTU range, this method returns an array(int[2], int[0]= MinMtu, int[1]=MaxMtu) |
|*getBleRequiredPermissions()*|Get all BLE required permissions. Lower version may not require any permissions, so do not forget to check the length of permissionList|
|*allBlePermissionsGranted(Context context)*|Check if all BLE required permissions have been granted|
|*scanPermissionGranted(Context context)*|Check if scan permissions have been granted|
|*connectionPermissionGranted(Context context)*|Check if connection permissions have been granted|
|*enableBluetooth(Activity activity, int requestCode)*|Turn on local bluetooth, calling the method will show users a request dialog to grant or reject,so you can get the result from Activity#onActivityResult().<br>Note that on Android12 or higher devices, this method can work only under the condition that BLE permissions have been granted|
|getScanOptions()|Get the scan option you set or default|
|getConnectOptions()|Get the connection option you set or default|
|getBluetoothGatt(String address)|Get the BluetoothGatt object of specific remote device,it will return null if the connection isn't established|



## License
```
Copyright 2018 Ficat

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
```