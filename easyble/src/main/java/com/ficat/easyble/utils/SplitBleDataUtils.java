package com.ficat.easyble.utils;

import java.util.LinkedList;
import java.util.Queue;

public class SplitBleDataUtils {
    public static Queue<byte[]> getBatchData(byte[] originalData, int lengthPerPackage) {
        Queue<byte[]> batchData = new LinkedList<>();
        int packageNumber = (originalData.length % lengthPerPackage == 0) ?
                originalData.length / lengthPerPackage :
                originalData.length / lengthPerPackage + 1;
        for (int i = 1; i <= packageNumber; i++) {
            int start = lengthPerPackage * (i - 1);
            int end = lengthPerPackage * i - 1;
            if (i == packageNumber) {
                end = originalData.length - 1;
            }
            batchData.offer(getSpecifyIndexBytes(originalData, start, end));
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
