package com.ficat.easyble.gatt.data;

import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.os.Build;
import android.os.Parcel;
import android.os.Parcelable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;


public class ServiceInfo implements Parcelable {
    private final BluetoothGattService mService;
    private final List<CharacteristicInfo> mCharacteristics;

    public ServiceInfo(BluetoothGattService bluetoothGattService) {
        this.mService = bluetoothGattService;
        List<BluetoothGattCharacteristic> list = bluetoothGattService.getCharacteristics();
        this.mCharacteristics = new ArrayList<>(list.size());
        for (BluetoothGattCharacteristic ch : list) {
            this.mCharacteristics.add(new CharacteristicInfo(ch));
        }
    }

    public BluetoothGattService getBluetoothGattService() {
        return mService;
    }

    public UUID getUuid() {
        return mService.getUuid();
    }

    public List<CharacteristicInfo> getCharacteristics() {
        return new ArrayList<>(mCharacteristics);
    }

    public CharacteristicInfo getCharacteristic(String characteristicUuid) {
        for (CharacteristicInfo ch : mCharacteristics) {
            if (ch.getUuid().toString().equals(characteristicUuid)) {
                return ch;
            }
        }
        return null;
    }

    @Override
    public String toString() {
        return "ServiceInfo{" +
                "mService=" + mService +
                ", mCharacteristics=" + mCharacteristics +
                '}';
    }

    // Parcelable implementation
    protected ServiceInfo(Parcel in) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) { // API 24+
            mService = in.readParcelable(BluetoothGattService.class.getClassLoader());
        } else {
            UUID uuid = UUID.fromString(in.readString());
            int type = in.readInt();
            mService = new BluetoothGattService(uuid, type);
        }

        mCharacteristics = new ArrayList<>();
        in.readList(mCharacteristics, CharacteristicInfo.class.getClassLoader());
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) { // API 24+
            dest.writeParcelable(mService, flags);
        } else {
            dest.writeString(mService.getUuid().toString());
            dest.writeInt(mService.getType());
        }
        dest.writeList(mCharacteristics);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public static final Creator<ServiceInfo> CREATOR = new Creator<ServiceInfo>() {
        @Override
        public ServiceInfo createFromParcel(Parcel in) {
            return new ServiceInfo(in);
        }

        @Override
        public ServiceInfo[] newArray(int size) {
            return new ServiceInfo[size];
        }
    };
}
