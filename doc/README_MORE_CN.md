### 1. 关于BLE所需权限情况
|API版本|所需权限|
|------|-----------|
|API31+|*"android.permission.BLUETOOTH_SCAN"*<br>*"android.permission.BLUETOOTH_CONNECT"*<br>*"android.permission.BLUETOOTH_ADVERTISE"*|
|API29+|*"android.permission.ACCESS_FINE_LOCATION"*|
|API23+|*"android.permission.ACCESS_COARSE_LOCATION"* 或 <br>*"android.permission.ACCESS_FINE_LOCATION"*|
|API22-| None|

### 2.使用BleManager#getDeviceServices(String)获取服务与特征
```java
       List<BluetoothGattService> services = BleManager.getInstance().getDeviceServices(device.getAddress());
       if (services == null) {
           return;
       }
       for (BluetoothGattService service : services) {
           // 获取服务下的所有特征
           List<BluetoothGattCharacteristic> characteristics = service.getCharacteristics();
           for (BluetoothGattCharacteristic ch: characteristics){
               // 检查该特征的属性
               boolean writable = BleManager.isCharacteristicWritable(ch);
               boolean readable = BleManager.isCharacteristicReadable(ch);
               boolean notifiable = BleManager.isCharacteristicNotifiable(ch);
               boolean indicative = BleManager.isCharacteristicIndicative(ch);
           }
       }
```