package com.ficat.easyble;

import android.bluetooth.BluetoothDevice;
import android.os.Parcel;
import android.os.Parcelable;
import android.text.TextUtils;


/**
 * Created by pw on 2018/9/13.
 */

public class BleDevice implements Parcelable {
    private static final String DEFAULT_NAME = "unknown";

    /**
     * Connection state constants
     */
    public static final int DISCONNECTED = 1;
    public static final int CONNECTING = 2;
    public static final int CONNECTED = 3;

    /**
     * Current connection state
     */
    private volatile int connState = DISCONNECTED;

    private String name;
    private final BluetoothDevice device;

    BleDevice(BluetoothDevice device) {
        this.device = device;
        tryToGetName();
    }

    public String getAddress() {
        return device.getAddress();
    }

    public String getName() {
        if (TextUtils.isEmpty(name) || name.equals(DEFAULT_NAME)) {
            tryToGetName();
        }
        return name;
    }

    public boolean isConnected() {
        return connState == CONNECTED;
    }

    public boolean isConnecting() {
        return connState == CONNECTING;
    }

    public void setConnectionState(int newState) {
        synchronized (this) {
            this.connState = newState;
        }
    }

    public int getConnectionState() {
        return this.connState;
    }

    public BluetoothDevice getDevice() {
        return device;
    }

    private void tryToGetName() {
        //Android12(api31) or higher,Bluetooth#getName() needs 'BLUETOOTH_CONNECT' permission
        try {
            this.name = device.getName();
        } catch (Exception e) {
            Logger.d("Failed to call BluetoothDevice#getName() because of no 'BLUETOOTH_CONNECT' permission");
        } finally {
            //Device name got from BluetoothDevice#getName() may be null, so check it
            if (TextUtils.isEmpty(this.name)) {
                this.name = DEFAULT_NAME;
            }
        }
    }

    @Override
    public String toString() {
        return "BleDevice{" +
                "connState=" + connState +
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
        dest.writeInt(this.connState);
        dest.writeString(this.name);
        dest.writeParcelable(this.device, flags);
    }

    protected BleDevice(Parcel in) {
        this.connState = in.readInt();
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
