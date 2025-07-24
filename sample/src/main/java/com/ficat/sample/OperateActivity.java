package com.ficat.sample;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.os.Bundle;
import android.os.Parcelable;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.text.TextUtils;
import android.view.View;
import android.widget.EditText;
import android.widget.ExpandableListView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.ficat.easyble.BleDevice;
import com.ficat.easyble.BleManager;
import com.ficat.easyble.utils.BluetoothGattUtils;
import com.ficat.easyble.utils.Logger;
import com.ficat.easyble.BleErrorCodes;
import com.ficat.easyble.gatt.callback.BleConnectCallback;
import com.ficat.easyble.gatt.callback.BleNotifyCallback;
import com.ficat.easyble.gatt.callback.BleReadCallback;
import com.ficat.easyble.gatt.callback.BleRssiCallback;
import com.ficat.easyble.gatt.callback.BleWriteCallback;
import com.ficat.sample.adapter.DeviceServiceInfoAdapter;
import com.ficat.sample.data.ExtraInfo;
import com.ficat.sample.utils.ByteUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class OperateActivity extends AppCompatActivity implements View.OnClickListener {
    public static final String KEY_DEVICE_INFO = "keyDeviceInfo";

    private BleDevice device;
    private LinearLayout llWrite, llRead;
    private EditText etWrite;
    private ProgressBar pb;
    private TextView tvConnectionState, tvReadResult, tvWriteResult,
            tvNotify, tvInfoCurrentUuid, tvInfoNotification;
    private ExpandableListView elv;
    private List<BluetoothGattService> groupList = new ArrayList<>();
    private List<List<BluetoothGattCharacteristic>> childList = new ArrayList<>();
    private List<UUID> notifySuccessUuids = new ArrayList<>();
    private DeviceServiceInfoAdapter adapter;
    private BluetoothGattService curService;
    private BluetoothGattCharacteristic curCharacteristic;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_operate);
        initData();
        initView();
        initElv();
    }

    private void initData() {
        device = getIntent().getParcelableExtra(KEY_DEVICE_INFO);
        Parcelable p = device.getParcelableExtra();
        if (p instanceof ExtraInfo) {
            ExtraInfo e = (ExtraInfo) p;
            Logger.i("Extra info=" + e.toString());
        }
        addDeviceInfoDataAndUpdate();
    }

    private void addDeviceInfoDataAndUpdate() {
        if (device == null) return;
        List<BluetoothGattService> services = BleManager.getInstance().getDeviceServices(device.getAddress());
        if (services == null) {
            return;
        }
        for (BluetoothGattService si : services) {
            groupList.add(si);
            childList.add(si.getCharacteristics());
        }
        adapter.notifyDataSetChanged();
        tvInfoCurrentUuid.setVisibility(View.VISIBLE);
    }

    private void initView() {
        TextView tvDeviceName = findViewById(R.id.tv_device_name);
        TextView tvAddress = findViewById(R.id.tv_device_address);
        TextView tvConnect = findViewById(R.id.tv_connect);
        TextView tvDisconnect = findViewById(R.id.tv_disconnect);
        TextView tvReadRssi = findViewById(R.id.tv_read_rssi);
        TextView tvRead = findViewById(R.id.tv_read);
        TextView tvWrite = findViewById(R.id.tv_write);
        llWrite = findViewById(R.id.ll_write);
        llRead = findViewById(R.id.ll_read);
        tvConnectionState = findViewById(R.id.tv_connection_state);
        tvReadResult = findViewById(R.id.tv_read_result);
        etWrite = findViewById(R.id.et_write);
        tvWriteResult = findViewById(R.id.tv_write_result);
        tvNotify = findViewById(R.id.tv_notify_or_indicate);
        tvInfoCurrentUuid = findViewById(R.id.tv_current_operate_info);
        tvInfoNotification = findViewById(R.id.tv_notify_info);
        elv = findViewById(R.id.elv);
        pb = findViewById(R.id.progress_bar);

        llWrite.setVisibility(View.GONE);
        llRead.setVisibility(View.GONE);
        tvNotify.setVisibility(View.GONE);
        tvInfoNotification.setVisibility(View.GONE);
        tvInfoCurrentUuid.setVisibility(View.GONE);

        tvConnect.setOnClickListener(this);
        tvDisconnect.setOnClickListener(this);
        tvReadRssi.setOnClickListener(this);
        tvRead.setOnClickListener(this);
        tvWrite.setOnClickListener(this);
        tvNotify.setOnClickListener(this);

        tvDeviceName.setText(getResources().getString(R.string.device_name_prefix) + device.getName());
        tvAddress.setText(getResources().getString(R.string.device_address_prefix) + device.getAddress());
        updateConnectionStateUi();
    }

    private void initElv() {
        if (groupList.size() != childList.size()) return;
        adapter = new DeviceServiceInfoAdapter(this, groupList, childList,
                R.layout.item_elv_device_info_group, R.layout.item_elv_device_info_child,
                new int[]{R.id.tv_service_uuid}, new int[]{R.id.tv_characteristic_uuid, R.id.tv_characteristic_attribution});
        int width = getWindowManager().getDefaultDisplay().getWidth();
        elv.setIndicatorBounds(width - 50, width);
        elv.setAdapter(adapter);
        elv.setOnChildClickListener(new ExpandableListView.OnChildClickListener() {
            @Override
            public boolean onChildClick(ExpandableListView expandableListView, View view, int groupPosition, int childPosition, long l) {
                if (adapter.getGroupData() != null && adapter.getChildData() != null) {
                    curService = adapter.getGroupData().get(groupPosition);
                    curCharacteristic = adapter.getChildData().get(groupPosition).get(childPosition);
                    updateOperationUi(curService, curCharacteristic);
                }
                return true;
            }
        });
    }

    private void updateOperationUi(BluetoothGattService service, BluetoothGattCharacteristic charInfo) {
        String extra = getResources().getString(R.string.current_operate_uuid) + "\n" + "service:\n      " +
                service.getUuid().toString() + "\n" + "characteristic:\n      " + charInfo.getUuid().toString();
        tvInfoCurrentUuid.setText(extra);
        tvWriteResult.setText(R.string.write_result);
        tvReadResult.setText(R.string.read_result);
        llRead.setVisibility(BluetoothGattUtils.isCharacteristicReadable(charInfo) ? View.VISIBLE : View.GONE);
        llWrite.setVisibility(BluetoothGattUtils.isCharacteristicWritable(charInfo) ? View.VISIBLE : View.GONE);
        tvNotify.setVisibility((BluetoothGattUtils.isCharacteristicNotifiable(charInfo) ||
                BluetoothGattUtils.isCharacteristicIndicative(charInfo)) ? View.VISIBLE : View.GONE);
    }

    private void updateConnectionStateUi() {
        String state;
        boolean isConnected = BleManager.getInstance().isConnected(device.getAddress());
        boolean isConnecting = BleManager.getInstance().isConnecting(device.getAddress());
        if (isConnected) {
            state = getResources().getString(R.string.connection_state_connected);
        } else if (isConnecting) {
            state = getResources().getString(R.string.connection_state_connecting);
        } else {
            state = getResources().getString(R.string.connection_state_disconnected);
        }
        pb.setVisibility(isConnecting ? View.VISIBLE : View.INVISIBLE);
        tvConnectionState.setText(state);
        tvConnectionState.setTextColor(getResources().getColor(isConnected ? R.color.bright_blue : R.color.bright_red));
    }

    private void updateNotificationInfo(String notification) {
        StringBuilder builder = new StringBuilder("Notify Uuid:");
        for (UUID s : notifySuccessUuids) {
            builder.append("\n");
            builder.append(s.toString());
        }
        if (!TextUtils.isEmpty(notification)) {
            builder.append("\nReceive Data:\n");
            builder.append(notification);
        }
        tvInfoNotification.setText(builder.toString());
    }

    private void reset() {
        groupList.clear();
        childList.clear();
        adapter.notifyDataSetChanged();

        llWrite.setVisibility(View.GONE);
        llRead.setVisibility(View.GONE);
        tvNotify.setVisibility(View.GONE);
        tvInfoNotification.setVisibility(View.GONE);
        tvInfoCurrentUuid.setVisibility(View.GONE);

        etWrite.setText("");
        tvInfoCurrentUuid.setText(R.string.tips_current_operate_uuid);
    }

    @SuppressLint("NonConstantResourceId")
    @Override
    public void onClick(View v) {
        if (v.getId() == R.id.tv_connect) {
            // This method will use the connection option that you set by BleManager#setScanOptions()
            BleManager.getInstance().connect(device.getAddress(), connectCallback);

            // Select a specified connection option
//            BleManager.getInstance().connect(device.getAddress(), BleManager.ConnectionOptions.newInstance(), connectCallback);
            return;
        } else if (v.getId() == R.id.tv_disconnect) {
            BleManager.getInstance().disconnect(device.getAddress());
            return;
        }
        if (!BleManager.getInstance().isConnected(device.getAddress())) {
            Toast.makeText(this, getResources().getString(R.string.tips_connection_disconnected),
                    Toast.LENGTH_SHORT).show();
            return;
        }
        switch (v.getId()) {
            case R.id.tv_read_rssi:
                BleManager.getInstance().readRssi(device, rssiCallback);
                break;
            case R.id.tv_read:
                BleManager.getInstance().read(device, curService.getUuid(), curCharacteristic.getUuid(), readCallback);
                break;
            case R.id.tv_write:
                String str = etWrite.getText().toString().trim();
                if (TextUtils.isEmpty(str)) {
                    Toast.makeText(this, getResources().getString(R.string.tips_write_operation),
                            Toast.LENGTH_SHORT).show();
                    return;
                }
                if (str.length() % 2 != 0) {
                    Toast.makeText(this, getResources().getString(R.string.tips_write_operation_length_err),
                            Toast.LENGTH_SHORT).show();
                    return;
                }
                BleManager.getInstance().write(device, curService.getUuid(), curCharacteristic.getUuid(),
                        ByteUtils.hexStr2Bytes(str), writeCallback);
                break;
            case R.id.tv_notify_or_indicate:
                BleManager.getInstance().notify(device, curService.getUuid(),
                        curCharacteristic.getUuid(), notifyCallback);
                break;
            default:
                break;
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (isFinishing() && device != null && !TextUtils.isEmpty(device.getAddress())) {
            BleManager.getInstance().disconnect(device.getAddress());
        }
    }

    private BleConnectCallback connectCallback = new BleConnectCallback() {

        @Override
        public void onConnectionStarted(BleDevice device) {
            OperateActivity.this.device = device;
            updateConnectionStateUi();
            Logger.d("onConnectionStarted");
        }

        @Override
        public void onConnected(BleDevice device) {
            reset();
            addDeviceInfoDataAndUpdate();
            updateConnectionStateUi();
            Logger.d("onConnected");
        }

        @Override
        public void onDisconnected(BleDevice device, int gattOperationStatus) {
            reset();
            updateConnectionStateUi();
            Logger.d("onDisconnected");
        }

        @Override
        public void onConnectionFailed(int errCode, BleDevice device) {
            String tips;
            switch (errCode) {
                case BleErrorCodes.BLUETOOTH_OFF:
                    tips = getString(R.string.tips_bluetooth_off);
                    break;
                case BleErrorCodes.CONNECTION_PERMISSION_NOT_GRANTED:
                    tips = getString(R.string.tips_connection_permissions_not_granted);
                    break;
                case BleErrorCodes.CONNECTION_REACH_MAX_NUM:
                    tips = getString(R.string.tips_connection_reach_max_num);
                    break;
                case BleErrorCodes.CONNECTION_TIMEOUT:
                    tips = getString(R.string.tips_connection_timeout);
                    break;
                case BleErrorCodes.CONNECTION_CANCELED:
                    tips = getString(R.string.tips_connection_canceled);
                    break;
                case BleErrorCodes.UNKNOWN:
                default:
                    tips = getString(R.string.tips_connection_fail);
                    break;
            }
            Logger.d("onConnectionFailed, errCode:" + errCode+"   msg="+tips);
            Toast.makeText(OperateActivity.this, tips, Toast.LENGTH_LONG).show();
            reset();
            updateConnectionStateUi();
        }
    };

    private BleRssiCallback rssiCallback = new BleRssiCallback() {
        @Override
        public void onRssiSuccess(int rssi, BleDevice bleDevice) {
            Logger.d("onRssiSuccess  rssi="+rssi);
            Toast.makeText(OperateActivity.this, rssi + "dBm", Toast.LENGTH_SHORT).show();
        }

        @Override
        public void onRssiFailed(int errCode, BleDevice bleDevice) {
            switch (errCode) {
                case BleErrorCodes.CONNECTION_NOT_ESTABLISHED:
                    break;
                case BleErrorCodes.UNKNOWN:
                    break;
            }
            Logger.d("onRssiFailed, errCode:" + errCode);
        }
    };

    private BleNotifyCallback notifyCallback = new BleNotifyCallback() {
        @Override
        public void onCharacteristicChanged(byte[] receivedData, UUID characteristicUuid, BleDevice device) {
            String s = ByteUtils.bytes2HexStr(receivedData);
            Logger.d("onCharacteristicChanged  data=" + s + "   Thread=" + Thread.currentThread().getName());
            runOnUiThread(() -> updateNotificationInfo(s));
        }

        @Override
        public void onNotifySuccess(UUID characteristicUuid, BleDevice device) {
            Logger.d("onNotifySuccess  uuid=" + characteristicUuid);
            tvInfoNotification.setVisibility(View.VISIBLE);
            if (!notifySuccessUuids.contains(characteristicUuid)) {
                notifySuccessUuids.add(characteristicUuid);
            }
            updateNotificationInfo("");
        }

        @Override
        public void onNotifyFailed(int errorCode, UUID characteristicUuid, BleDevice device) {
            switch (errorCode) {
                case BleErrorCodes.CONNECTION_NOT_ESTABLISHED:
                    // Connection is not established yet, or disconnected from the remote device
                    break;
                case BleErrorCodes.SERVICE_NOT_FOUND:
                    // Service not found in the remote device
                    break;
                case BleErrorCodes.CHARACTERISTIC_NOT_FOUND_IN_SERVICE:
                    // Characteristic not found in specified service
                    break;
                case BleErrorCodes.NOTIFICATION_OR_INDICATION_UNSUPPORTED:
                    // Characteristic not support notification or indication
                    break;
                case BleErrorCodes.UNKNOWN:
                    // Other reason
                    break;
            }
            Logger.d("onNotifyFailed, errCode=" + errorCode);
        }
    };

    private BleWriteCallback writeCallback = new BleWriteCallback() {
        @Override
        public void onWriteSuccess(byte[] data, UUID characteristicUuid, BleDevice device) {
            Logger.d("write success:" + ByteUtils.bytes2HexStr(data));
            tvWriteResult.setText(ByteUtils.bytes2HexStr(data));
        }

        @Override
        public void onWriteFailed(int errCode, byte[] data, UUID characteristicUuid, BleDevice device) {
            switch (errCode) {
                case BleErrorCodes.CONNECTION_NOT_ESTABLISHED:
                    // Connection is not established yet, or disconnected from the remote device
                    break;
                case BleErrorCodes.SERVICE_NOT_FOUND:
                    // Service not found in the remote device
                    break;
                case BleErrorCodes.CHARACTERISTIC_NOT_FOUND_IN_SERVICE:
                    // Characteristic not found in specified service
                    break;
                case BleErrorCodes.WRITE_UNSUPPORTED:
                    // Characteristic not support writing
                    break;
                case BleErrorCodes.DATA_LENGTH_GREATER_THAN_MTU:
                    // Data length is greater than MTU
                    break;
                case BleErrorCodes.UNKNOWN:
                    // Other reason
                    break;
            }
            Logger.d("write failed, errCode=" + errCode);
            tvWriteResult.setText("write failed, errorCode=" + errCode);
        }
    };

    private BleReadCallback readCallback = new BleReadCallback() {
        @Override
        public void onReadSuccess(byte[] readData, UUID characteristicUuid, BleDevice device) {
            Logger.d("read success:" + ByteUtils.bytes2HexStr(readData));
            tvReadResult.setText(ByteUtils.bytes2HexStr(readData));
        }

        @Override
        public void onReadFailed(int errorCode, UUID characteristicUuid, BleDevice device) {
            switch (errorCode) {
                case BleErrorCodes.CONNECTION_NOT_ESTABLISHED:
                    // Connection is not established yet, or disconnected from the remote device
                    break;
                case BleErrorCodes.SERVICE_NOT_FOUND:
                    // Service not found in the remote device
                    break;
                case BleErrorCodes.CHARACTERISTIC_NOT_FOUND_IN_SERVICE:
                    // Characteristic not found in specified service
                    break;
                case BleErrorCodes.READ_UNSUPPORTED:
                    // Characteristic not support reading
                    break;
                case BleErrorCodes.UNKNOWN:
                    // Other reason
                    break;
            }
            tvReadResult.setText("read failed, errCode=" + errorCode);
        }
    };
}
