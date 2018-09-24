package com.ficat.easyble;


import android.os.Handler;
import android.os.Looper;

public class MainThreadExecutor {
    private static Handler sHandler = new Handler(Looper.getMainLooper());

    public static void execute(Runnable r) {
        if (isMainThread()) {
            r.run();
        } else {
            executeDelay(r, 0);
        }
    }

    public static void executeDelay(Runnable r, long delayMillis) {
        if (delayMillis < 0) {
            delayMillis = 0;
        }
        sHandler.postDelayed(r, delayMillis);
    }

    public static boolean isMainThread() {
        return Looper.myLooper() == Looper.getMainLooper();
    }
}
