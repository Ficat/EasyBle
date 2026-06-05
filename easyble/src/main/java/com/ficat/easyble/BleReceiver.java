package com.ficat.easyble;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.text.TextUtils;

/**
 * Created by pw on 2018/9/23.
 */

public final class BleReceiver extends BroadcastReceiver {
    private BluetoothStateChangeListener mBluetoothStateChangeListener;
    private BluetoothBondStateChangeListener mBluetoothBondStateChangeListener;

    BleReceiver() {

    }

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (TextUtils.isEmpty(action)) {
            return;
        }
        switch (action) {
            case BluetoothAdapter.ACTION_STATE_CHANGED:
                int btState = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, 0);
                if (mBluetoothStateChangeListener != null) {
                    mBluetoothStateChangeListener.onBluetoothStateChanged(btState);
                }
                break;
            case BluetoothDevice.ACTION_BOND_STATE_CHANGED:
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                int newState = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.ERROR);
                int previousState = intent.getIntExtra(BluetoothDevice.EXTRA_PREVIOUS_BOND_STATE, BluetoothDevice.ERROR);
                if (device != null && mBluetoothBondStateChangeListener != null) {
                    mBluetoothBondStateChangeListener.onBluetoothBondStateChanged(newState, previousState, device);
                }
                break;
        }

    }

    public void setBluetoothStateChangeListener(BluetoothStateChangeListener listener) {
        if (listener == null) {
            throw new IllegalArgumentException("BluetoothStateChangedListener is null");
        }
        mBluetoothStateChangeListener = listener;
    }

    public void cancelBluetoothStateChangeListener() {
        mBluetoothStateChangeListener = null;
    }

    public void setBluetoothBondStateChangeListener(BluetoothBondStateChangeListener listener) {
        if (listener == null) {
            throw new IllegalArgumentException("BluetoothBondStateChangeListener is null");
        }
        mBluetoothBondStateChangeListener = listener;
    }

    public void cancelBluetoothBondStateChangeListener() {
        mBluetoothBondStateChangeListener = null;
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

    public interface BluetoothBondStateChangeListener {
        /**
         * @param newState      New bond state, it is one of the following:
         *                      {@link BluetoothDevice#BOND_NONE}
         *                      {@link BluetoothDevice#BOND_BONDING}
         *                      {@link BluetoothDevice#BOND_BONDED}
         * @param previousState Previous bond state, one of the following:
         *                      {@link BluetoothDevice#BOND_NONE}
         *                      {@link BluetoothDevice#BOND_BONDING}
         *                      {@link BluetoothDevice#BOND_BONDED}
         * @param device        The remote device
         */
        void onBluetoothBondStateChanged(int newState, int previousState, BluetoothDevice device);
    }
}
