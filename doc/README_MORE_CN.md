
### 1.使用BleManager#getDeviceServices(String)获取服务与特征
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