package com.ficat.easyble.scan;

import android.text.TextUtils;

import com.ficat.easyble.utils.Logger;

import java.util.UUID;

public class BleScanFilter {
    private final String mDeviceName;
    private final boolean mFuzzyDeviceName;
    private final String mDeviceAddress;
    private final UUID mServiceUuid;

    private BleScanFilter(String mDeviceName, boolean mFuzzyDeviceName, String mDeviceAddress,
                          UUID mServiceUuid) {
        this.mDeviceName = mDeviceName;
        this.mFuzzyDeviceName = mFuzzyDeviceName;
        this.mDeviceAddress = mDeviceAddress;
        this.mServiceUuid = mServiceUuid;
    }

    public String getDeviceName() {
        return mDeviceName;
    }

    public boolean isFuzzyDeviceName() {
        return mFuzzyDeviceName;
    }

    public String getDeviceAddress() {
        return mDeviceAddress;
    }

    public UUID getServiceUuid() {
        return mServiceUuid;
    }

    @Override
    public String toString() {
        return "BleScanFilter{" +
                "mDeviceName='" + mDeviceName + '\'' +
                ", mFuzzyDeviceName=" + mFuzzyDeviceName +
                ", mDeviceAddress='" + mDeviceAddress + '\'' +
                ", mServiceUuid=" + mServiceUuid +
                '}';
    }

    public static final class Builder {
        private String mDeviceName;
        private boolean mFuzzyDeviceName;
        private String mDeviceAddress;
        private UUID mServiceUuid;

        public Builder setDeviceName(String deviceName) {
            return setDeviceName(deviceName, false);
        }

        public Builder setDeviceName(String deviceName, boolean fuzzy) {
            if (fuzzy && TextUtils.isEmpty(deviceName)) {
                Logger.w("You enabled fuzzy device name matching, but provided a null or empty deviceName");
            }
            mDeviceName = deviceName;
            mFuzzyDeviceName = fuzzy;
            return this;
        }

        public Builder setDeviceAddress(String deviceAddress) {
            mDeviceAddress = deviceAddress;
            return this;
        }

        public Builder setServiceUuid(UUID serviceUuid) {
            mServiceUuid = serviceUuid;
            return this;
        }

        public Builder copyFrom(BleScanFilter filter) {
            if (filter != null) {
                mDeviceName = filter.mDeviceName;
                mFuzzyDeviceName = filter.mFuzzyDeviceName;
                mServiceUuid = filter.mServiceUuid;
                mDeviceAddress = filter.mDeviceAddress;
            }
            return this;
        }

        public BleScanFilter build() {
            return new BleScanFilter(mDeviceName, mFuzzyDeviceName, mDeviceAddress, mServiceUuid);
        }
    }
}
