# EasyBle
  EasyBle is a framework used for android BLE, this framework makes android Ble operation simpler and supports basic BLE operations, besides, it also support batch writing data and multi connection
>The version 1.0.x is no longer maintained , please use or update to the newest version(2.0.x)

[中文文档](https://github.com/Ficat/EasyBle/blob/master/README_CN.md)

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
### 1.Check if the device supports BLE and turn on bluetooth
```java
        //check if the device supports BLE
        BleManager.supportBle(context);

        //is Bluetooth turned on?
        BleManager.isBluetoothOn();
        
        //If Bluetooth is turned off, you should call BleManager#enableBluetooth() to
        //turn on bluetooth with a request dialog, and you will receive the result from
        //the method onActivityResult() of this activity
        BleManager.enableBluetooth(activity,requestCode);
```

### 2.Get ble manager and initialization

```java

        //scan/connection options is not necessary, if you don't set,
        //it will use default config
        BleManager.ScanOptions scanOptions = BleManager.ScanOptions
                .newInstance()
                .scanPeriod(10000)
                .scanDeviceName(null);

        BleManager.ConnectOptions connectOptions = BleManager.ConnectOptions
                .newInstance()
                .connectTimeout(12000);

        BleManager bleManager = BleManager
                        .getInstance()
                        .setScanOptions(scanOptions)//it is not necessary
                        .setConnectionOptions(connectOptions)//like scan options
                        .setLog(true, "TAG")
                        .init(this.getApplication());//Context is needed here,do not use Activity,which can cause Activity leak

```

### 3.Scan
If sdk version >=23, scanning must have location permission，if Android version sdk version >=29(Android10), scanning must have fine location permission(*Manifest.permission.ACCESS_FINE_LOCATION*)
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
                    //start scan successfully
                } else {
                    //fail to start scan, you can see details from 'info'
                    String failReason = info;
                }
            }

            @Override
            public void onFinish() {
               
            }
        });

        //start scan with specified scanOptions
        bleManager.startScan(scanOptions, bleScanCallback);

```

Once target remote device has been discovered you can use stopScan() to stop scanning
```java
        bleManager.stopScan();
```

### 4.Connect
You can connect to remote device by device address or BleDevice object
```java

       BleConnectCallback bleConnectCallback = new BleConnectCallback() {
            @Override
            public void onStart(boolean startConnectSuccess, String info, BleDevice device) {
                if (startConnectSuccess) {
                    //start to connect successfully
                } else {
                    //fail to start connection, see details from 'info'
                    String failReason = info;
                }
            }

            @Override
            public void onFailure(int failCode, String info, BleDevice device) {
                if(failCode == BleConnectCallback.FAIL_CONNECT_TIMEOUT){
                    //connection timeout
                }else{
                    //connection fail due to other reasons
                }

            }

             @Override
             public void onConnected(BleDevice device) {

             }

             @Override
             public void onDisconnected(String info, int status, BleDevice device) {

             }
        };

       bleManager.connect(bleDevice, bleConnectCallback);
       //connect with specified connectOptions
       bleManager.connect(bleDevice, connectOptions, bleConnectCallback);

       //connect with mac address
       bleManager.connect(address, bleConnectCallback);
       bleManager.connect(address, connectOptions, bleConnectCallback);

```

Use one of the following methods to disconnect from remote device
```java

       //disconnect from the specific remote device by BleDevice object
       bleManager.disconnect(bleDevice);
	   
       //disconnect from the specific remote device by address
       bleManager.disconnect(address);

       //disconnect all connected devices
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
            public void onFailure(int failCode, String info, BleDevice device) {
                switch (failCode) {
                    case BleCallback.FAIL_DISCONNECTED://connection has disconnected
                        break;
                    case BleCallback.FAIL_OTHER://other reason
                        break;
                    default:
                        break;
                }

            }
        });
```
When you want to cancel notification or indication, you can call cancelNotify()
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
            public void onFailure(int failCode, String info, BleDevice device) {

            }
        });
```

if the length of the data you wanna deliver to remote device is larger than MTU(default 20), you can use the following method to write by batch
```java
       bleManager.writeByBatch(bleDevice, serviceUuid, writeUuid, data, lengthPerPackage, new  BleWriteByBatchCallback() {
            @Override
            public void writeByBatchSuccess(byte[] data, BleDevice device) {

            }

            @Override
            public void onFailure(int failCode, String info, BleDevice device) {

            }
        });
```

### 7.Destroy
You must call destroy() to release some resources after BLE communication end
```java
       bleManager.destroy();

```

### Other api
|Method|Description|
|------|-----------|
|**read**(BleDevice bleDevice, String serviceUuid, String readUuid, BleReadCallback bleReadCallback)|Read the characteristic data|
|**readRssi**(BleDevice device, BleRssiCallback callback)|Read the remote device rssi(Received Signal Strength Indication)|
|**setMtu**(BleDevice device, int mtu, BleMtuCallback callback)|Set MTU (Maximum Transmission Unit)|
|isScanning()|Is Scanning?|
|isConnected(String address)|Check if the local bluetooth has connected to the remote device|
|isConnecting(String address)|Check if local device is connecting with the remote device|
|getConnectedDevices()|Get connected devices list|
|getDeviceServices(BleDevice device);<br>getDeviceServices(String address)|Get service information which the remote device supports,note that it may return null. you will get a **Map<ServiceInfo, List<CharacteristicInfo>>**<br>ServiceInfo: get service uuid info like *uuid*.<br>CharacteristicInfo: get characteristic info like *uuid* and *property*(readable,writable,notify,indicative).|
|*supportBle(Context context)*|Check if this device supports ble|
|*isBluetoothOn()*|Check if local bluetooth is enabled|
|*isAddressValid(String address)*|Check if the address is valid|
|*isScanPermissionGranted(Context context)*|Check if the scan-permission is granted|
|*enableBluetooth(Activity activity, int requestCode)*|Turn on local bluetooth, calling the method will show users a request dialog to grant or reject,so you can get the result from Activity#onActivityResult()|
|*toggleBluetooth(boolean enable)*|Turn on or off local bluetooth directly without showing users a request dialog|
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