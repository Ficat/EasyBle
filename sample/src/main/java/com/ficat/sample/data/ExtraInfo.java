package com.ficat.sample.data;

import android.os.Parcel;
import android.os.Parcelable;

import java.util.Arrays;

public class ExtraInfo implements Parcelable {
    private String note;
    private int rssi;
    private byte[] scanRecordBytes;

    public ExtraInfo() {

    }

    public String getNote() {
        return note;
    }

    public void setNote(String remarks) {
        this.note = remarks;
    }

    public int getRssi() {
        return rssi;
    }

    public void setRssi(int rssi) {
        this.rssi = rssi;
    }

    public byte[] getScanRecordBytes() {
        return scanRecordBytes;
    }

    public void setScanRecordBytes(byte[] scanRecordBytes) {
        this.scanRecordBytes = scanRecordBytes;
    }

    @Override
    public String toString() {
        return "Extra{" +
                "note='" + note + '\'' +
                ", rssi=" + rssi +
                ", scanRecordBytes=" + Arrays.toString(scanRecordBytes) +
                '}';
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(this.note);
        dest.writeInt(this.rssi);
        dest.writeByteArray(scanRecordBytes);
    }

    protected ExtraInfo(Parcel in) {
        this.note = in.readString();
        this.rssi = in.readInt();
        this.scanRecordBytes = in.createByteArray();
    }

    public static final Creator<ExtraInfo> CREATOR = new Creator<ExtraInfo>() {
        @Override
        public ExtraInfo createFromParcel(Parcel source) {
            return new ExtraInfo(source);
        }

        @Override
        public ExtraInfo[] newArray(int size) {
            return new ExtraInfo[size];
        }
    };
}
