package com.ficat.sample.data;

import android.os.Parcel;
import android.os.Parcelable;

import com.ficat.easyble.scan.BleScanRecord;

public class ExtraInfo implements Parcelable {
    private String note;
    private int rssi;

    private BleScanRecord bleScanRecord;

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

    public BleScanRecord getBleScanRecord() {
        return bleScanRecord;
    }

    public void setBleScanRecord(BleScanRecord bleScanRecord) {
        this.bleScanRecord = bleScanRecord;
    }

    @Override
    public String toString() {
        return "ExtraInfo{" +
                "note='" + note + '\'' +
                ", rssi=" + rssi +
                ", bleScanRecord=" + bleScanRecord +
                '}';
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(note);
        dest.writeInt(rssi);
        dest.writeParcelable(bleScanRecord, flags);
    }

    protected ExtraInfo(Parcel in) {
        note = in.readString();
        rssi = in.readInt();
        bleScanRecord = in.readParcelable(BleScanRecord.class.getClassLoader());
    }

    public static final Creator<ExtraInfo> CREATOR = new Creator<ExtraInfo>() {
        @Override
        public ExtraInfo createFromParcel(Parcel in) {
            return new ExtraInfo(in);
        }

        @Override
        public ExtraInfo[] newArray(int size) {
            return new ExtraInfo[size];
        }
    };
}
