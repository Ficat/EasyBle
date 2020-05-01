package com.ficat.easyble.utils;

import java.util.ArrayList;
import java.util.List;

public class SplitBleDataUtils {
    public static List<byte[]> getBatchData(byte[] originalData, int lengthPerPackage) {
        List<byte[]> batchData = new ArrayList<>();
        int packageNumber = (originalData.length % lengthPerPackage == 0) ?
                originalData.length / lengthPerPackage :
                originalData.length / lengthPerPackage + 1;
        for (int i = 1; i <= packageNumber; i++) {
            int start = lengthPerPackage * (i - 1);
            int end = lengthPerPackage * i - 1;
            if (i == packageNumber) {
                end = originalData.length - 1;
            }
            batchData.add(getSpecifyIndexBytes(originalData, start, end));
        }
        return batchData;
    }

    public static byte[] getSpecifyIndexBytes(byte[] originalData, int start, int end) {
        if (start > end || end > originalData.length - 1) {
            return null;
        }
        byte[] packageData = new byte[end - start + 1];
        for (int i = 0; start <= end; i++, start++) {
            packageData[i] = originalData[start];
        }
        return packageData;
    }
}
