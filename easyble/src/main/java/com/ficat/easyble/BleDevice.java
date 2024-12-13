package com.ficat.easyble;

import android.bluetooth.BluetoothDevice;
import android.os.Parcel;
import android.os.Parcelable;
import android.text.TextUtils;


/**
 * Created by pw on 2018/9/13.
 */

public class BleDevice implements Parcelable {
    public volatile boolean connected;
    public volatile boolean connecting;
    public String address;
    public String name;
    private BluetoothDevice device;

    BleDevice(BluetoothDevice device) {
        this.device = device;
        this.address = device.getAddress();

        //Android12(api31) or higher,Bluetooth#getName() needs 'BLUETOOTH_CONNECT' permission
        try {
            this.name = device.getName();
        } catch (Exception e) {
            Logger.e("Failed to call BluetoothDevice#getName() because of no 'BLUETOOTH_CONNECT' permission");
        } finally {
            //Device name got from BluetoothDevice#getName() may be null, so check it
            if (TextUtils.isEmpty(this.name)){
                this.name = "unknown";
            }
        }
    }

    public BluetoothDevice getDevice() {
        return device;
    }

    @Override
    public String toString() {
        return "BleDevice{" +
                "connected=" + connected +
                ", connecting=" + connecting +
                ", address='" + address + '\'' +
                ", name='" + name + '\'' +
                ", device=" + device +
                '}';
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeByte(this.connected ? (byte) 1 : (byte) 0);
        dest.writeByte(this.connecting ? (byte) 1 : (byte) 0);
        dest.writeString(this.address);
        dest.writeString(this.name);
        dest.writeParcelable(this.device, flags);
    }

    protected BleDevice(Parcel in) {
        this.connected = in.readByte() != 0;
        this.connecting = in.readByte() != 0;
        this.address = in.readString();
        this.name = in.readString();
        this.device = in.readParcelable(BluetoothDevice.class.getClassLoader());
    }

    public static final Creator<BleDevice> CREATOR = new Creator<BleDevice>() {
        @Override
        public BleDevice createFromParcel(Parcel source) {
            return new BleDevice(source);
        }

        @Override
        public BleDevice[] newArray(int size) {
            return new BleDevice[size];
        }
    };
}
