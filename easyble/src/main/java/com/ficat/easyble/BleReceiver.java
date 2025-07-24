package com.ficat.easyble;

import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.text.TextUtils;

/**
 * Created by pw on 2018/9/23.
 */

public final class BleReceiver extends BroadcastReceiver {
    private BluetoothStateChangeListener mListener;

    BleReceiver() {

    }

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (TextUtils.isEmpty(action) || !action.equals(BluetoothAdapter.ACTION_STATE_CHANGED)) {
            return;
        }
        int btState = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, 0);
        if (mListener != null) {
            mListener.onBluetoothStateChanged(btState);
        }
    }

    public void setBluetoothStateChangeListener(BluetoothStateChangeListener listener) {
        if (listener == null) {
            throw new IllegalArgumentException("BluetoothStateChangedListener is null");
        }
        mListener = listener;
    }

    public void removeBluetoothStateChangeListener() {
        mListener = null;
    }

    public interface BluetoothStateChangeListener {
        /**
         * Called back while bluetooth state changed
         *
         * @param state Possible values are:
         *              {@link BluetoothAdapter#STATE_OFF},
         *              {@link BluetoothAdapter#STATE_TURNING_ON},
         *              {@link BluetoothAdapter#STATE_ON},
         *              {@link BluetoothAdapter#STATE_TURNING_OFF},
         */
        void onBluetoothStateChanged(int state);
    }
}
