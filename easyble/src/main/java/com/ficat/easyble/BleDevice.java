package com.ficat.easyble;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothDevice;
import android.os.Build;
import android.os.Parcel;
import android.os.Parcelable;
import android.text.TextUtils;

import com.ficat.easyble.utils.Logger;

/**
 * Created by pw on 2018/9/13.
 */

public final class BleDevice implements Parcelable {
    /**
     * Device name
     */
    private String name;

    /**
     * Original bluetooth device
     */
    private final BluetoothDevice device;

    private Parcelable parcelableExtra;//extra info


    BleDevice(BluetoothDevice device) {
        this.device = device;
        tryToGetName();
    }

    public String getAddress() {
        return device.getAddress();
    }

    public String getName() {
        if (TextUtils.isEmpty(name)) {
            tryToGetName();
        }
        return name;
    }

    public void setParcelableExtra(Parcelable parcelableExtra) {
        if (parcelableExtra == null) {
            return;
        }
        this.parcelableExtra = parcelableExtra;
    }

    public Parcelable getParcelableExtra() {
        return parcelableExtra;
    }

    public BluetoothDevice getBluetoothDevice() {
        return device;
    }

    @SuppressLint("MissingPermission")
    private void tryToGetName() {
        //Android12(api31) or higher,Bluetooth#getName() needs 'BLUETOOTH_CONNECT' permission
        try {
            this.name = device.getName();
        } catch (Exception e) {
            Logger.e("Failed to call BluetoothDevice#getName(), error msg: " + e.getMessage());
        }
    }

    @Override
    public String toString() {
        return "BleDevice{" +
                "name='" + name + '\'' +
                ", device=" + device +
                ", parcelableExtra=" + parcelableExtra +
                '}';
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(this.name);
        dest.writeParcelable(this.device, flags);
        dest.writeParcelable(this.parcelableExtra, flags);
    }

    protected BleDevice(Parcel in) {
        this.name = in.readString();
        if (Build.VERSION.SDK_INT >= 33) { // Android 13
            this.device = in.readParcelable(BluetoothDevice.class.getClassLoader(), BluetoothDevice.class);
        } else {
            this.device = in.readParcelable(BluetoothDevice.class.getClassLoader());
        }
        parcelableExtra = in.readParcelable(getClass().getClassLoader());
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
