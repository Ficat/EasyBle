# EasyBle
  EasyBle is a framework used for android BLE, this framework makes android Ble operation simpler and supports basic BLE operations, besides, it also support batch writing data and multi connection

[中文文档](https://github.com/Ficat/EasyBle/blob/master/README_CN.md)

## Gradle dependency
```gradle
allprojects {
    repositories {
        maven { url 'https://jitpack.io' }
    }
}

dependencies {
    implementation 'com.github.Ficat:EasyBle:v1.0.2'
}
```

## Usage
 The framework uses BleManager to manager BLE
### Some basic useful api in BleManager
If you want to open bluetooth, I strongly recommend you call enableBluetooth() rather than toggleBluetooth(true), due to you can receive result message from Activity#onActivityResult if using enableBluetooth() to enable bluetooth
```java
        //check if the device supports BLE
        BleManager.supportBle(context);

        //is Bluetooth turned on?
        BleManager.isBluetoothOn();
        
        //open or close bluetooth without showing users a request
        //dialog, except some special android devices 
        BleManager.toggleBluetooth(true); 
        
        //open bluetooth with a request dialog, you can receive the
        //result from the method onActivityResult() of this activity
        BleManager.enableBluetooth(activity,requestCode);
```

### Step
### 1.Get ble manager

```java

        //Get ble manager
        BleManager bleManager = BleManager.getInstance(this.getApplication());

        //Set options, you can call this method many times, this framework will use
        //the newest options. For example,you have set scan period(like 10s), but next
        //scan you wanna scan period longer(like 15s), so you can call this method again
        //to set scan period is 15s
        BleManager.Options options = new BleManager.Options();
        options.loggable = true; //does it print log?
        options.connectTimeout = 10000; //connection time out
        options.scanPeriod = 12000; //scan period
        options.scanDeviceName = "deviceName"; 
        options.scanDeviceAddress = "deviceAddress";//like "DD:0D:30:00:0D:9B"
        options.scanServiceUuids = serviceUuidArray;
        bleManager.option(options);

```

### 2.Scan
If sdk version >=23, scanning ble must have location permissions
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

```

Once target remote device has been discovered you can use stopScan() to stop scanning
```java
        bleManager.stopScan();
```

### 3.Connect
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
            public void onTimeout(BleDevice device) {

            }

            @Override
            public void onConnected(BleDevice device) {

            }

            @Override
            public void onDisconnected(BleDevice device) {

            }
        };

       //connect to the remote device by BleDevice object 
       bleManager.connect(bleDevice, bleConnectCallback);

       //connect to the remote device by address
       bleManager.connect(address, bleConnectCallback)
```

Use one of the following methods to disconnect from remote device
```java

       //disconnect from the specific remote device by BleDevice object
       bleManager.disconnect(bleDevice);
	   
       //disconnect from the specific remote device by address
       bleManager.disconnect(address);

       //disconenct all connected devices
       bleManager.disconnectAll();
```


### 4.Notify
Both notification and indication use the following method to set notfication or indication
```java
       bleManager.notify(bleDevice, serviceUuid, notifyUuid, new BleNotifyCallback() {
            @Override
            public void onCharacteristicChanged(byte[] data, BleDevice device) {
              
            }

            @Override
            public void onNotifySuccess(String notifySuccessUuid, BleDevice device) {

            }

            @Override
            public void onFail(int failCode, String info, BleDevice device) {
             
            }
        });
```
When you want to cancel notification or indication, you can call cancelNotify()
```java
       bleManager.cancelNotify(bleDevice, notifyUuid);
```

### 5.Write 
```java
       bleManager.write(bleDevice, serviceUuid, writeUuid, data, new BleWriteCallback() {
            @Override
            public void onWrite(byte[] data, BleDevice device) {

            }

            @Override
            public void onFail(int failCode, String info, BleDevice device) {

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
            public void onFail(int failCode, String info, BleDevice device) {

            }
        });
```

### 6.Destroy
You must call destroy() to release some resources after BLE communication end
```java
       bleManager.destroy();

```

### Other api
```java

       //get service infomations which the remote supports,it may return
       //null if the remote device is not connected
       bleManager.getDeviceServices(bleDevice);

       Map<ServiceInfo, List<CharacteristicInfo>> serviceInfoMap = bleManager.getDeviceServices(bleDevice);
       if (serviceInfoMap != null){
           for (Map.Entry<ServiceInfo, List<CharacteristicInfo>> entry : serviceInfoMap.entrySet()) {
               ServiceInfo serviceInfo = entry.getKey();
               Log.e("TAG", "service uuid: " + serviceInfo.uuid);
               for (CharacteristicInfo characterInfo : entry.getValue()) {
                   Log.e("TAG", "chracteristic uuid: " + characterInfo.uuid);
                   boolean readable = characterInfo.readable;
                   boolean writeable = characterInfo.writeable;
                   boolean notification = characterInfo.notify;
                   boolean indicative = characterInfo.indicative;
               }
           }
       }


       //read characteristic data
       bleManager.read(bleDevice, serviceUuid, readUuid, bleReadCallback);

    
       //read the remote device's rssi
       bleManager.readRssi(bleDevice, bleRssiCallback);


       //set mtu
       bleManager.setMtu(bleDevice, mtu, bleMtuCallback);


       //get a list of current connected devices 
       bleManager.getConnectedDevices(); 


       //check if the local bluetooth has connected to the remote device
       bleManager.isConnected(address);

```



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