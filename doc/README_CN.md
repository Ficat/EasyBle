# EasyBle
  EasyBle是一个轻量级Android BLE库，旨在简化安卓BLE开发。其具有如下特性：
- 可配置扫描过滤器进行扫描
- 支持自动连接与连接失败重试
- 支持RSSI读取、MTU配置、Characteristic和Descriptor读写、Notify以及Indicate等较全面的GATT操作
- GATT操作顺序执行并具有超时保护机制（从v3.4.0起支持）
>由于Android12起蓝牙权限等发生变化，且为了能使用到最新特性，请升级或更新到3.3.0以上版本
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

## 权限
|API版本|所需权限|
|------|-----------|
|API31+|*"android.permission.BLUETOOTH_SCAN"*<br>*"android.permission.BLUETOOTH_CONNECT"*<br>*"android.permission.BLUETOOTH_ADVERTISE"*|
|API29+|*"android.permission.ACCESS_FINE_LOCATION"*<br>*"android.permission.ACCESS_FINE_LOCATION"*|
|API23+|*"android.permission.ACCESS_COARSE_LOCATION"* 或 <br>*"android.permission.ACCESS_FINE_LOCATION"*|
|API22-| None|

## Usage
### 1.判断设备是否支持BLE并打开蓝牙.<br>
```java
        // 是否支持BLE
        boolean supportBle = BleManager.supportBle(context);

        // 请求蓝牙权限
        List<String> permissions = BleManager.getBleRequiredPermissions();
        if (list.size() > 0) {
            requestPermissions(permissions);
        }
        
        // 打开蓝牙
        boolean requestStart = BleManager.enableBluetooth(activity,requestCode);
        if(!requestStart) {
            // 说明无BLE权限或不支持BLE
        }
```

### 2.获取BleManager对象并初始化

```java

        BleManager bleManager = BleManager
                        .getInstance()
                        .setGattOperationTimeout(600) // Gatt操作(如读/写等)超时，毫秒
                        .setLog(true, "TAG")
                        .init(this.getApplication());

```

### 3.扫描

```java
        //配置扫描选项
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

                // 可使用如下方法解析扫描到的数据
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
                        // 蓝牙已关闭
                        break;
                    case BleErrorCodes.PERMISSION_MISSING:
                        // 扫描权限未授予
                        break;
                    case BleErrorCodes.SCAN_ALREADY_STARTED:
                        // 上次扫描尚未完成
                        break;
                    case BleErrorCodes.SCAN_TOO_FREQUENTLY:
                        // 扫描太过频繁
                        break;
                    case BleErrorCodes.UNKNOWN:
                        // 未知原因
                        break;
                }
            }
        });

        // 或者使用默认扫描选项扫描，可以通过BleManager#setScanOptions()配置默认扫描选项
        bleManager.startScan(bleScanCallback);
```
当需要结束扫描时用以下方法结束扫描，建议在扫描到目标设备后停止扫描
```java
        bleManager.stopScan();
```

### 4.连接
 你可以使用BleDevice对象或mac地址连接设备<br>
**注意：** Android 10 以下版本一次只允许发起一个连接请求，并将所有后续请求排队等待处理。Android 10 及更高版本则采用批量处理的方式，将连接请求分组执行。这意味着，在 Android 10 以下版本中，我们必须等待前一个连接的回调完成后才能发起新的连接请求
```java

       BleManager.ConnectionOptions connOptions = BleManager.ConnectionOptions
               .newInstance()
               .autoConnect(false) // 自动连接
               .retryWhenConnectionFailed(3, 5000) //失败重试 
               .connectionTimeout(12000);// 连接超时
       
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
                        // 蓝牙已关闭
                        break;
                    case BleErrorCodes.PERMISSION_MISSING:
                        // 连接权限未授予
                        break;
                    case BleErrorCodes.CONNECTION_REACH_MAX_NUM:
                        // 已达到最大连接数
                        break;
                    case BleErrorCodes.TIMEOUT:
                        // 连接超时
                        break;
                    case BleErrorCodes.CONNECTION_CANCELED:
                        // 连接已取消
                        break;
                    case BleErrorCodes.CONNECTION_ALREADY_STARTED_OR_ESTABLISHED:
                        // 连接早已开始或早已成功
                        break;
                    case BleErrorCodes.UNKNOWN:
                        // 未知原因
                        break;
                    default: // 其他GATT错误码，如BleErrorCodes.GATT_FAILURE或BleErrorCodes.GATT_INSUFFICIENT_AUTHORIZATION
                        break;
                }
            }
        };

       bleManager.connect(bleDevice, connOptions, bleConnectCallback);

       // 通过Mac地址连接
       bleManager.connect(address, connOptions, bleConnectCallback);

       // 或者使用默认连接选项，可以通过BleManager#setConnectionOptions()设置默认连接选项
       bleManager.connect(bleDevice, connectCallback);

```

当需要断开与设备的连接时可使用以下任一方法断开设备连接
```java

       //断开与指定设备的连接，传入BleDevice实例或mac地址
       bleManager.disconnect(bleDevice);

       // 传入目标的mac地址断开与该设备的连接
       // 第二个参数表示，若设备已经连接，是否立即关闭BluetoothGatt而无需等待系统断开连接的回调
       bleManager.disconnect(address, true);

       //断开所有已连接设备
       bleManager.disconnectAll();
```

### 5.Notify
notify和indicate都使用以下方法
```java
       bleManager.notify(bleDevice, serviceUuid, notifyUuid, new BleNotifyCallback() {
            @Override
            public void onCharacteristicChanged(byte[] receivedData, UUID characteristicUuid, BleDevice device) {
                // 注意该回调方法运行于非UI线程
            }

            @Override
            public void onNotifySuccess(UUID characteristicUuid, BleDevice device) {

            }

            @Override
            public void onNotifyFailed(int errorCode, UUID characteristicUuid, BleDevice device) {
                switch (errorCode) {
                    case BleErrorCodes.CONNECTION_NOT_ESTABLISHED:
                        // 连接尚未建立或连接已断开
                        break;
                    case BleErrorCodes.SERVICE_NOT_FOUND:
                        // 未找到服务
                        break;
                    case BleErrorCodes.CHARACTERISTIC_NOT_FOUND_IN_SERVICE:
                        // 在指定服务中未找到指定的特征
                        break;
                    case BleErrorCodes.NOTIFICATION_OR_INDICATION_UNSUPPORTED:
                        // 特征不支持notify或indicate
                        break;
                    case BleErrorCodes.TIMEOUT:
                        // 设置notify操作超时
                        break;
                    case BleErrorCodes.UNKNOWN:
                        // 未知原因
                        break;
                    default:
                        // 其他GATT错误码，如BleErrorCodes.GATT_FAILURE
                        break;
                }
            }
        });
```
当需要取消notify或indicate时调用以下方法
```java
       bleManager.cancelNotify(bleDevice, notifyUuid);
```

### 6.写入特征数据
```java
       bleManager.write(bleDevice, serviceUuid, writeUuid, data, new BleWriteCallback() {
            @Override
            public void onWriteSuccess(byte[] data, UUID characteristicUuid, BleDevice device) {

            }

            @Override
            public void onWriteFailed(int errCode, byte[] data, UUID characteristicUuid, BleDevice device) {
                switch (errCode) {
                    case BleErrorCodes.CONNECTION_NOT_ESTABLISHED:
                        // 连接尚未建立或连接已断开
                        break;
                    case BleErrorCodes.SERVICE_NOT_FOUND:
                        // 未找到服务
                        break;
                    case BleErrorCodes.CHARACTERISTIC_NOT_FOUND_IN_SERVICE:
                        // 在指定服务中未找到指定的特征
                        break;
                    case BleErrorCodes.WRITE_UNSUPPORTED:
                        // 特征不支持写入操作
                        break;
                    case BleErrorCodes.DATA_LENGTH_GREATER_THAN_MTU:
                        // 数据长度超MTU长度
                        break;
                    case BleErrorCodes.UNKNOWN:
                        // 未知原因
                        break;
                    case BleErrorCodes.TIMEOUT:
                        // 特征写入操作超时
                        break;
                    default:
                        // 其他GATT错误码，如BleErrorCodes.GATT_FAILURE
                        break;
                }
            }
        });
```
如果一次性写入的数据长度大于MTU即最大传输单元（默认是20字节），则可以使用下列方法进行分批写入
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
                        // 连接尚未建立或连接已断开
                        break;
                    case BleErrorCodes.SERVICE_NOT_FOUND:
                        // 未找到服务
                        break;
                    case BleErrorCodes.CHARACTERISTIC_NOT_FOUND_IN_SERVICE:
                        // 在指定服务中未找到指定的特征
                        break;
                    case BleErrorCodes.WRITE_UNSUPPORTED:
                        // 特征不支持写入操作
                        break;
                    case BleErrorCodes.DATA_LENGTH_GREATER_THAN_MTU:
                        // 数据长度(此处特指参数中的每包长度，即lengthPerPackage)超MTU长度
                        break;
                    case BleErrorCodes.TIMEOUT:
                        // 特征写入操作超时
                        break;
                    case BleErrorCodes.UNKNOWN:
                        // 未知原因
                        break;
                    default:
                        // 其他GATT错误码，如BleErrorCodes.GATT_FAILURE
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
                        // 连接尚未建立或连接已断开
                        break;
                    case BleErrorCodes.SERVICE_NOT_FOUND:
                        // 未找到服务
                        break;
                    case BleErrorCodes.CHARACTERISTIC_NOT_FOUND_IN_SERVICE:
                        // 在指定服务中未找到指定的特征
                        break;
                    case BleErrorCodes.READ_UNSUPPORTED:
                        // 特征不支持读操作
                        break;
                    case BleErrorCodes.UNKNOWN:
                        // 未知原因
                        break;
                    case BleErrorCodes.TIMEOUT:
                        // 特征读取超时
                        break;
                    default:
                        // 其他GATT错误码，如BleErrorCodes.GATT_FAILURE
                        break;
                }
            }
        });
```

### 8.Destroy
当结束BLE通信时必须调用destroy方法以释放资源
```java
       bleManager.destroy();

       // 第一个参数表示扫描回调是否在销毁时正常回调，第二个参数表示gatt操作回调是否在销毁时正常回调。
       // 通常而言，我们都会在这些回调中做一些操作比如重连、更新UI等。在做这些之前，我们需要检查下BleManger
       // 是否已经销毁，可以检查BleManager#getContext()是否返回null来检查是否已销毁
       bleManager.destroy(true, true);
```

### 其他API
|Method|Description|
|------|-----------|
|**readRssi**(BleDevice device, BleRssiCallback callback)|读取设备信号强度|
|**setMtu**(BleDevice device, int mtu, BleMtuCallback callback)|设置MTU (Maximum Transmission Unit，即最大传输单元)|
|**descriptorRead**(BleDevice device, UUID serviceUuid, UUID characteristicUuid, UUID descriptorUuid, BleDescriptorReadCallback callback)|读取描述符|
|**descriptorWrite**(BleDevice device, UUID serviceUuid, UUID characteristicUuid, UUID descriptorUuid, byte[] data, BleDescriptorWriteCallback callback)|写入数据至指定描述符|
|**readPhy**(BleDevice device, BlePhyReadCallback callback)|读取连接的当前发射端PHY和接收端PHY|
|**setPreferencePhy**(BleDevice device, int txPhy, int rxPhy, int phyOptions, BlePhyPreferenceCallback callback)|设置首选连接PHY。此仅为建议，PHY是否更改取决于其他应用偏好、本地和远程控制器功能|
|**requestConnectionPriority**(BleDevice device, int connPriority, BleConnectionPriorityCallback callback)|请求更新链接参数|
|isScanning()|是否正在扫描|
|isConnected(String address)|是否已连接到指定mac的设备|
|isConnecting(String address)|是否正在与指定设备进行连接|
|getConnectedDevices()|获取已连接设备列表|
|getConnectingDevices()|获取正在连接的设备列表|
|getDeviceServices(String address)|获取已连接设备所支持的服务信息，注意若未连接则返回null。 [见示例](README_MORE_CN.md)|
|*supportBle(Context context)*|设备是否支持BLE|
|*isBluetoothOn()*|蓝牙是否已打开，3.3.0开始请使用isBluetoothEnabled()|
|*parseScanRecord(byte[] scanRecord)*|解析扫描结果字节数组，返回一个BleScanRecord实例|
|*isCharacteristicReadable(BluetoothGattCharacteristic ch)*|特征是否可读？|
|*isCharacteristicWritable(BluetoothGattCharacteristic ch)*|特征是否可写？|
|*isCharacteristicNotifiable(BluetoothGattCharacteristic ch)*|特征是否支持Notification|
|*isCharacteristicIndicative(BluetoothGattCharacteristic ch)*|特征是否支持Indication|
|*isAddressValid(String address)*|是否为合法的mac地址，3.3.0开始请使用isValidAddress()|
|*getValidMtuRange()*|获取有效的MTU范围，返回int数组，数组长度为2，分别为MTU最小值、MTU最大值|
|*getDefaultMaxConnectionNum()*|获取默认最大可连接数量|
|*getBleRequiredPermissions()*|获取所有BLE所需权限，低版本可能无需任何权限，因此不要忘记检查permissionList长度|
|*allBlePermissionsGranted(Context context)*|检查是否所有BLE权限已被授予|
|*scanPermissionGranted(Context context)*|检查是否扫描权限已被授予|
|*connectionPermissionGranted(Context context)*|检查连接权限是否已被授予|
|getScanOptions()|获取默认或您已设置过的扫描配置信息|
|getConnectOptions()|获取默认或您已设置过的连接配置信息||
|getBluetoothGatt(String address)|获取指定设备的BluetoothGatt,注意若尚未与指定设备建立连接，则返回null|



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