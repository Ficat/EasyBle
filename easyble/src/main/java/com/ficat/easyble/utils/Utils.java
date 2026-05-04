package com.ficat.easyble.utils;

import android.Manifest;
import android.app.ActivityManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.LocationManager;
import android.os.Build;
import android.os.PowerManager;

import java.util.ArrayList;
import java.util.List;

public class Utils {
    private static final String PERMISSION_BLUETOOTH_SCAN = "android.permission.BLUETOOTH_SCAN";
    private static final String PERMISSION_BLUETOOTH_CONNECT = "android.permission.BLUETOOTH_CONNECT";
    private static final String PERMISSION_BLUETOOTH_ADVERTISE = "android.permission.BLUETOOTH_ADVERTISE";

    /**
     * Get all permissions BLE required
     *
     * @return all BLE permissions
     */
    public static List<String> getBleRequiredPermissions() {
        List<String> list = new ArrayList<>();
        //BLE required permissions
        if (Build.VERSION.SDK_INT >= 31) { //Android12
            //BLUETOOTH_SCAN: enable this central device to scan peripheral devices
            //BLUETOOTH_CONNECT: used to get peripheral device name (BluetoothDevice#getName())
            list.add(PERMISSION_BLUETOOTH_SCAN);
            list.add(PERMISSION_BLUETOOTH_CONNECT);
            list.add(PERMISSION_BLUETOOTH_ADVERTISE);
        } else if (Build.VERSION.SDK_INT >= 29) {//Android10
            list.add(Manifest.permission.ACCESS_FINE_LOCATION);
        } else if (Build.VERSION.SDK_INT >= 23) {//Android6
            list.add(Manifest.permission.ACCESS_COARSE_LOCATION);
        }
        return list;
    }

    /**
     * Check if scan-permission has been granted
     */
    public static boolean scanPermissionGranted(Context context) {
        if (context == null) {
            throw new IllegalArgumentException("Context is null");
        }
        if (Build.VERSION.SDK_INT >= 31) { //Android12
            //BLUETOOTH_SCAN: enable this central device to scan peripheral devices
            //BLUETOOTH_CONNECT: used to get peripheral device name (BluetoothDevice#getName())
            return isPermissionGranted(context, PERMISSION_BLUETOOTH_SCAN) &&
                    isPermissionGranted(context, PERMISSION_BLUETOOTH_CONNECT);
        } else if (Build.VERSION.SDK_INT >= 29) {//Android10
            return isPermissionGranted(context, Manifest.permission.ACCESS_FINE_LOCATION);
        } else if (Build.VERSION.SDK_INT >= 23) {//Android6
            return isPermissionGranted(context, Manifest.permission.ACCESS_COARSE_LOCATION) ||
                    isPermissionGranted(context, Manifest.permission.ACCESS_FINE_LOCATION);
        } else {
            return true;
        }
    }

    /**
     * Check if connection-permission has been granted
     */
    public static boolean connectionPermissionGranted(Context context) {
        if (context == null) {
            throw new IllegalArgumentException("Context is null");
        }
        //Android12(api31) or higher, BLUETOOTH_CONNECT permission is necessary
        return Build.VERSION.SDK_INT < 31 || isPermissionGranted(context, PERMISSION_BLUETOOTH_CONNECT);
    }

    public static boolean isPermissionGranted(Context context, String permission){
        if (context == null) {
            throw new IllegalArgumentException("Context is null");
        }
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.M || context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED;
    }


    public static boolean isForeground(Context context) {
        return isForeground(context, true);
    }

    /**
     * Is the current process in the foreground?
     *
     * @param context                  context
     * @param includeForegroundService if true, apps that are running foreground services will be
     *                                 considered foreground-app
     * @return If true, current process is in the foreground
     */
    public static boolean isForeground(Context context, boolean includeForegroundService) {
        if (context == null) {
            throw new IllegalArgumentException("Context is null");
        }
        ActivityManager manager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        int foregroundImportance = ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND;
        if (Build.VERSION.SDK_INT >= 23 && includeForegroundService) {
            foregroundImportance = ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND_SERVICE;
        }
        int myPid = android.os.Process.myPid();
        for (ActivityManager.RunningAppProcessInfo info : manager.getRunningAppProcesses()) {
//            if (info.processName.equals(context.getPackageName())) {
//                return info.importance <= foregroundImportance;
//            }
            // An app may have more than one process, and context.getPackageName() is just main
            // default main process name, so use PID to determine whether it is the current process
            if (info.pid == myPid) {
                return info.importance <= foregroundImportance;
            }
        }
        return false;
    }

    /**
     * Is the screen on?
     *
     * @param context context
     * @return if true, screen is on
     */
    public static boolean isScreenOn(Context context) {
        if (context == null) {
            throw new IllegalArgumentException("Context is null");
        }
        PowerManager manager = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        return Build.VERSION.SDK_INT >= 20 ? manager.isInteractive() : manager.isScreenOn();
    }

    /**
     * Is GPS on?
     *
     * @param context context
     * @return true if GPS is turned on
     */
    public static boolean isGpsOn(Context context) {
        if (context == null) {
            throw new IllegalArgumentException("Context is null");
        }
        LocationManager locationManager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
    }

}
