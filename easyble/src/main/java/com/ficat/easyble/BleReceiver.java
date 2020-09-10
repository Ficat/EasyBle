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

public class BleReceiver extends BroadcastReceiver {
    private List<BluetoothStateChangedListener> listeners = new ArrayList<>();

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (TextUtils.isEmpty(action)) {
            return;
        }
        switch (action) {
            case BluetoothAdapter.ACTION_STATE_CHANGED:
                for (BluetoothStateChangedListener l : listeners) {
                    if (l != null) {
                        l.onBluetoothStateChanged();
                    }
                }
                break;
        }
    }

    public synchronized void registerBluetoothStateChangedListener(BluetoothStateChangedListener listener) {
        checkNotNull(listener, BluetoothStateChangedListener.class);
        listeners.add(listener);
    }

    public synchronized void unregisterBluetoothStateChangedListener(BluetoothStateChangedListener listener) {
        checkNotNull(listener, BluetoothStateChangedListener.class);
        listeners.remove(listener);
    }

    private void checkNotNull(Object object, Class<?> clasz) {
        if (object == null) {
            String claszSimpleName = clasz.getSimpleName();
            throw new IllegalArgumentException(claszSimpleName + " is null");
        }
    }

    public interface BluetoothStateChangedListener {
        void onBluetoothStateChanged();
    }
}
