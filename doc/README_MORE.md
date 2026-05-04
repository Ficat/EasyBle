### 1. About BLE required permissions
|API version|Required Permissions|
|------|-----------|
|API31+|*"android.permission.BLUETOOTH_SCAN"*<br>*"android.permission.BLUETOOTH_CONNECT"*<br>*"android.permission.BLUETOOTH_ADVERTISE"*|
|API29+|*"android.permission.ACCESS_FINE_LOCATION"*|
|API23+|*"android.permission.ACCESS_COARSE_LOCATION"* or <br>*"android.permission.ACCESS_FINE_LOCATION"*|
|API22-| None|

### 2.About BleManager#getDeviceServices(String), how to get services and its characteristics
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