# EasyBle
  EasyBle主要用于简化安卓BLE操作流程，降低BLE开发繁琐程度。本库支持扫描（含自定义过滤条件扫描）、连接（包括设备多连接）、设备服务查询、读写数据（含分批写入）、读取设备信号、设置最大传输单元等BLE操作

## Gradle dependency
```gradle
allprojects {
    repositories {
	    maven { url 'https://jitpack.io' }
    }
}


dependencies {
    implementation 'com.github.Ficat:EasyBle:v1.0.4'
}
```

## Usage
 本库主要通过BleManager类来进行BLE操作
### BleManager中常用基础api
```java
        //是否支持BLE
        BleManager.supportBle(context);

        //蓝牙是否打开
        BleManager.isBluetoothOn();
        
        //打开或关闭蓝牙，不显示请求用户授权dialog（一些特殊设备如大部分国产手机除外）
        BleManager.toggleBluetooth(true); 
        
        //显示dialog请求用户打开蓝牙，需在传入的activity的onActivityResult中处理请求结果
        BleManager.enableBluetooth(activity,requestCode);
```

### 步骤如下

### 1.获取BleManager对象

```java

        //获取管理器对象
        BleManager bleManager = BleManager.getInstance(this.getApplication());
        
        //设置ble选项，可多次设置,EasyBle将使用最新的options。比如本次扫描周期
        //为10s，但你想要下一次扫描周期更长一些，则再次调用本方法去设置即可
        BleManager.Options options = new BleManager.Options();
        options.loggable = true; //是否打印日志
        options.connectTimeout = 10000; //连接超时时间
        options.scanPeriod = 12000; //扫描周期
        options.scanDeviceName = "targetDeviceName"; //扫描的目标设备名
        options.scanDeviceAddress = "targetDeviceAddress"; //扫描目标设备地址如"DD:0D:30:00:0D:9B"
        options.scanServiceUuids = serviceUuidArray; //扫描含该服务UUID的目标设备
        bleManager.option(options);
        
```

### 2.扫描
安卓版本不小于6.0的，扫描必须要有定位权限
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

```
当需要结束扫描时用以下方法结束扫描，建议在扫描到目标设备后停止扫描
```java
        bleManager.stopScan();
```

### 3.连接

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
            public void onTimeout(BleDevice device) {

            }

            @Override
            public void onConnected(BleDevice device) {

            }

            @Override
            public void onDisconnected(BleDevice device) {

            }
        };

       //通过BleDevice对象连接设备
       bleManager.connect(bleDevice, bleConnectCallback);

       //直接通过mac地址连接
       bleManager.connect(address, bleConnectCallback)
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

### 4.Notify
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
            public void onFail(int failCode, String info, BleDevice device) {
             
            }
        });
```
当需要取消notify或indicate时调用以下方法
```java
       bleManager.cancelNotify(bleDevice, notifyUuid);
```

### 5.写入特征数据
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
如果一次性写入的数据长度大于MTU即最大传输单元（默认是20字节），则可以使用下列方法进行分批写入
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
当结束BLE通信时必须调用destroy方法以释放资源
```java
       bleManager.destroy();

```

### 其他api
```java
       //获取设备支持的服务信息，如果设备尚未连接上则返回值为null
       bleManager.getDeviceServices(bleDevice);

       Map<ServiceInfo, List<CharacteristicInfo>> serviceInfoMap = bleManager.getDeviceServices(bleDevice);
       if (serviceInfoMap != null){
           for (Map.Entry<ServiceInfo, List<CharacteristicInfo>> entry : serviceInfoMap.entrySet()) {
               ServiceInfo serviceInfo = entry.getKey();
               Log.e("TAG", "service uuid: " + serviceInfo.uuid);
               for (CharacteristicInfo characterInfo : entry.getValue()) {
                   Log.e("TAG", "characteristic uuid: " + characterInfo.uuid);
                   boolean readable = characterInfo.readable;
                   boolean writable = characterInfo.writable;
                   boolean notification = characterInfo.notify;
                   boolean indicative = characterInfo.indicative;
               }
           }
       }

    
       //读取已连接的远程设备信号
       bleManager.readRssi(bleDevice, bleRssiCallback);


       //设置MTU
       bleManager.setMtu(bleDevice, mtu, bleMtuCallback);


       //读取特征数据
       bleManager.read(bleDevice, serviceUuid, readUuid, bleReadCallback);


       //获取当前连接的设备
       bleManager.getConnectedDevices(); 


       //判断是否已连接上某台设备
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