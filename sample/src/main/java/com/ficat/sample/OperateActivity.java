package com.ficat.sample;

import android.os.Bundle;
import android.os.Parcelable;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.ExpandableListView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.ficat.easyble.BleDevice;
import com.ficat.easyble.BleManager;
import com.ficat.easyble.Logger;
import com.ficat.easyble.gatt.BleHandlerThread;
import com.ficat.easyble.gatt.callback.BleCallback;
import com.ficat.easyble.gatt.callback.BleConnectCallback;
import com.ficat.easyble.gatt.callback.BleNotifyCallback;
import com.ficat.easyble.gatt.callback.BleReadCallback;
import com.ficat.easyble.gatt.callback.BleRssiCallback;
import com.ficat.easyble.gatt.callback.BleWriteCallback;
import com.ficat.easyble.gatt.data.CharacteristicInfo;
import com.ficat.easyble.gatt.data.ServiceInfo;
import com.ficat.sample.adapter.DeviceServiceInfoAdapter;
import com.ficat.sample.data.ExtraInfo;
import com.ficat.sample.utils.ByteUtils;

import java.util.ArrayList;
import java.util.List;

public class OperateActivity extends AppCompatActivity implements View.OnClickListener {
    public static final String KEY_DEVICE_INFO = "keyDeviceInfo";

    private BleDevice device;
    private LinearLayout llWrite, llRead;
    private EditText etWrite;
    private ProgressBar pb;
    private TextView tvConnectionState, tvReadResult, tvWriteResult,
            tvNotify, tvInfoCurrentUuid, tvInfoNotification;
    private ExpandableListView elv;
    private List<ServiceInfo> groupList = new ArrayList<>();
    private List<List<CharacteristicInfo>> childList = new ArrayList<>();
    private List<String> notifySuccessUuids = new ArrayList<>();
    private DeviceServiceInfoAdapter adapter;
    private ServiceInfo curService;
    private CharacteristicInfo curCharacteristic;

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
        List<ServiceInfo> services = BleManager.getInstance().getDeviceServices(device.getAddress());
        if (services == null) {
            return;
        }
        for (ServiceInfo si : services) {
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
        updateConnectionStateUi(BleManager.getInstance().isConnected(device.getAddress()));
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

    private void updateOperationUi(ServiceInfo service, CharacteristicInfo charInfo) {
        String extra = getResources().getString(R.string.current_operate_uuid) + "\n" + "service:\n      " +
                service.getUuid().toString() + "\n" + "characteristic:\n      " + charInfo.getUuid().toString();
        tvInfoCurrentUuid.setText(extra);
        tvWriteResult.setText(R.string.write_result);
        tvReadResult.setText(R.string.read_result);
        llRead.setVisibility(charInfo.isReadable() ? View.VISIBLE : View.GONE);
        llWrite.setVisibility(charInfo.isWritable() ? View.VISIBLE : View.GONE);
        tvNotify.setVisibility((charInfo.isNotifiable() || charInfo.isIndicative()) ? View.VISIBLE : View.GONE);
    }

    private void updateConnectionStateUi(boolean connected) {
        String state;
        if (device.isConnected()) {
            state = getResources().getString(R.string.connection_state_connected);
        } else if (device.isConnecting()) {
            state = getResources().getString(R.string.connection_state_connecting);
        } else {
            state = getResources().getString(R.string.connection_state_disconnected);
        }
        pb.setVisibility(device.isConnecting() ? View.VISIBLE : View.INVISIBLE);
        tvConnectionState.setText(state);
        tvConnectionState.setTextColor(getResources().getColor(device.isConnected() ? R.color.bright_blue : R.color.bright_red));
    }

    private void updateNotificationInfo(String notification) {
        StringBuilder builder = new StringBuilder("Notify Uuid:");
        for (String s : notifySuccessUuids) {
            builder.append("\n");
            builder.append(s);
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

    @Override
    public void onClick(View v) {
        if (v.getId() == R.id.tv_connect) {
            // This method will use the connection option that you set by BleManager#setScanOptions(),
            // and it's all callbacks will run in UI-Thread
            BleManager.getInstance().connect(device.getAddress(), connectCallback);

            // Select a specified connection option
//            BleManager.getInstance().connect(device.getAddress(), BleManager.ConnectionOptions.newInstance(), connectCallback);

            // Select a thread to run all operation callbacks, like connect/notify/read/write and so on
//            BleManager.getInstance().connect(device.getAddress(), connectCallback, new BleHandlerThread("BleThread"));
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
                BleManager.getInstance().read(device, curService.getUuid().toString(),
                        curCharacteristic.getUuid().toString(), readCallback);
                break;
            case R.id.tv_write:
                String str = etWrite.getText().toString();
                if (TextUtils.isEmpty(str)) {
                    Toast.makeText(this, getResources().getString(R.string.tips_write_operation),
                            Toast.LENGTH_SHORT).show();
                    return;
                }
                BleManager.getInstance().write(device, curService.getUuid().toString(),
                        curCharacteristic.getUuid().toString(), ByteUtils.hexStr2Bytes(str), writeCallback);
                break;
            case R.id.tv_notify_or_indicate:
                BleManager.getInstance().notify(device, curService.getUuid().toString(),
                        curCharacteristic.getUuid().toString(), notifyCallback);
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
        public void onStart(boolean startConnectSuccess, String info, BleDevice device) {
            Logger.e("start connecting:" + startConnectSuccess + "    info=" + info + "   Thread=" +
                    Thread.currentThread().getName());
            runOnUiThread(() -> {
                OperateActivity.this.device = device;
                updateConnectionStateUi(false);
                if (!startConnectSuccess) {
                    Toast.makeText(OperateActivity.this, "start connecting fail:" + info, Toast.LENGTH_LONG).show();
                }
            });

        }

        @Override
        public void onConnected(BleDevice device) {
            Logger.e("connected. " + "   Thread=" + Thread.currentThread().getName());
            runOnUiThread((Runnable) () -> {
                addDeviceInfoDataAndUpdate();
                updateConnectionStateUi(true);
            });

        }

        @Override
        public void onDisconnected(String info, int status, BleDevice device) {
            Logger.e("disconnected!" + "   Thread=" + Thread.currentThread().getName());
            runOnUiThread(() -> {
                reset();
                updateConnectionStateUi(false);
            });

        }

        @Override
        public void onFailure(int failureCode, String info, BleDevice device) {
            Logger.e("connect fail:" + info + "   Thread=" + Thread.currentThread().getName());
            runOnUiThread(() -> {
                String tips;
                switch (failureCode) {
                    case BleCallback.FAILURE_CONNECTION_TIMEOUT:
                        tips = getString(R.string.tips_connection_timeout);
                        break;
                    case BleCallback.FAILURE_CONNECTION_CANCELED:
                        tips = getString(R.string.tips_connection_canceled);
                        break;
                    case BleCallback.FAILURE_CONNECTION_FAILED:
                    default:
                        tips = getString(R.string.tips_connection_fail);
                        break;
                }
                Toast.makeText(OperateActivity.this, tips, Toast.LENGTH_LONG).show();
                reset();
                updateConnectionStateUi(false);
            });
        }
    };

    private BleRssiCallback rssiCallback = new BleRssiCallback() {
        @Override
        public void onRssi(int rssi, BleDevice bleDevice) {
            Logger.e("read rssi success:" + rssi + "   Thread=" + Thread.currentThread().getName());
            runOnUiThread(() -> Toast.makeText(OperateActivity.this, rssi + "dBm", Toast.LENGTH_SHORT).show());

        }

        @Override
        public void onFailure(int failureCode, String info, BleDevice device) {
            Logger.e("read rssi fail:" + info + "   Thread=" + Thread.currentThread().getName());
        }
    };

    private BleNotifyCallback notifyCallback = new BleNotifyCallback() {
        @Override
        public void onCharacteristicChanged(byte[] data, BleDevice device) {
            String s = ByteUtils.bytes2HexStr(data);
            Logger.e("onCharacteristicChanged:" + s + "   Thread=" + Thread.currentThread().getName());
            runOnUiThread(() -> updateNotificationInfo(s));
        }

        @Override
        public void onNotifySuccess(String notifySuccessUuid, BleDevice device) {
            Logger.e("notify success uuid:" + notifySuccessUuid + "   Thread=" + Thread.currentThread().getName());
            runOnUiThread(() -> {
                tvInfoNotification.setVisibility(View.VISIBLE);
                if (!notifySuccessUuids.contains(notifySuccessUuid)) {
                    notifySuccessUuids.add(notifySuccessUuid);
                }
                updateNotificationInfo("");
            });

        }

        @Override
        public void onFailure(int failureCode, String info, BleDevice device) {
            switch (failureCode) {
                case BleCallback.FAILURE_CONNECTION_NOT_ESTABLISHED:
                    // Connection is not established yet, or disconnected from the remote device
                    break;
                case BleCallback.FAILURE_SERVICE_NOT_FOUND:
                    // Service not found in the remote device
                    break;
                case BleCallback.FAILURE_CHARACTERISTIC_NOT_FOUND_IN_SERVICE:
                    // Characteristic not found in specified service
                    break;
                case BleCallback.FAILURE_NOTIFICATION_OR_INDICATION_UNSUPPORTED:
                    // Characteristic not support notification or indication
                    break;
                case BleCallback.FAILURE_OTHER:
                    // Other reason
                    break;
            }
            Logger.e("notify fail:" + info + "   Thread=" + Thread.currentThread().getName());
            runOnUiThread(() -> Toast.makeText(OperateActivity.this, "notify fail:" + info, Toast.LENGTH_LONG).show());
        }
    };

    private BleWriteCallback writeCallback = new BleWriteCallback() {
        @Override
        public void onWriteSuccess(byte[] data, BleDevice device) {
            Logger.e("write success:" + ByteUtils.bytes2HexStr(data) + "   Thread=" + Thread.currentThread().getName());
            runOnUiThread(() -> tvWriteResult.setText(ByteUtils.bytes2HexStr(data)));
        }

        @Override
        public void onFailure(int failureCode, String info, BleDevice device) {
            switch (failureCode) {
                case BleCallback.FAILURE_CONNECTION_NOT_ESTABLISHED:
                    // Connection is not established yet, or disconnected from the remote device
                    break;
                case BleCallback.FAILURE_SERVICE_NOT_FOUND:
                    // Service not found in the remote device
                    break;
                case BleCallback.FAILURE_CHARACTERISTIC_NOT_FOUND_IN_SERVICE:
                    // Characteristic not found in specified service
                    break;
                case BleCallback.FAILURE_WRITE_UNSUPPORTED:
                    // Characteristic not support writing
                    break;
                case BleCallback.FAILURE_OTHER:
                    // Other reason
                    break;
            }
            Logger.e("write fail:" + info + "   Thread=" + Thread.currentThread().getName());
            runOnUiThread(() -> tvWriteResult.setText("write fail:" + info));
        }
    };

    private BleReadCallback readCallback = new BleReadCallback() {
        @Override
        public void onReadSuccess(byte[] data, BleDevice device) {
            Logger.e("read success:" + ByteUtils.bytes2HexStr(data) + "   Thread=" + Thread.currentThread().getName());
            runOnUiThread(() -> tvReadResult.setText(ByteUtils.bytes2HexStr(data)));
        }

        @Override
        public void onFailure(int failureCode, String info, BleDevice device) {
            switch (failureCode) {
                case BleCallback.FAILURE_CONNECTION_NOT_ESTABLISHED:
                    // Connection is not established yet, or disconnected from the remote device
                    break;
                case BleCallback.FAILURE_SERVICE_NOT_FOUND:
                    // Service not found in the remote device
                    break;
                case BleCallback.FAILURE_CHARACTERISTIC_NOT_FOUND_IN_SERVICE:
                    // Characteristic not found in specified service
                    break;
                case BleCallback.FAILURE_READ_UNSUPPORTED:
                    // Characteristic not support reading
                    break;
                case BleCallback.FAILURE_OTHER:
                    // Other reason
                    break;
            }
            Logger.e("read fail:" + info + "   Thread=" + Thread.currentThread().getName());
            runOnUiThread(() -> tvReadResult.setText("read fail:" + info));
        }
    };
}
