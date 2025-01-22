# EasyBle
  EasyBle is a framework used for android BLE, this framework makes android Ble operation simpler and supports basic BLE operations, besides, it also support batch writing data and multi connection
>The version 1.0.x is no longer maintained , please use or update to the newest version(2.0.x)

[中文文档](doc/README_CN.md)

## Gradle dependency
```gradle
allprojects {
    repositories {
        maven { url 'https://jitpack.io' }
    }
}

dependencies {
    implementation 'com.github.Ficat:EasyBle:v2.0.2'
}
```

## Usage
 The framework uses BleManager to manager BLE
### 1.Check if the device supports BLE, request BLE required permissions and turn on bluetooth. <br>
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
                .connectTimeout(12000);

        BleManager bleManager = BleManager
                        .getInstance()
                        .setScanOptions(scanOptions)
                        .setConnectionOptions(connOptions)
                        .setLog(true, "TAG")
                        .init(this.getApplication());//Init() requires context, to avoid memory leak, you'd better use Application instance

```

### 3.Scan
On API23+ or higher devices, scan requires some permissions, so ensure all BLE permissions have been granted. <br>
[How to use BleDevice to carry extra info](doc/README_MORE.md).
```java
        bleManager.startScan(new BleScanCallback() {
            @Override
            public void onLeScan(BleDevice device, int rssi, byte[] scanRecord) {
                String name = device.name;
                String address = device.address;
            }

            @Override
            public void onStart(boolean startScanSuccess, String info) {
                if (startScanSuccess) {
                    // Start scan successfully
                } else {
                    // Fail to start scan, you can see details from 'info'
                    String failReason = info;
                }
            }

            @Override
            public void onFinish() {
               
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
You can connect to remote device by device address or BleDevice object. Like scan, now connection also requires permissions.<br>
 By default, all operation(like connect/notify/read/write/setMtu/readRssi and so on) callbacks run in UI-Thread, but you can select a thread to run them[How to select a thread to run all operation callbacks](doc/README_MORE.md).
```java

       BleConnectCallback bleConnectCallback = new BleConnectCallback() {
            @Override
            public void onStart(boolean startSuccess, String info, BleDevice device) {
                if (startSuccess) {
                    // Start to connect successfully
                } else {
                    // Fail to start connection, see details from 'info'
                }
            }

            @Override
            public void onFailure(int failureCode, String info, BleDevice device) {
                switch (failureCode) {
                    case BleCallback.FAILURE_CONNECTION_TIMEOUT:
                        // Connection timed out
                        break;
                    case BleCallback.FAILURE_CONNECTION_CANCELED:
                        // Connection canceled. Occurred when a connection is in progress,
                        // but you call disconnect() or disconnectAll().
                        break;
                    case BleCallback.FAILURE_CONNECTION_FAILED:
                        // Connection failed
                        break;
                    default:
                        // Other reason
                        break;
                }
            }

            @Override
            public void onConnected(BleDevice device) {
                // Connection established
            }

            @Override
            public void onDisconnected(String info, int status, BleDevice device) {

            }
        };

       bleManager.connect(bleDevice, bleConnectCallback);

       // Connect with mac address
       bleManager.connect(address, bleConnectCallback);

       // Second param:  Select a specified connection option
       // Last param:    Select a thread to run all operation callbacks, like connect/notify/read/write
       //                and so on. If it's null, all callbacks will run in UI-Thread (By default, it's null)
       bleManager.connect(bleDevice, connectionOptions, connectCallback, new BleHandlerThread("BleThread"));
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
            public void onCharacteristicChanged(byte[] data, BleDevice device) {
              
            }

            @Override
            public void onNotifySuccess(String notifySuccessUuid, BleDevice device) {

            }

            @Override
            public void onFailure(int failureCode, String info, BleDevice device) {
                switch (failureCode) {
                    case BleCallback.FAILURE_CONNECTION_NOT_ESTABLISHED:
                        // Connection is not established yet, or disconnected from the remote device
                        break;
                    case BleCallback.FAILURE_SERVICE_NOT_FOUND:
                        // Service not found in the remote device
                        break;
                    case BleCallback.FAILURE_CHARACTERISTIC_NOT_FOUND_IN_SERVICE:
                        // Characteristic not found in specified service
                        break;
                    case BleCallback.FAILURE_NOTIFICATION_OR_INDICATION_UNSUPPORTED:
                        // Characteristic not support notification or indication
                        break;
                    case BleCallback.FAILURE_OTHER:
                        // Other reason
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
            public void onWriteSuccess(byte[] data, BleDevice device) {

            }

            @Override
            public void onFailure(int failureCode, String info, BleDevice device) {
                switch (failureCode) {
                    case BleCallback.FAILURE_CONNECTION_NOT_ESTABLISHED:
                        // Connection is not established yet, or disconnected from the remote device
                        break;
                    case BleCallback.FAILURE_SERVICE_NOT_FOUND:
                        // Service not found in the remote device
                        break;
                    case BleCallback.FAILURE_CHARACTERISTIC_NOT_FOUND_IN_SERVICE:
                        // Characteristic not found in specified service
                        break;
                    case BleCallback.FAILURE_WRITE_UNSUPPORTED:
                        // Characteristic not support writing
                        break;
                    case BleCallback.FAILURE_OTHER:
                        // Other reason (like invalid data length and so on, see 'info').
                        break;
                }
            }
        });
```

If the length of the data you wanna deliver to remote device is larger than MTU(default 20), call the following method to write by batch
```java
       bleManager.writeByBatch(bleDevice, serviceUuid, writeUuid, data, lengthPerPackage, new  BleWriteByBatchCallback() {
            @Override
            public void writeByBatchSuccess(byte[] data, BleDevice device) {

            }

            @Override
            public void onFailure(int failureCode, String info, BleDevice device) {
                switch (failureCode) {
                    case BleCallback.FAILURE_CONNECTION_NOT_ESTABLISHED:
                        // Connection is not established yet, or disconnected from the remote device
                        break;
                    case BleCallback.FAILURE_SERVICE_NOT_FOUND:
                        // Service not found in the remote device
                        break;
                    case BleCallback.FAILURE_CHARACTERISTIC_NOT_FOUND_IN_SERVICE:
                        // Characteristic not found in specified service
                        break;
                    case BleCallback.FAILURE_WRITE_UNSUPPORTED:
                        // Characteristic not support writing
                        break;
                    case BleCallback.FAILURE_OTHER:
                        // Other reason (like invalid data length, or invalid package length and so on).
                        break;
                }
            }
        });
```

### 7.Read
```java
       bleManager.read(bleDevice, serviceUuid, writeUuid, data, new BleReadCallback() {
            @Override
            public void onReadSuccess(byte[] data, BleDevice device) {

            }

            @Override
            public void onFailure(int failureCode, String info, BleDevice device) {
                switch (failureCode) {
                    case BleCallback.FAILURE_CONNECTION_NOT_ESTABLISHED:
                        // Connection is not established yet, or disconnected from the remote device
                        break;
                    case BleCallback.FAILURE_SERVICE_NOT_FOUND:
                        // Service not found in the remote device
                        break;
                    case BleCallback.FAILURE_CHARACTERISTIC_NOT_FOUND_IN_SERVICE:
                        // Characteristic not found in specified service
                        break;
                    case BleCallback.FAILURE_READ_UNSUPPORTED:
                        // Characteristic not support reading
                        break;
                    case BleCallback.FAILURE_OTHER:
                        // Other reason
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
|getConnectedDevices()|Get connected devices list|
|getDeviceServices(BleDevice device);<br>getDeviceServices(String address)|Get service information which the remote device supports,note that it may return null. you will get a **List<ServiceInfo>**<br>ServiceInfo: it contains service uuid and CharacteristicInfo.|
|*supportBle(Context context)*|Check if this device supports ble|
|*isBluetoothOn()*|Check if local bluetooth is enabled|
|*isAddressValid(String address)*|Check if the address is valid|
|*getBleRequiredPermissions()*|Get all BLE required permissions. Lower version may not require any permissions, so do not forget to check the length of permissionList|
|*allBlePermissionsGranted(Context context)*|Check if all BLE required permissions have been granted|
|*scanPermissionGranted(Context context)*|Check if scan-permission has been granted|
|*connectionPermissionGranted(Context context)*|Check if connection-permission has been granted|
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