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

### 3.How to select a thread to run all operation callbacks(except BleScanCallback， like connect/notify/read/write and so on)
```java
       // After creating a BleHandlerThread instance, you can or not call BleHandlerThread#start(),
       // if you don't call it, it will be called automatically. When disconnected or failed to
       // connect, BleHandlerThread#quitLooperSafely() will also be called automatically. You don't
       // have to call other methods(like #getLooper(), #quit(), #or quitSafety()), they all have
       // been deprecated in BleHandlerThread, and they will not work even if you have called them.
       // You just need to create a instance and add it to specified position of BleManager#connect()
       BleManager.getInstance().connect(device.getAddress(), connectCallback, new BleHandlerThread("BleThread"));
```