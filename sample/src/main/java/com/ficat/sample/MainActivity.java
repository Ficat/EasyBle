package com.ficat.sample;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.graphics.Rect;
import android.location.LocationManager;
import android.os.Build;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.util.SparseArray;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import com.ficat.easyble.BleDevice;
import com.ficat.easyble.BleManager;
import com.ficat.easyble.scan.BleScanCallback;
import com.ficat.easypermissions.EasyPermissions;
import com.ficat.easypermissions.RequestExecutor;
import com.ficat.easypermissions.bean.Permission;
import com.ficat.sample.adapter.ScanDeviceAdapter;
import com.ficat.sample.adapter.CommonRecyclerViewAdapter;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {
    private final static String TAG = "EasyBle";

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
        rv = findViewById(R.id.rv);

        btnScan.setOnClickListener(this);
    }

    private void initBleManager() {
        //check if this android device supports ble
        if (!BleManager.supportBle(this)) {
            return;
        }
        //open bluetooth without a request dialog
        BleManager.toggleBluetooth(true);

        BleManager.ScanOptions scanOptions = BleManager.ScanOptions
                .newInstance()
                .scanPeriod(8000)
                .scanDeviceName(null);

        BleManager.ConnectOptions connectOptions = BleManager.ConnectOptions
                .newInstance()
                .connectTimeout(12000);

        manager = BleManager
                .getInstance()
                .setScanOptions(scanOptions)
                .setConnectionOptions(connectOptions)
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
            case R.id.btn_scan:
                if (!BleManager.isBluetoothOn()) {
                    BleManager.toggleBluetooth(true);
                }
                //for most devices whose version is over Android6,scanning may need GPS permission
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && !isGpsOn()) {
                    Toast.makeText(this, getResources().getString(R.string.tips_turn_on_gps), Toast.LENGTH_LONG).show();
                    return;
                }
                EasyPermissions
                        .with(this)
                        .request(Manifest.permission.ACCESS_FINE_LOCATION)
                        .autoRetryWhenUserRefuse(true, null)
                        .result(new RequestExecutor.ResultReceiver() {
                            @Override
                            public void onPermissionsRequestResult(boolean grantAll, List<Permission> results) {
                                if (grantAll) {
                                    if (!manager.isScanning()) {
                                        startScan();
                                    }
                                } else {
                                    Toast.makeText(MainActivity.this,
                                            getResources().getString(R.string.tips_go_setting_to_grant_location),
                                            Toast.LENGTH_LONG).show();
                                    EasyPermissions.goToSettingsActivity(MainActivity.this);
                                }
                            }
                        });
                break;
            default:
                break;
        }
    }

    private void startScan() {
        manager.startScan(new BleScanCallback() {
            @Override
            public void onLeScan(BleDevice device, int rssi, byte[] scanRecord) {
                for (BleDevice d : deviceList) {
                    if (device.address.equals(d.address)) {
                        return;
                    }
                }
                deviceList.add(device);
                adapter.notifyDataSetChanged();
            }

            @Override
            public void onStart(boolean startScanSuccess, String info) {
                Log.e(TAG, "start scan = " + startScanSuccess + "   info: " + info);
                if (startScanSuccess) {
                    deviceList.clear();
                    adapter.notifyDataSetChanged();
                }
            }

            @Override
            public void onFinish() {
                Log.e(TAG, "scan finish");
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
