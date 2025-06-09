package com.ficat.easyble;

import android.bluetooth.BluetoothDevice;
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

    /**
     * Extra info, you can use this to save what you want
     */
    private String parcelableExtraClassName;//extra info class name
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
        this.parcelableExtraClassName = parcelableExtra.getClass().getName();
    }

    public Parcelable getParcelableExtra() {
        return parcelableExtra;
    }

    public BluetoothDevice getBluetoothDevice() {
        return device;
    }

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
                ", name='" + name + '\'' +
                ", device=" + device +
                ", parcelableExtraClassName='" + parcelableExtraClassName + '\'' +
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
        dest.writeString(this.parcelableExtraClassName);
        dest.writeParcelable(this.parcelableExtra, flags);
    }

    protected BleDevice(Parcel in) {
        this.name = in.readString();
        this.device = in.readParcelable(BluetoothDevice.class.getClassLoader());
        this.parcelableExtraClassName = in.readString();
        if (!TextUtils.isEmpty(this.parcelableExtraClassName)) {
            try {
                Class<?> claze = Class.forName(this.parcelableExtraClassName);
                this.parcelableExtra = in.readParcelable(claze.getClassLoader());
            } catch (Exception e) {
                Logger.e("Failed to read parcelable extra info while creating BleDevice, error msg: "
                        + e.getMessage());
            }
        }
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
