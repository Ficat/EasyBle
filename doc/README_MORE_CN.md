### 1. 关于BLE所需权限情况
|API版本|所需权限|
|------|-----------|
|API31+|*"android.permission.BLUETOOTH_SCAN"*<br>*"android.permission.BLUETOOTH_CONNECT"*<br>*"android.permission.BLUETOOTH_ADVERTISE"*|
|API29+|*"android.permission.ACCESS_FINE_LOCATION"*|
|API23+|*"android.permission.ACCESS_COARSE_LOCATION"* 或 <br>*"android.permission.ACCESS_FINE_LOCATION"*|
|API22-| None|

### 2.如何使用BleDevice携带一些额外信息（即BleDevice中）
```java
       // 若你想在BleDevice存储一些额外信息，可以按以下步骤来
       // 1. 首先，创建一个用于存储额外信息的类，无论类名以及任何成员都可以，只要该类实现了Parcelable接口
       // 2. 其次，创建一个该额外信息类的实例，并且将信息传入进去
       // 3. 最后，调用BleDevice#setParcelableExtra()然后将该实例传进去即可
       // 例如，我们建一个名叫ExtraInfo的类，该类已实现Parcelable接口，并且该类包含三个成员
       // ( String note, int rssi, byte[] scanRecordBytes)， 然后我们可以按以下代码处理即可
       String strInfo = "this is string info";
       byte[] bytes = new byte[2];
       int rssi = -50;
       ExtraInfo extra = new ExtraInfo();
       extra.setNote(strInfo);
       extra.setRssi(rssi);
       extra.setScanRecordBytes(bytes);
       bleDevice.setParcelableExtra(extra);

       // 从BleDevice对象中获取该额外信息
       Parcelable p = bleDevice.getParcelableExtra();
       if (p instanceof ExtraInfo) {
           ExtraInfo e = (ExtraInfo) p;
       }
```