package com.ficat.easyble;

import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.text.TextUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by pw on 2018/9/23.
 */

public final class BleReceiver extends BroadcastReceiver {
    private final List<BluetoothStateChangedListener> listeners = new ArrayList<>();

    BleReceiver() {

    }

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (TextUtils.isEmpty(action) || !action.equals(BluetoothAdapter.ACTION_STATE_CHANGED)) {
            return;
        }
        synchronized (this) {
            for (BluetoothStateChangedListener l : listeners) {
                if (l != null) {
                    l.onBluetoothStateChanged();
                }
            }
        }
    }

    public void registerBluetoothStateChangedListener(BluetoothStateChangedListener listener) {
        if (listener == null) {
            throw new IllegalArgumentException("BluetoothStateChangedListener is null");
        }
        synchronized (this) {
            listeners.add(listener);
        }
    }

    public void unregisterBluetoothStateChangedListener(BluetoothStateChangedListener listener) {
        if (listener == null) {
            throw new IllegalArgumentException("BluetoothStateChangedListener is null");
        }
        synchronized (this) {
            listeners.remove(listener);
        }
    }

    public void clearAllListener() {
        synchronized (this) {
            listeners.clear();
        }
    }

    public interface BluetoothStateChangedListener {
        void onBluetoothStateChanged();
    }
}
