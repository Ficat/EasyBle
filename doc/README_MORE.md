### 1. About BLE required permissions
|API version|Required Permissions|
|------|-----------|
|API31+|*"android.permission.BLUETOOTH_SCAN"*<br>*"android.permission.BLUETOOTH_CONNECT"*<br>*"android.permission.BLUETOOTH_ADVERTISE"*|
|API29+|*"android.permission.ACCESS_FINE_LOCATION"*|
|API23+|*"android.permission.ACCESS_COARSE_LOCATION"* or <br>*"android.permission.ACCESS_FINE_LOCATION"*|
|API22-| None|

### 2.How to use BleDevice to carry or save extra info
```java
       // If you want to save any extra info to BleDevice, you can do like the following.
       // 1. First, create a class used to save extra info, whatever any name and members
       //    attribute, but note that it must implement the interface 'Parcelable'
       // 2. Then, create an instance and put info you wanna save into it
       // 3. Finally, call BleDevice#setParcelableExtra()

       // For example, create a class named ExtraInfo, it has implemented the interface 'Parcelable'
       // and contains three members( String note, int rssi, byte[] scanRecordBytes), we save info
       // like the following
       String strInfo = "this is string info";
       byte[] bytes = new byte[2];
       int rssi = -50;
       ExtraInfo extra = new ExtraInfo();
       extra.setNote(strInfo);
       extra.setRssi(rssi);
       extra.setScanRecordBytes(bytes);
       bleDevice.setParcelableExtra(extra);

       // Get it from the BleDevice instance
       Parcelable p = bleDevice.getParcelableExtra();
       if (p instanceof ExtraInfo) {
           ExtraInfo e = (ExtraInfo) p;
       }
```

### 3.About BleManager#getDeviceServices(String), how to get services and its characteristics
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
               boolean writable = BluetoothGattUtils.isCharacteristicWritable(ch);
               boolean readable = BluetoothGattUtils.isCharacteristicReadable(ch);
               boolean notifiable = BluetoothGattUtils.isCharacteristicNotifiable(ch);
               boolean indicative = BluetoothGattUtils.isCharacteristicIndicative(ch);
           }
       }
```