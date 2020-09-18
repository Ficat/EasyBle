# EasyBle
  EasyBle主要用于简化安卓BLE操作流程，降低BLE开发繁琐程度。本库支持扫描（含自定义过滤条件扫描）、连接（包括设备多连接）、设备服务查询、读写数据（含分批写入）、读取设备信号、设置最大传输单元等BLE操作
>由于个人原因不再维护1.0.x，请使用或升级到最新版本（2.0.x）
## Gradle dependency
```gradle
allprojects {
    repositories {
	    maven { url 'https://jitpack.io' }
    }
}


dependencies {
    implementation 'com.github.Ficat:EasyBle:v2.0.1'
}
```

## Usage
 本库主要通过BleManager类来进行BLE操作
### 1.判断设备是否支持BLE并打开蓝牙
```java
        //是否支持BLE
        BleManager.supportBle(context);

        //蓝牙是否打开
        BleManager.isBluetoothOn();

        //若蓝牙未打开则首先使用该方法请求用户打开蓝牙，需在传入的activity的onActivityResult
        //中处理请求结果
        BleManager.enableBluetooth(activity,requestCode);
```

### 2.获取BleManager对象并初始化

```java

        //scan/connection不是必须的，若不设置，那么扫描或连接就
        //会使用默认参数
        BleManager.ScanOptions scanOptions = BleManager.ScanOptions
                .newInstance()
                .scanPeriod(10000)
                .scanDeviceName(null);

        BleManager.ConnectOptions connectOptions = BleManager.ConnectOptions
                .newInstance()
                .connectTimeout(12000);

        BleManager manager = BleManager
                .getInstance()
                .setScanOptions(scanOptions)//非必须设置项
                .setConnectionOptions(connectOptions)
                .setLog(true, "TAG")
                .init(this.getApplication());//这里需要Context，但注意不要传Activity
        
```

### 3.扫描
安卓版本不小于6.0的，扫描必须要有定位权限，若版本为Android10及以上，则需精确定位权限(即*Manifest.permission.ACCESS_FINE_LOCATION*)
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
                    //开始扫描成功
                } else {
                    //未能成功开始扫描，可通过info查看详情
                    String failReason = info;
                }
            }

            @Override
            public void onFinish() {
               
            }
        });

        //使用指定扫描参数扫描
        bleManager.startScan(scanOptions, bleScanCallback);
```
当需要结束扫描时用以下方法结束扫描，建议在扫描到目标设备后停止扫描
```java
        bleManager.stopScan();
```

### 4.连接

```java

       BleConnectCallback bleConnectCallback = new BleConnectCallback() {
            @Override
            public void onStart(boolean startConnectSuccess, String info, BleDevice device) {
                if (startConnectSuccess) {
                    //开始连接
                } else {
                    //未能成功开始连接，可通过info查看详情
                    String failReason = info;
                }
            }

            @Override
            public void onFailure(int failCode, String info, BleDevice device) {
                if(failCode == BleConnectCallback.FAIL_CONNECT_TIMEOUT){
                    //连接超时
                }else{
                    //其他原因导致的连接失败
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
       //使用指定连接选项参数进行连接
       bleManager.connect(bleDevice, connectOptions, bleConnectCallback);

       //使用mac地址连接
       bleManager.connect(address, bleConnectCallback);
       bleManager.connect(address, connectOptions, bleConnectCallback);

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
                switch (failCode) {
                    case BleCallback.FAIL_DISCONNECTED://连接断开
                        break;
                    case BleCallback.FAIL_OTHER://其他原因
                        break;
                    default:
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

            }
        });
```

### 7.Destroy
当结束BLE通信时必须调用destroy方法以释放资源
```java
       bleManager.destroy();

```

### 其他api
|Method|Description|
|------|-----------|
|**read**(BleDevice bleDevice, String serviceUuid, String readUuid, BleReadCallback bleReadCallback)|读取characteristic数据|
|**readRssi**(BleDevice device, BleRssiCallback callback)|读取设备信号强度|
|**setMtu**(BleDevice device, int mtu, BleMtuCallback callback)|设置MTU (Maximum Transmission Unit，即最大传输单元)|
|isAddressValid(String address)|是否为合法的mac地址|
|isScanning()|是否正在扫描|
|isConnected(String address)|是否已连接到指定mac的设备|
|isConnecting(String address)|是否正在与指定设备进行连接|
|getConnectedDevices()|获取已连接设备列表|
|getDeviceServices(BleDevice device);<br>getDeviceServices(String address)|获取已连接设备所支持的服务/特征信息，注意若未连接则返回null，该方法得到一个**Map<ServiceInfo, List<CharacteristicInfo>>**<br>ServiceInfo: 服务信息如*uuid*.<br>CharacteristicInfo: 特征信息如*uuid*、*property*(readable,writable,notify,indicative)等.|
|*supportBle(Context context)*|设备是否支持BLE|
|*isBluetoothOn()*|蓝牙是否已打开|
|*enableBluetooth(Activity activity, int requestCode)*|打开蓝牙，该方法会显示一个dialog请求用户打开,因此打开与否需从Activity#onActivityResult()获取结果|
|*toggleBluetooth(boolean enable)*|打开或关闭蓝牙，有些设备仍会显示请求dialog，但与enableBluetooth()不一样的是该方法调用后不会立刻获取到打开/关闭的结果|
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