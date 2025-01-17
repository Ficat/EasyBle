package com.ficat.easyble;

import android.bluetooth.BluetoothDevice;
import android.os.Parcel;
import android.os.Parcelable;
import android.text.TextUtils;

/**
 * Created by pw on 2018/9/13.
 */

public final class BleDevice implements Parcelable {
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

    public int getConnectionState() {
        return this.connState;
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

    void setConnectionState(int newState) {
        synchronized (this) {
            this.connState = newState;
        }
    }

    private void tryToGetName() {
        //Android12(api31) or higher,Bluetooth#getName() needs 'BLUETOOTH_CONNECT' permission
        try {
            this.name = device.getName();
        } catch (Exception e) {
            Logger.e("Failed to call BluetoothDevice#getName(), error msg: " + e.getMessage());
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
        dest.writeInt(this.connState);
        dest.writeString(this.name);
        dest.writeParcelable(this.device, flags);
        dest.writeString(this.parcelableExtraClassName);
        dest.writeParcelable(this.parcelableExtra, flags);
    }

    protected BleDevice(Parcel in) {
        this.connState = in.readInt();
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
