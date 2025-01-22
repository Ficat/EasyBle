package com.ficat.easyble.gatt.data;

import android.bluetooth.BluetoothGattCharacteristic;
import android.os.Build;
import android.os.Parcel;
import android.os.Parcelable;

import java.util.UUID;

/**
 * Created by pw on 2018/9/19.
 */

public class CharacteristicInfo implements Parcelable {
    private final BluetoothGattCharacteristic mCharacteristic;

    public CharacteristicInfo(BluetoothGattCharacteristic characteristic) {
        this.mCharacteristic = characteristic;
    }

    public UUID getUuid() {
        return mCharacteristic.getUuid();
    }

    public boolean isReadable() {
        return (mCharacteristic.getProperties() & BluetoothGattCharacteristic.PROPERTY_READ) > 0;
    }

    public boolean isWritable() {
        return (mCharacteristic.getProperties() & (BluetoothGattCharacteristic.PROPERTY_WRITE |
                BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE)) > 0;
    }

    public boolean isNotifiable() {
        return (mCharacteristic.getProperties() & BluetoothGattCharacteristic.PROPERTY_NOTIFY) > 0;
    }

    public boolean isIndicative() {
        return (mCharacteristic.getProperties() & BluetoothGattCharacteristic.PROPERTY_INDICATE) > 0;
    }

    public BluetoothGattCharacteristic getBluetoothGattCharacteristic() {
        return mCharacteristic;
    }

    @Override
    public String toString() {
        return "CharacteristicInfo{" +
                "mCharacteristic=" + mCharacteristic +
                '}';
    }

    protected CharacteristicInfo(Parcel in) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) { // API 24+
            mCharacteristic = in.readParcelable(BluetoothGattCharacteristic.class.getClassLoader());
        } else {
            UUID uuid = UUID.fromString(in.readString());
            int properties = in.readInt();
            int permissions = in.readInt();
            mCharacteristic = new BluetoothGattCharacteristic(uuid, properties, permissions);
        }
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) { // API 24+
            dest.writeParcelable(mCharacteristic, flags);
        } else {
            dest.writeString(mCharacteristic.getUuid().toString());
            dest.writeInt(mCharacteristic.getProperties());
            dest.writeInt(mCharacteristic.getPermissions());
        }
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public static final Creator<CharacteristicInfo> CREATOR = new Creator<CharacteristicInfo>() {
        @Override
        public CharacteristicInfo createFromParcel(Parcel in) {
            return new CharacteristicInfo(in);
        }

        @Override
        public CharacteristicInfo[] newArray(int size) {
            return new CharacteristicInfo[size];
        }
    };

}
