package com.ficat.easyble.scan;

import android.os.Parcel;
import android.os.ParcelUuid;
import android.os.Parcelable;
import android.util.SparseArray;

import com.ficat.easyble.utils.Logger;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.*;

public class BleScanRecord implements Parcelable {
    public static final int DATA_TYPE_FLAGS = 0x01;
    public static final int DATA_TYPE_SERVICE_UUIDS_16_BIT_PARTIAL = 0x02;
    public static final int DATA_TYPE_SERVICE_UUIDS_16_BIT_COMPLETE = 0x03;
    public static final int DATA_TYPE_SERVICE_UUIDS_32_BIT_PARTIAL = 0x04;
    public static final int DATA_TYPE_SERVICE_UUIDS_32_BIT_COMPLETE = 0x05;
    public static final int DATA_TYPE_SERVICE_UUIDS_128_BIT_PARTIAL = 0x06;
    public static final int DATA_TYPE_SERVICE_UUIDS_128_BIT_COMPLETE = 0x07;
    public static final int DATA_TYPE_LOCAL_NAME_SHORT = 0x08;
    public static final int DATA_TYPE_LOCAL_NAME_COMPLETE = 0x09;
    public static final int DATA_TYPE_TX_POWER_LEVEL = 0x0A;
    public static final int DATA_TYPE_SERVICE_SOLICITATION_UUIDS_16_BIT = 0x14;
    public static final int DATA_TYPE_SERVICE_SOLICITATION_UUIDS_32_BIT = 0x1F;
    public static final int DATA_TYPE_SERVICE_SOLICITATION_UUIDS_128_BIT = 0x15;
    public static final int DATA_TYPE_SERVICE_DATA_16_BIT = 0x16;
    public static final int DATA_TYPE_SERVICE_DATA_32_BIT = 0x20;
    public static final int DATA_TYPE_SERVICE_DATA_128_BIT = 0x21;
    public static final int DATA_TYPE_MANUFACTURER_SPECIFIC_DATA = 0xFF;

    private static final int UUID_LENGTH_2 = 2;
    private static final int UUID_LENGTH_4 = 4;
    private static final int UUID_LENGTH_16 = 16;

    private static final UUID BASE_UUID = UUID.fromString("00000000-0000-1000-8000-00805F9B34FB");

    private final int mAdvertiseFlags;
    private final List<ParcelUuid> mServiceUuids;
    private final List<ParcelUuid> mServiceSolicitationUuids;
    private final SparseArray<byte[]> mManufacturerSpecificData;
    private final Map<ParcelUuid, byte[]> mServiceData;
    private final int mTxPowerLevel;
    private final String mDeviceName;
    private final byte[] mBytes;
    private final Map<Integer, List<byte[]>> mAdvertisingDataMap;

    private BleScanRecord(List<ParcelUuid> serviceUuids, List<ParcelUuid> solicitationUuids,
                          SparseArray<byte[]> manufacturerData, Map<ParcelUuid, byte[]> serviceData,
                          int advertiseFlags, int txPowerLevel, String deviceName,
                          Map<Integer, List<byte[]>> rawMap, byte[] bytes) {

        this.mServiceUuids = serviceUuids;
        this.mServiceSolicitationUuids = solicitationUuids;
        this.mManufacturerSpecificData = manufacturerData;
        this.mServiceData = serviceData;
        this.mAdvertiseFlags = advertiseFlags;
        this.mTxPowerLevel = txPowerLevel;
        this.mDeviceName = deviceName;
        this.mAdvertisingDataMap = rawMap;
        this.mBytes = bytes;
    }

    public int getAdvertiseFlags() {
        return mAdvertiseFlags;
    }

    /**
     * Get service uuid list
     * <p>
     * Note that it may return null or empty list
     * </p>
     */
    public List<ParcelUuid> getServiceUuids() {
        return mServiceUuids;
    }

    /**
     * Get a list of service solicitation UUIDs advertised by the device.
     * <p>
     * Note that it may return null or empty list
     * </p>
     */
    public List<ParcelUuid> getServiceSolicitationUuids() {
        return mServiceSolicitationUuids;
    }

    /**
     * Get manufacturer data
     * <p>
     * Note that it may return null or empty sparse-array
     * </p>
     */
    public SparseArray<byte[]> getManufacturerSpecificData() {
        return mManufacturerSpecificData;
    }

    /**
     * Get service data
     * <p>
     * Note that it may return null or empty map
     * </p>
     */
    public Map<ParcelUuid, byte[]> getServiceData() {
        return mServiceData;
    }

    /**
     * Get the transmission power level (in dBm) included in the advertisement
     */
    public int getTxPowerLevel() {
        return mTxPowerLevel;
    }

    /**
     * Get device name parsed by scan result bytes
     */
    public String getDeviceName() {
        return mDeviceName;
    }

    /**
     * Get a map of advertising data types to their raw byte values. The keys
     * are AD types defined by the Bluetooth specification, and the values are
     * the corresponding raw data payloads.
     * <p>
     * Note that it may return null or empty map
     * </p>
     */
    public Map<Integer, List<byte[]>> getAdvertisingDataMap() {
        return mAdvertisingDataMap;
    }

    /**
     * Get original scan result bytes
     */
    public byte[] getBytes() {
        return mBytes;
    }

    @Override
    public String toString() {
        return "BleScanRecord{" +
                "mAdvertiseFlags=" + mAdvertiseFlags +
                ", mServiceUuids=" + mServiceUuids +
                ", mServiceSolicitationUuids=" + mServiceSolicitationUuids +
                ", mManufacturerSpecificData=" + toString(mManufacturerSpecificData) +
                ", mServiceData=" + toString(mServiceData) +
                ", mTxPowerLevel=" + mTxPowerLevel +
                ", mDeviceName='" + mDeviceName +
                '}';
    }

    /**
     * LE Limited Discoverable Mode
     *
     * <p>
     * Defined by Bluetooth SIG (Core Spec Supplement).
     * Indicates the device is in a limited discoverable mode, meaning it is only
     * discoverable for a short period (typically during pairing or initialization).
     * </p>
     *
     * @param flags Flags field
     * @return true if bit0 is set
     */
    public static boolean isLeLimitedDiscoverable(int flags) {
        return (flags & 0x01) != 0;
    }

    /**
     * LE General Discoverable Mode
     *
     * <p>
     * Defined by Bluetooth SIG.
     * Indicates the device is generally discoverable with no time limitation.
     * </p>
     *
     * @param flags Flags field
     * @return true if bit1 is set
     */
    public static boolean isLeGeneralDiscoverable(int flags) {
        return (flags & 0x02) != 0;
    }

    /**
     * BR/EDR Not Supported
     *
     * <p>
     * Defined by Bluetooth SIG.
     * Indicates the device does NOT support BR/EDR (Classic Bluetooth),
     * meaning it is a BLE-only device.
     * </p>
     *
     * @param flags Flags field
     * @return true if bit2 is set
     */
    public static boolean isBrEdrNotSupported(int flags) {
        return (flags & 0x04) != 0;
    }

    /**
     * Simultaneous LE and BR/EDR to Same Device Capable (Controller)
     *
     * <p>
     * Defined by Bluetooth SIG.
     * Indicates that the Bluetooth controller supports simultaneous connections
     * over LE and BR/EDR to the same remote device.
     * </p>
     *
     * @param flags Flags field
     * @return true if bit3 is set
     */
    public static boolean isSimultaneousLeAndBrEdrToSameDeviceCapable(int flags) {
        return (flags & 0x08) != 0;
    }

    /**
     * Parse scan bytes
     *
     * @param scanRecord original scan data
     * @return BleScanRecord
     */
    public static BleScanRecord parseFromBytes(byte[] scanRecord) {
        BleScanRecord defaultResult = new BleScanRecord(null, null,
                null, null, -1, Integer.MIN_VALUE,
                null, null, scanRecord);

        if (scanRecord == null || scanRecord.length == 0) {
            return defaultResult;
        }

        // Decode scan record
        List<AdStructure> list = decode(scanRecord);
        if (list == null || list.isEmpty()) {
            return defaultResult;
        }

        int flags = -1;
        int txPower = Integer.MIN_VALUE;
        String name = null;
        List<ParcelUuid> serviceUuids = new ArrayList<>();
        List<ParcelUuid> solicitationUuids = new ArrayList<>();
        SparseArray<byte[]> manufacturer = new SparseArray<>();
        Map<ParcelUuid, byte[]> serviceData = new HashMap<>();
        Map<Integer, List<byte[]>> raw = new HashMap<>();

        for (AdStructure s : list) {
            List<byte[]> typeDataList = raw.get(s.type);
            if (typeDataList == null) {
                typeDataList = new ArrayList<>();
            }
            typeDataList.add(s.data);
            raw.put(s.type, typeDataList);
            switch (s.type) {
                case DATA_TYPE_FLAGS:
                    if (s.data != null && s.data.length > 0) {
                        flags = s.data[0] & 0xFF;
                    }
                    break;
                case DATA_TYPE_LOCAL_NAME_SHORT:
                case DATA_TYPE_LOCAL_NAME_COMPLETE:
                    if (s.data != null && s.data.length > 0) {
                        name = new String(s.data);
                    }
                    break;
                case DATA_TYPE_TX_POWER_LEVEL:
                    if (s.data != null && s.data.length > 0) {
                        txPower = s.data[0];
                    }
                    break;
                case DATA_TYPE_SERVICE_UUIDS_16_BIT_PARTIAL:
                case DATA_TYPE_SERVICE_UUIDS_16_BIT_COMPLETE:
                    parseUuidList(s.data, UUID_LENGTH_2, serviceUuids);
                    break;
                case DATA_TYPE_SERVICE_UUIDS_32_BIT_PARTIAL:
                case DATA_TYPE_SERVICE_UUIDS_32_BIT_COMPLETE:
                    parseUuidList(s.data, UUID_LENGTH_4, serviceUuids);
                    break;
                case DATA_TYPE_SERVICE_UUIDS_128_BIT_PARTIAL:
                case DATA_TYPE_SERVICE_UUIDS_128_BIT_COMPLETE:
                    parseUuidList(s.data, UUID_LENGTH_16, serviceUuids);
                    break;
                case DATA_TYPE_SERVICE_SOLICITATION_UUIDS_16_BIT:
                    parseUuidList(s.data, UUID_LENGTH_2, solicitationUuids);
                    break;
                case DATA_TYPE_SERVICE_SOLICITATION_UUIDS_32_BIT:
                    parseUuidList(s.data, UUID_LENGTH_4, solicitationUuids);
                    break;
                case DATA_TYPE_SERVICE_SOLICITATION_UUIDS_128_BIT:
                    parseUuidList(s.data, UUID_LENGTH_16, solicitationUuids);
                    break;
                case DATA_TYPE_SERVICE_DATA_16_BIT:
                    parseServiceData(s.data, UUID_LENGTH_2, serviceData);
                    break;
                case DATA_TYPE_SERVICE_DATA_32_BIT:
                    parseServiceData(s.data, UUID_LENGTH_4, serviceData);
                    break;
                case DATA_TYPE_SERVICE_DATA_128_BIT:
                    parseServiceData(s.data, UUID_LENGTH_16, serviceData);
                    break;
                case DATA_TYPE_MANUFACTURER_SPECIFIC_DATA:
                    parseManufacturer(s.data, manufacturer);
                    break;
                default:
                    // ignore
                    break;
            }

        }
        return new BleScanRecord(
                serviceUuids,
                solicitationUuids,
                manufacturer,
                serviceData,
                flags,
                txPower,
                name,
                raw,
                scanRecord);
    }

    private static List<AdStructure> decode(byte[] data) {
        if (data == null || data.length == 0) {
            return null;
        }
        List<AdStructure> list = new ArrayList<>();
        int index = 0;

        while (index < data.length) {
            int len = data[index++] & 0xFF;
            if (len == 0) break;
            if (index >= data.length) break;

            // type
            int type = data[index] & 0xFF;

            // payload
            int payloadLen = len - 1;
            int payloadStart = index + 1;
            if (payloadStart + payloadLen > data.length) {
                break;
            }
            byte[] payload = new byte[payloadLen];
            System.arraycopy(data, payloadStart, payload, 0, payloadLen);

            list.add(new AdStructure(type, payload));
            index += len;
        }

        return list;
    }

    /**
     * Parse uuid list
     */
    private static void parseUuidList(byte[] data, int size, List<ParcelUuid> out) {
        // Check size length
        if (size != UUID_LENGTH_2 && size != UUID_LENGTH_4 && size != UUID_LENGTH_16) {
            return;
        }
        // Check data and out
        if (data == null || data.length == 0 || out == null) {
            return;
        }
        try {
            for (int i = 0; i + size <= data.length; i += size) {
                byte[] slice = new byte[size];
                System.arraycopy(data, i, slice, 0, size);
                out.add(parseUuid(slice));
            }
        } catch (Exception e) {
            Logger.e("Failed to parse uuid list, data=" + Arrays.toString(data));
        }
    }

    /**
     * Parse service data
     */
    private static void parseServiceData(byte[] data, int uuidLen, Map<ParcelUuid, byte[]> out) {
        if (data == null || data.length < uuidLen || out == null) {
            return;
        }
        // UUID
        byte[] uuidBytes = new byte[uuidLen];
        System.arraycopy(data, 0, uuidBytes, 0, uuidLen);
        ParcelUuid uuid = parseUuid(uuidBytes);

        // Data
        int valueLen = data.length - uuidLen;
        if (valueLen <= 0) {
            return;
        }
        byte[] value = new byte[valueLen];
        System.arraycopy(data, uuidLen, value, 0, valueLen);

        out.put(uuid, value);
    }

    /**
     * Parse manufacturer data
     */
    private static void parseManufacturer(byte[] data, SparseArray<byte[]> out) {
        // Check data and out
        if (data == null || data.length < 2 || out == null) {
            return;
        }
        // Manufacturer id
        int id = ((data[1] & 0xFF) << 8) | (data[0] & 0xFF);

        // Manufacturer data
        int valueLen = data.length - 2;
        if (valueLen <= 0) {
            return;
        }
        byte[] value = new byte[valueLen];
        System.arraycopy(data, 2, value, 0, valueLen);

        // If any existing data exists, merge it
        byte[] old = out.get(id);
        if (old != null) {
            byte[] merged = new byte[old.length + value.length];
            System.arraycopy(old, 0, merged, 0, old.length);
            System.arraycopy(value, 0, merged, old.length, value.length);
            out.put(id, merged);
        } else {
            out.put(id, value);
        }
    }

    /**
     * Parse uuid bytes
     *
     * @param uuidBytes uuid bytes
     * @return ParcelUuid
     */
    private static ParcelUuid parseUuid(byte[] uuidBytes) {
        if (uuidBytes == null) {
            throw new IllegalArgumentException("uuidBytes is null");
        }
        if (uuidBytes.length != UUID_LENGTH_2 && uuidBytes.length != UUID_LENGTH_4 &&
                uuidBytes.length != UUID_LENGTH_16) {
            throw new IllegalArgumentException("Invalid uuidBytes length: " + uuidBytes.length);
        }

        if (uuidBytes.length == UUID_LENGTH_16) {
            ByteBuffer buf = ByteBuffer.wrap(uuidBytes).order(ByteOrder.LITTLE_ENDIAN);
            return new ParcelUuid(new UUID(buf.getLong(8), buf.getLong(0)));
        }

        long value = 0;
        for (int i = 0; i < uuidBytes.length; i++) {
            value |= (long) (uuidBytes[i] & 0xFF) << (8 * i);
        }

        return new ParcelUuid(new UUID(BASE_UUID.getMostSignificantBits() + (value << 32),
                BASE_UUID.getLeastSignificantBits()));
    }

    private static String toString(SparseArray<byte[]> sparseArray) {
        if (sparseArray == null) {
            return "null";
        }
        StringBuilder build = new StringBuilder();
        build.append('{');
        for (int i = 0; i < sparseArray.size(); ++i) {
            int key = sparseArray.keyAt(i);
            byte[] value = sparseArray.valueAt(i);
            build.append(key)
                    .append("=")
                    .append(Arrays.toString(value));
            if (i != sparseArray.size() - 1) {
                build.append(", ");
            }
        }
        build.append('}');
        return build.toString();
    }

    private static String toString(Map<ParcelUuid, byte[]> map) {
        if (map == null) {
            return "null";
        }
        StringBuilder build = new StringBuilder();
        build.append('{');
        int size = map.size();
        int index = 0;
        for (Map.Entry<ParcelUuid, byte[]> entry : map.entrySet()) {
            ParcelUuid key = entry.getKey();
            byte[] value = entry.getValue();
            build.append(key.toString())
                    .append("=")
                    .append(Arrays.toString(value));
            if (index != size - 1) {
                build.append(", ");
            }
            index++;
        }
        build.append('}');
        return build.toString();
    }

    private static class AdStructure {
        final int type;
        final byte[] data;

        AdStructure(int type, byte[] data) {
            this.type = type;
            this.data = data;
        }
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(mAdvertiseFlags);
        dest.writeInt(mTxPowerLevel);
        dest.writeString(mDeviceName);

        // serviceUuids
        dest.writeTypedList(mServiceUuids);

        // solicitationUuids
        dest.writeTypedList(mServiceSolicitationUuids);

        // manufacturer
        if (mManufacturerSpecificData != null) {
            dest.writeInt(mManufacturerSpecificData.size());
            for (int i = 0; i < mManufacturerSpecificData.size(); i++) {
                dest.writeInt(mManufacturerSpecificData.keyAt(i));
                dest.writeByteArray(mManufacturerSpecificData.valueAt(i));
            }
        } else {
            dest.writeInt(-1);
        }

        // serviceData
        if (mServiceData != null) {
            dest.writeInt(mServiceData.size());
            for (Map.Entry<ParcelUuid, byte[]> entry : mServiceData.entrySet()) {
                dest.writeParcelable(entry.getKey(), flags);
                dest.writeByteArray(entry.getValue());
            }
        } else {
            dest.writeInt(-1);
        }

        // raw map (Map<Integer, List<byte[]>>)
        if (mAdvertisingDataMap != null) {
            dest.writeInt(mAdvertisingDataMap.size());
            for (Map.Entry<Integer, List<byte[]>> entry : mAdvertisingDataMap.entrySet()) {
                dest.writeInt(entry.getKey());
                List<byte[]> list = entry.getValue();
                dest.writeInt(list.size());
                for (byte[] arr : list) {
                    dest.writeByteArray(arr);
                }
            }
        } else {
            dest.writeInt(-1);
        }

        // bytes
        dest.writeByteArray(mBytes);
    }

    protected BleScanRecord(Parcel in) {
        mAdvertiseFlags = in.readInt();
        mTxPowerLevel = in.readInt();
        mDeviceName = in.readString();

        mServiceUuids = in.createTypedArrayList(ParcelUuid.CREATOR);
        mServiceSolicitationUuids = in.createTypedArrayList(ParcelUuid.CREATOR);

        // manufacturer
        int manuSize = in.readInt();
        if (manuSize >= 0) {
            mManufacturerSpecificData = new SparseArray<>();
            for (int i = 0; i < manuSize; i++) {
                int key = in.readInt();
                byte[] value = in.createByteArray();
                mManufacturerSpecificData.put(key, value);
            }
        } else {
            mManufacturerSpecificData = null;
        }

        // serviceData
        int serviceSize = in.readInt();
        if (serviceSize >= 0) {
            mServiceData = new HashMap<>();
            for (int i = 0; i < serviceSize; i++) {
                ParcelUuid key = in.readParcelable(ParcelUuid.class.getClassLoader());
                byte[] value = in.createByteArray();
                mServiceData.put(key, value);
            }
        } else {
            mServiceData = null;
        }

        // advertisingDataMap
        int rawSize = in.readInt();
        if (rawSize >= 0) {
            mAdvertisingDataMap = new HashMap<>();
            for (int i = 0; i < rawSize; i++) {
                int key = in.readInt();
                int listSize = in.readInt();
                List<byte[]> list = new ArrayList<>();
                for (int j = 0; j < listSize; j++) {
                    list.add(in.createByteArray());
                }
                mAdvertisingDataMap.put(key, list);
            }
        } else {
            mAdvertisingDataMap = null;
        }

        mBytes = in.createByteArray();
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public static final Creator<BleScanRecord> CREATOR = new Creator<BleScanRecord>() {
        @Override
        public BleScanRecord createFromParcel(Parcel in) {
            return new BleScanRecord(in);
        }

        @Override
        public BleScanRecord[] newArray(int size) {
            return new BleScanRecord[size];
        }
    };

}