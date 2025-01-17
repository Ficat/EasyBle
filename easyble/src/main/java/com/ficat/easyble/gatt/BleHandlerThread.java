package com.ficat.easyble.gatt;

import android.os.HandlerThread;
import android.os.Looper;

/**
 * Defines a HandlerThread class used to run all BLE operation callbacks, and only these
 * callbacks can run in this thread, other messages or events are not expected to send and
 * run this thread. As a result, {@link #getLooper()} {@link #quit()} and {@link #quitSafely()}
 * won't be supported, you can still call these methods but they won't work.
 */
public final class BleHandlerThread extends HandlerThread {
    volatile boolean mLooperPrepared;

    public BleHandlerThread(String name) {
        super(name);
    }

    public BleHandlerThread(String name, int priority) {
        super(name, priority);
    }

    @Deprecated
    @Override
    public Looper getLooper() {
        return null;
    }

    @Deprecated
    @Override
    public boolean quit() {
        //super.quit() doesn't work, it calls getLooper(), but in this class we have
        //deprecated getLooper(), so super.quit() will always return false.
        return super.quit();
    }

    @Deprecated
    @Override
    public boolean quitSafely() {
        //Like the super.quit(), super.quitSafely() will always return false
        return super.quitSafely();
    }

    @Override
    protected void onLooperPrepared() {
        super.onLooperPrepared();
        mLooperPrepared = true;
    }

    boolean isLooperPrepared() {
        return mLooperPrepared;
    }

    Looper getLooperInThread() {
        return super.getLooper();
    }

    boolean quitLooperSafely() {
        Looper looper = super.getLooper();
        if (looper != null) {
            looper.quitSafely();
            return true;
        }
        return false;
    }
}
