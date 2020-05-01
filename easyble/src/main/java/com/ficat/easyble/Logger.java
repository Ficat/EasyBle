package com.ficat.easyble;

import android.util.Log;

public class Logger {
    static boolean SHOW_LOG = false;
    static String TAG = "EasyBle";

    public static void d(String info) {
        if (SHOW_LOG) {
            Log.d(TAG, info);
        }
    }

    public static void e(String info) {
        if (SHOW_LOG) {
            Log.e(TAG, info);
        }
    }

    public static void w(String info) {
        if (SHOW_LOG) {
            Log.w(TAG, info);
        }
    }

    public static void v(String info) {
        if (SHOW_LOG) {
            Log.v(TAG, info);
        }
    }

    public static void i(String info) {
        if (SHOW_LOG) {
            Log.i(TAG, info);
        }
    }

    public static void d(Object obj, String info) {
        if (SHOW_LOG) {
            Log.d(tag(obj), info);
        }
    }

    public static void e(Object obj, String info) {
        if (SHOW_LOG) {
            Log.e(tag(obj), info);
        }
    }

    public static void v(Object obj, String info) {
        if (SHOW_LOG) {
            Log.v(tag(obj), info);
        }
    }

    public static void w(Object obj, String info) {
        if (SHOW_LOG) {
            Log.w(tag(obj), info);
        }
    }

    public static void i(Object obj, String info) {
        if (SHOW_LOG) {
            Log.i(tag(obj), info);
        }
    }

    private static String tag(Object obj) {
        if (obj == null) {
            return TAG;
        }
        if (obj instanceof String) {
            return (String) obj;
        }
        if (obj instanceof Number) {
            return String.valueOf(obj);
        }
        return obj.getClass().getSimpleName();
    }

}
