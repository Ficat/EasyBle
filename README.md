# EasyBle
  EasyBle is a framework used for android BLE, it makes android Ble operation simpler and supports basic BLE operations
>On android12 or higher devices, BLE requires some permissions, please use or update it to the newest version(3.1.x)

[中文文档](doc/README_CN.md)

## Gradle dependency
```gradle
allprojects {
    repositories {
        maven { url 'https://jitpack.io' }
    }
}

dependencies {
    implementation 'com.github.Ficat:EasyBle:v3.1.1'
}
```

## Usage
 The framework uses BleManager to manager BLE
### 1.Check if the device supports BLE, request BLE required permissions and turn on bluetooth.<br>
[See BLE permission details](doc/README_MORE.md)
```java
        // Check if the device supports BLE
        BleManager.supportBle(context);

        // Request BLE permissions. On Android 12 or higher devices, most BLE operations such
        // as enabling Bluetooth, notifications, and write/read require these permissions, so
        // this step is necessary. You can get all BLE permissions by BleManager#getBleRequiredPermissions().
        List<String> permissions = BleManager.getBleRequiredPermissions();
        if (list.size() > 0) { // Lower version may not require any permissions
            requestPermissions(permissions);
        }

        // Is Bluetooth turned on?
        BleManager.isBluetoothOn();
        
        // If Bluetooth is turned off, you can call BleManager#enableBluetooth() to turn on
        // bluetooth with a request dialog, and you will receive the result from the method
        // onActivityResult() of this activity. Note that the method requires BLE permissions,
        // so do not forget to request it and ensure all permissions have been granted.
        boolean requestStart = BleManager.enableBluetooth(activity,requestCode);
        if(!requestStart) {
            // No BLE permissions or not support BLE
        }
```

### 2.Get ble manager and initialization

```java

        // Scan or connection option is not necessary, if you don't set, The default
        // configuration will be applied
        BleManager.ScanOptions scanOptions = BleManager.ScanOptions
                .newInstance()
                .scanPeriod(10000)
                .scanDeviceName(null);

        BleManager.ConnectionOptions connOptions = BleManager.ConnectionOptions
                .newInstance()
                .connectionPeriod(12000);

        BleManager bleManager = BleManager
                        .getInstance()
                        .setScanOptions(scanOptions)
                        .setConnectionOptions(connOptions)
                        .setLog(true, "TAG")
                        .init(this.getApplication());//Init() requires context, to avoid memory leak, you'd better use Application instance

```

### 3.Scan
On API23+ or higher devices, scan requires some permissions, so ensure all BLE permissions have been granted.
[How to use BleDevice to carry extra info](doc/README_MORE.md).
```java
        bleManager.startScan(new BleScanCallback() {
            @Override
            public void onScanning(BleDevice device, int rssi, byte[] scanRecord) {
                String name = device.getName();
                String address = device.getAddress();
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
                    case BleScanCallback.BLUETOOTH_OFF:
                        // Bluetooth turned off
                        break;
                    case BleScanCallback.SCAN_PERMISSION_NOT_GRANTED:
                        // Scan permissions not granted
                        break;
                    case BleScanCallback.PREVIOUS_SCAN_NOT_FINISHED:
                        // Previous scan not finished
                        break;
                    case BleScanCallback.SCAN_FAILED:
                        // Failed to start scan because of unknown reason
                        break;
                }
            }
        });

        // Start scan with specified scanOptions
        bleManager.startScan(scanOptions, bleScanCallback);

```

Once target remote device has been discovered you can use stopScan() to stop scanning
```java
        bleManager.stopScan();
```

### 4.Connect
You can connect to remote device by device address or BleDevice object. Like scan, now connection also requires permissions.
```java

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
                    case BleErrorCodes.CONNECTION_PERMISSION_NOT_GRANTED:
                        // Connection permissions not granted
                        break;
                    case BleErrorCodes.CONNECTION_REACH_MAX_NUM:
                        // Max connection num reached
                        break;
                    case BleErrorCodes.CONNECTION_TIMEOUT:
                        // Connection timed out
                        break;
                    case BleErrorCodes.CONNECTION_CANCELED:
                        // Connection canceled
                        break;
                    case BleErrorCodes.UNKNOWN:
                        // Unknown
                        break
                    default:
                        break;
                }
            }
        };

       bleManager.connect(bleDevice, bleConnectCallback);

       // Connect with mac address
       bleManager.connect(address, bleConnectCallback);

       // Second param:  Select a specified connection option
       bleManager.connect(bleDevice, connectionOptions, connectCallback);
```

Call one of the following methods to disconnect from remote device
```java

       // Disconnect from the remote device by BleDevice object
       bleManager.disconnect(bleDevice);
	   
       // Disconnect from the remote device by address
       bleManager.disconnect(address);

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
                    case BleErrorCodes.UNKNOWN:
                        // Unknown
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
                    case BleErrorCodes.UNKNOWN:
                        // Unknown
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
                    case BleErrorCodes.UNKNOWN:
                        // Unknown
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
                }
            }
        });
```

### 8.Destroy
You must call destroy() to release some resources after BLE communication end
```java
       bleManager.destroy();

```

### Other api
|Method|Description|
|------|-----------|
|**readRssi**(BleDevice device, BleRssiCallback callback)|Read the remote device rssi(Received Signal Strength Indication)|
|**setMtu**(BleDevice device, int mtu, BleMtuCallback callback)|Set MTU (Maximum Transmission Unit)|
|isScanning()|Is Scanning?|
|isConnected(String address)|Check if the local bluetooth has connected to the remote device|
|isConnecting(String address)|Check if local device is connecting with the remote device|
|getConnectedDevices()|Get connected devices|
|getDeviceServices(String address)|Get all services that remote device supports,note that it may return null.|
|*supportBle(Context context)*|Check if this device supports ble|
|*isBluetoothOn()*|Check if local bluetooth is enabled|
|*isAddressValid(String address)*|Check if the address is valid|
|*getValidMtuRange()*|Get valid MTU range, this method returns an array(int[2], int[0]= MinMtu, int[1]=MaxMtu) |
|*getBleRequiredPermissions()*|Get all BLE required permissions. Lower version may not require any permissions, so do not forget to check the length of permissionList|
|*allBlePermissionsGranted(Context context)*|Check if all BLE required permissions have been granted|
|*scanPermissionGranted(Context context)*|Check if scan permissions have been granted|
|*connectionPermissionGranted(Context context)*|Check if connection permissions have been granted|
|*enableBluetooth(Activity activity, int requestCode)*|Turn on local bluetooth, calling the method will show users a request dialog to grant or reject,so you can get the result from Activity#onActivityResult().<br>Note that on Android12 or higher devices, this method can work only under the condition that BLE permissions have been granted|
|*toggleBluetooth(boolean enable)*|Turn on or off local bluetooth directly without showing users a request dialog.<br>Note that this method, like *enableBluetooth(Activity activity, int requestCode)*, also requires BLE permissions. In addition, now it may not work on some devices, especially high version devices|
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