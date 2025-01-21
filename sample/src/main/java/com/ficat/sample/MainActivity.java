package com.ficat.sample;

import android.content.Context;
import android.content.Intent;
import android.graphics.Rect;
import android.location.LocationManager;
import android.os.Build;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.SparseArray;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import com.ficat.easyble.BleDevice;
import com.ficat.easyble.BleManager;
import com.ficat.easyble.Logger;
import com.ficat.easyble.scan.BleScanCallback;
import com.ficat.easypermissions.EasyPermissions;
import com.ficat.sample.adapter.ScanDeviceAdapter;
import com.ficat.sample.adapter.CommonRecyclerViewAdapter;
import com.ficat.sample.data.ExtraInfo;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {
    private final static int REQUEST_CODE_ENABLE_BLUETOOTH = 23;

    private RecyclerView rv;
    private BleManager manager;
    private List<BleDevice> deviceList = new ArrayList<>();
    private ScanDeviceAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        initView();
        initBleManager();
        showDevicesByRv();
    }

    private void initView() {
        Button btnScan = findViewById(R.id.btn_scan);
        Button btnEnableBT = findViewById(R.id.btn_enable_bluetooth);
        Button btnPermission = findViewById(R.id.btn_request_permission);
        rv = findViewById(R.id.rv);

        btnScan.setOnClickListener(this);
        btnEnableBT.setOnClickListener(this);
        btnPermission.setOnClickListener(this);
    }

    private void initBleManager() {
        //check if this android device supports ble
        if (!BleManager.supportBle(this)) {
            Toast.makeText(this, getString(R.string.tips_not_support_ble), Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        BleManager.ScanOptions scanOptions = BleManager.ScanOptions
                .newInstance()
                .scanPeriod(8000)
                .scanDeviceName(null);

        BleManager.ConnectionOptions connectionOptions = BleManager.ConnectionOptions
                .newInstance()
                .connectionPeriod(12000);

        manager = BleManager
                .getInstance()
                .setScanOptions(scanOptions)
                .setConnectionOptions(connectionOptions)
                .setLog(true, "EasyBle")
                .init(this.getApplication());
    }

    private void showDevicesByRv() {
        rv.setLayoutManager(new LinearLayoutManager(this));
        rv.addItemDecoration(new RecyclerView.ItemDecoration() {
            @Override
            public void getItemOffsets(Rect outRect, View view, RecyclerView parent, RecyclerView.State state) {
                super.getItemOffsets(outRect, view, parent, state);
                outRect.top = 3;
            }
        });
        SparseArray<int[]> res = new SparseArray<>();
        res.put(R.layout.item_rv_scan_devices, new int[]{R.id.tv_name, R.id.tv_address, R.id.tv_connection_state});
        adapter = new ScanDeviceAdapter(this, deviceList, res);
        adapter.setOnItemClickListener(new CommonRecyclerViewAdapter.OnItemClickListener() {
            @Override
            public void onItemClick(View itemView, int position) {
                manager.stopScan();
                BleDevice device = deviceList.get(position);
                Intent intent = new Intent(MainActivity.this, OperateActivity.class);
                intent.putExtra(OperateActivity.KEY_DEVICE_INFO, device);
                startActivity(intent);
            }
        });
        rv.setAdapter(adapter);
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.btn_request_permission:
                List<String> list = BleManager.getBleRequiredPermissions();
                // Lower version devices may not require any permissions, so check it
                if (list.size() <= 0){
                    return;
                }
                EasyPermissions
                        .with(this)
                        .request(list.toArray(new String[0]))
                        .result((grantAll, results) -> {
                            if (!grantAll) {
                                Toast.makeText(MainActivity.this,
                                        getString(R.string.tips_user_reject_permissions),
                                        Toast.LENGTH_SHORT).show();
                            }
                        });
                break;
            case R.id.btn_enable_bluetooth:
                if (!BleManager.allBlePermissionsGranted(this)) {
                    Toast.makeText(this, getString(R.string.tips_request_ble_permissions), Toast.LENGTH_SHORT).show();
                    return;
                }
                if (BleManager.isBluetoothOn()) {
                    Toast.makeText(this, getString(R.string.tips_bluetooth_on), Toast.LENGTH_SHORT).show();
                    return;
                }
                BleManager.enableBluetooth(this, REQUEST_CODE_ENABLE_BLUETOOTH);
                break;
            case R.id.btn_scan:
                if (!BleManager.allBlePermissionsGranted(this)) {
                    Toast.makeText(this, getString(R.string.tips_request_ble_permissions), Toast.LENGTH_SHORT).show();
                    return;
                }
                if (!BleManager.isBluetoothOn()) {
                    Toast.makeText(this, getString(R.string.tips_enable_bluetooth), Toast.LENGTH_SHORT).show();
                    return;
                }
                //Android7 or higher, scanning may need GPS permission
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && !isGpsOn()) {
                    Toast.makeText(this, getResources().getString(R.string.tips_turn_on_gps), Toast.LENGTH_LONG).show();
                }
                if (!manager.isScanning()) {
                    startScan();
                }
                break;
            default:
                break;
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CODE_ENABLE_BLUETOOTH) {
            Toast.makeText(this, getString(resultCode == RESULT_OK ?
                    R.string.tips_bluetooth_on : R.string.tips_bluetooth_off), Toast.LENGTH_SHORT).show();
        }
    }

    private void startScan() {
        manager.startScan(new BleScanCallback() {
            @Override
            public void onLeScan(BleDevice device, int rssi, byte[] scanRecord) {
                for (BleDevice d : deviceList) {
                    if (device.getAddress().equals(d.getAddress())) {
                        return;
                    }
                }

                // If you want to save any extra info to BleDevice, you can do like the following.
                // 1. First, create a class used to save extra info, whatever any name, but make
                //    sure it implements the interface 'Parcelable'
                // 2. Then, create an instance and put info you wanna save into it
                // 3. Finally, call BleDevice#setParcelableExtra()
                ExtraInfo extra = new ExtraInfo();
                extra.setNote("test extra info: " + device.getName());
                extra.setRssi(rssi);
                extra.setScanRecordBytes(scanRecord);
                device.setParcelableExtra(extra);

                deviceList.add(device);
                adapter.notifyDataSetChanged();
            }

            @Override
            public void onStart(boolean startScanSuccess, String info) {
                Logger.e("start scan = " + startScanSuccess + "   info: " + info);
                if (startScanSuccess) {
                    deviceList.clear();
                    adapter.notifyDataSetChanged();
                }
            }

            @Override
            public void onFinish() {
                Logger.e("scan finish");
            }
        });
    }

    private boolean isGpsOn() {
        LocationManager locationManager
                = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (manager != null) {
            //you must call BleManager#destroy() to release resources
            manager.destroy();
        }
    }
}
