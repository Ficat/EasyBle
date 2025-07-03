# EasyBle
  EasyBle主要用于简化安卓BLE操作流程，降低BLE开发繁琐程度。本库支持扫描（含自定义过滤条件扫描）、连接（包括设备多连接）、设备服务查询、读写数据（含分批写入）、读取设备信号、设置最大传输单元等BLE操作
>由于Android12起蓝牙权限等发生变化，故请使用或升级到最新版本（3.1.x）
## Gradle dependency
```gradle
allprojects {
    repositories {
	    maven { url 'https://jitpack.io' }
    }
}


dependencies {
    implementation 'com.github.Ficat:EasyBle:v3.1.2'
}
```

## Usage
 本库主要通过BleManager类来进行BLE操作
### 1.判断设备是否支持BLE并打开蓝牙.<br>
[查看蓝牙所需权限详情](README_MORE_CN.md)
```java
        // 是否支持BLE
        BleManager.supportBle(context);

        // 请求蓝牙权限，在Android12及以上版本，大多数蓝牙操作（如打开蓝牙、连接、通知与读写等等）都需要相应的蓝
        // 牙权限，因此这一步不可避免。你可以通过BleManager#getBleRequiredPermissions()获取BLE所需所有权
        // 限
        List<String> permissions = BleManager.getBleRequiredPermissions();
        if (list.size() > 0) { // 低版本可能不需要任何权限，所有此处检查一下
            requestPermissions(permissions);
        }

        // 蓝牙是否打开
        BleManager.isBluetoothOn();

        // 若蓝牙未打开则首先使用该方法请求用户打开蓝牙，需在传入的activity的onActivityResult中处理请求结果。
        // 该方法需要蓝牙权限，否则该方法调用不生效
        boolean requestStart = BleManager.enableBluetooth(activity,requestCode);
        if(!requestStart) {
            // 说明无BLE权限或不支持BLE
        }
```

### 2.获取BleManager对象并初始化

```java

        // Scan/connection不是必须的，若不设置，那么扫描或连接就会使用默认参数
        BleManager.ScanOptions scanOptions = BleManager.ScanOptions
                .newInstance()
                .scanPeriod(10000)
                .scanDeviceName(null);

        BleManager.ConnectionOptions connOptions = BleManager.ConnectionOptions
                .newInstance()
                .connectionPeriod(12000);

        BleManager manager = BleManager
                .getInstance()
                .setScanOptions(scanOptions)
                .setConnectionOptions(connOptions)
                .setLog(true, "TAG")
                .init(this.getApplication());// 这里需要Context，但为了防止内存泄露，最好传Application实例
        
```

### 3.扫描
安卓版本不小于6.0的，扫描需要BLE权限，因此扫描前确保所有BLE权限已被授予.
 [如何使用BleDevice存储或携带额外信息](README_MORE_CN.md).
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
                        // 蓝牙已关闭
                        break;
                    case BleScanCallback.SCAN_PERMISSION_NOT_GRANTED:
                        // 扫描权限未授予
                        break;
                    case BleScanCallback.PREVIOUS_SCAN_NOT_FINISHED:
                        // 上次扫描尚未完成
                        break;
                    case BleScanCallback.SCAN_FAILED:
                        // 未知原因
                        break;
                }
            }
        });

        // 使用指定扫描参数扫描
        bleManager.startScan(scanOptions, bleScanCallback);
```
当需要结束扫描时用以下方法结束扫描，建议在扫描到目标设备后停止扫描
```java
        bleManager.stopScan();
```

### 4.连接
 你可以使用BleDevice对象或mac地址连接设备
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
                        // 蓝牙已关闭
                        break;
                    case BleErrorCodes.CONNECTION_PERMISSION_NOT_GRANTED:
                        // 连接权限未授予
                        break;
                    case BleErrorCodes.CONNECTION_REACH_MAX_NUM:
                        // 已达到最大连接数
                        break;
                    case BleErrorCodes.CONNECTION_TIMEOUT:
                        // 连接超时
                        break;
                    case BleErrorCodes.CONNECTION_CANCELED:
                        // 连接已取消
                        break;
                    case BleErrorCodes.UNKNOWN:
                        // 未知原因
                        break
                    default:
                        break;
                }
            }
        };

       bleManager.connect(bleDevice, bleConnectCallback);

       // 通过Mac地址连接
       bleManager.connect(address, bleConnectCallback);

       // 第二个参数:   选择一个指定的连接选项
       bleManager.connect(bleDevice, connectionOptions, connectCallback);

```

当需要断开与设备的连接时可使用以下任一方法断开设备连接
```java

       //断开与指定设备的连接
       bleManager.disconnect(bleDevice);
	   
       //传入目标的mac地址断开与该设备的连接
       bleManager.disconnect(address);

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
                    case BleErrorCodes.UNKNOWN:
                        // 未知原因
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
                    case BleErrorCodes.UNKNOWN:
                        // 未知原因
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
                }
            }
        });
```

### 8.Destroy
当结束BLE通信时必须调用destroy方法以释放资源
```java
       bleManager.destroy();

```

### 其他api
|Method|Description|
|------|-----------|
|**readRssi**(BleDevice device, BleRssiCallback callback)|读取设备信号强度|
|**setMtu**(BleDevice device, int mtu, BleMtuCallback callback)|设置MTU (Maximum Transmission Unit，即最大传输单元)|
|isScanning()|是否正在扫描|
|isConnected(String address)|是否已连接到指定mac的设备|
|isConnecting(String address)|是否正在与指定设备进行连接|
|getConnectedDevices()|获取已连接设备列表|
|getDeviceServices(String address)|获取已连接设备所支持的服务信息，注意若未连接则返回null|
|*supportBle(Context context)*|设备是否支持BLE|
|*isBluetoothOn()*|蓝牙是否已打开|
|*isAddressValid(String address)*|是否为合法的mac地址|
|*getValidMtuRange()*|获取有效的MTU范围，返回int数组，数组长度为2，分别为MTU最小值、MTU最大值|
|*getBleRequiredPermissions()*|获取所有BLE所需权限，低版本可能无需任何权限，因此不要忘记检查permissionList长度|
|*allBlePermissionsGranted(Context context)*|检查是否所有BLE权限已被授予|
|*scanPermissionGranted(Context context)*|检查是否扫描权限已被授予|
|*connectionPermissionGranted(Context context)*|检查连接权限是否已被授予|
|*enableBluetooth(Activity activity, int requestCode)*|打开蓝牙，该方法会显示一个dialog请求用户打开,因此打开与否需从Activity#onActivityResult()获取结果。注意该方法在Android12及其以上需要蓝牙权限|
|*toggleBluetooth(boolean enable)*|打开或关闭蓝牙，有些设备仍会显示请求dialog，但与enableBluetooth()不一样的是该方法调用后不会立刻获取到打开/关闭的结果。注意该方法在Android12及其以上需要蓝牙权限，并且在一些设备上该方法会失效|
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