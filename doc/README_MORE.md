### 1.About BleManager#getDeviceServices(String), how to get services and its characteristics
```java
       List<BluetoothGattService> services = BleManager.getInstance().getDeviceServices(device.getAddress());
       if (services == null) {
           return;
       }
       for (BluetoothGattService service : services) {
           // Get its characteristics
           List<BluetoothGattCharacteristic> characteristics = service.getCharacteristics();
           for (BluetoothGattCharacteristic ch: characteristics){
               // Check if the property of the characteristic
               boolean writable = BleManager.isCharacteristicWritable(ch);
               boolean readable = BleManager.isCharacteristicReadable(ch);
               boolean notifiable = BleManager.isCharacteristicNotifiable(ch);
               boolean indicative = BleManager.isCharacteristicIndicative(ch);
           }
       }
```