# EasyBle
  EasyBle主要用于简化安卓BLE操作流程，降低BLE开发繁琐程度。本库支持扫描（含自定义过滤条件扫描）、连接（包括设备多连接）、设备服务查询、读写数据（含分批写入）、读取设备信号、设置最大传输单元等BLE操作
>由于Android12起蓝牙权限等发生变化，故请使用或升级到最新版本（3.0.x）
## Gradle dependency
```gradle
allprojects {
    repositories {
	    maven { url 'https://jitpack.io' }
    }
}


dependencies {
    implementation 'com.github.Ficat:EasyBle:v3.0.0'
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
                .connectTimeout(12000);

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
            public void onLeScan(BleDevice device, int rssi, byte[] scanRecord) {
                String name = device.getName();
                String address = device.getAddress();
            }

            @Override
            public void onStart(boolean startScanSuccess, String info) {
                if (startScanSuccess) {
                    // 开始扫描成功
                } else {
                    // 未能成功开始扫描，可通过info查看详情
                    String failReason = info;
                }
            }

            @Override
            public void onFinish() {
               
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
 你可以使用BleDevice对象或mac地址连接设备.默认情况下，所有操作回调（connect/notify/read/write/readRssi/setMtu等等）都将运行在主线程，
 当然你可以选择其运行在子线程[如何选择一个线程来运行所有出扫描外的回调](README_MORE_CN.md).
```java

       BleConnectCallback bleConnectCallback = new BleConnectCallback() {
            @Override
            public void onStart(boolean startSuccess, String info, BleDevice device) {
                if (startSuccess) {
                    // 连接成功开始
                } else {
                    // 连接未能成功开始，可通过info查看详情
                }
            }

            @Override
            public void onFailure(int failureCode, String info, BleDevice device) {
                switch (failureCode) {
                    case BleCallback.FAILURE_CONNECTION_TIMEOUT:
                        // 连接超时
                        break;
                    case BleCallback.FAILURE_CONNECTION_CANCELED:
                        // 连接被取消,发生于连接正在进行中，但我们手动调用disconnect()或disconnectAll()。
                        break;
                    case BleCallback.FAILURE_CONNECTION_FAILED:
                        // 连接失败
                        break;
                    default:
                        // 其他原因
                        break;
                }
            }

            @Override
            public void onConnected(BleDevice device) {
                // 连接成功
            }

            @Override
            public void onDisconnected(String info, int status, BleDevice device) {
                // 连接断开
            }
        };

       bleManager.connect(bleDevice, bleConnectCallback);

       // 通过Mac地址连接
       bleManager.connect(address, bleConnectCallback);

       // 第二个参数:   选择一个指定的连接选项
       // 最后一个参数:  选择一个线程去运行所有回调，比如connect/notify/read/write等，若该参数为null, 所有回调将
       //              运行在主线程（默认就是null）
       bleManager.connect(bleDevice, connectionOptions, connectCallback, new BleHandlerThread("BleThread"));

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
            public void onCharacteristicChanged(byte[] data, BleDevice device) {
              
            }
            
            @Override
            public void onNotifySuccess(String notifySuccessUuid, BleDevice device) {

            }

            @Override
            public void onFailure(int failCode, String info, BleDevice device) {
                switch (failureCode) {
                    case BleCallback.FAILURE_CONNECTION_NOT_ESTABLISHED:
                        // 连接尚未建立或连接已断开
                        break;
                    case BleCallback.FAILURE_SERVICE_NOT_FOUND:
                        // 未找到服务
                        break;
                    case BleCallback.FAILURE_CHARACTERISTIC_NOT_FOUND_IN_SERVICE:
                        // 在指定服务中未找到指定的特征
                        break;
                    case BleCallback.FAILURE_NOTIFICATION_OR_INDICATION_UNSUPPORTED:
                        // 该特征不支持notify或indicate
                        break;
                    case BleCallback.FAILURE_OTHER:
                        // 其他原因
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
            public void onWriteSuccess(byte[] data, BleDevice device) {

            }

            @Override
            public void onFailure(int failCode, String info, BleDevice device) {
                switch (failureCode) {
                    case BleCallback.FAILURE_CONNECTION_NOT_ESTABLISHED:
                        // 连接尚未建立或连接已断开
                        break;
                    case BleCallback.FAILURE_SERVICE_NOT_FOUND:
                        // 未找到服务
                        break;
                    case BleCallback.FAILURE_CHARACTERISTIC_NOT_FOUND_IN_SERVICE:
                        // 在指定服务中未找到指定的特征
                        break;
                    case BleCallback.FAILURE_WRITE_UNSUPPORTED:
                        // 特征不支持写入操作
                        break;
                    case BleCallback.FAILURE_OTHER:
                        // 其他原因 (比如数据长度无效等, 可通过info查看详情).
                        break;
                }
            }
        });
```
如果一次性写入的数据长度大于MTU即最大传输单元（默认是20字节），则可以使用下列方法进行分批写入
```java
       bleManager.writeByBatch(bleDevice, serviceUuid, writeUuid, data, lengthPerPackage, new  BleWriteByBatchCallback() {
            @Override
            public void writeByBatchSuccess(byte[] data, BleDevice device) {

            }

            @Override
            public void onFailure(int failCode, String info, BleDevice device) {
                switch (failureCode) {
                    case BleCallback.FAILURE_CONNECTION_NOT_ESTABLISHED:
                        // 连接尚未建立或连接已断开
                        break;
                    case BleCallback.FAILURE_SERVICE_NOT_FOUND:
                        // 未找到服务
                        break;
                    case BleCallback.FAILURE_CHARACTERISTIC_NOT_FOUND_IN_SERVICE:
                        // 在指定服务中未找到指定的特征
                        break;
                    case BleCallback.FAILURE_WRITE_UNSUPPORTED:
                        // 特征不支持写入操作
                        break;
                    case BleCallback.FAILURE_OTHER:
                        // 其他原因 (比如数据长度无效、每包数据长度无效等, 可通过info查看详情).
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
                        // 连接尚未建立或连接已断开
                        break;
                    case BleCallback.FAILURE_SERVICE_NOT_FOUND:
                        // 未找到服务
                        break;
                    case BleCallback.FAILURE_CHARACTERISTIC_NOT_FOUND_IN_SERVICE:
                        // 在指定服务中未找到指定的特征
                        break;
                    case BleCallback.FAILURE_READ_UNSUPPORTED:
                        // 特征不支持读操作
                        break;
                    case BleCallback.FAILURE_OTHER:
                        // 其他原因
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
|getDeviceServices(BleDevice device);<br>getDeviceServices(String address)|获取已连接设备所支持的服务信息，注意若未连接则返回null，该方法得到一个**List<ServiceInfo>**<br>ServiceInfo: 包含服务UUID以及CharacteristicInfo信息.|
|*supportBle(Context context)*|设备是否支持BLE|
|*isBluetoothOn()*|蓝牙是否已打开|
|*isAddressValid(String address)*|是否为合法的mac地址|
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