package com.ficat.sample;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Rect;
import android.location.LocationManager;
import android.os.Build;
import android.os.ParcelUuid;
import android.os.Parcelable;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.ForegroundColorSpan;
import android.util.SparseArray;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.ficat.easyble.BleDevice;
import com.ficat.easyble.BleManager;
import com.ficat.easyble.scan.BleScanRecord;
import com.ficat.easyble.utils.Logger;
import com.ficat.easyble.scan.BleScanCallback;
import com.ficat.easypermissions.EasyPermissions;
import com.ficat.sample.adapter.ScanDeviceAdapter;
import com.ficat.sample.data.ExtraInfo;
import com.ficat.sample.utils.ByteUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public class MainActivity extends AppCompatActivity implements View.OnClickListener, View.OnFocusChangeListener {
    private final static int REQUEST_CODE_ENABLE_BLUETOOTH = 23;

    private RecyclerView rv;
    private EditText etName, etAddress, etServiceUuid;
    private CheckBox cbFuzzy;
    private TextView tvInvalidAddress, tvInvalidUuid;
    private BleManager manager;
    private List<BleDevice> deviceList = new ArrayList<>();
    private ScanDeviceAdapter adapter;
    private boolean isScanFilterEditing = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        initView();
        initBleManager();
        showDevicesByRv();
    }

    private void initView() {
        rv = findViewById(R.id.rv);
        cbFuzzy = findViewById(R.id.cb_fuzzy);
        etName = findViewById(R.id.et_name);
        etAddress = findViewById(R.id.et_address);
        etServiceUuid = findViewById(R.id.et_uuid);
        tvInvalidAddress = findViewById(R.id.tv_tips_invalid_address);
        tvInvalidUuid = findViewById(R.id.tv_tips_invalid_service_uuid);
        TextView tvConfirmFilter = findViewById(R.id.tv_confirm_filter);
        TextView tvModifyFilter = findViewById(R.id.tv_modify_filter);
        TextView tvScan = findViewById(R.id.tv_scan);

        setScanFilterEditMode(false);

        tvScan.setOnClickListener(this);
        tvConfirmFilter.setOnClickListener(this);
        tvModifyFilter.setOnClickListener(this);
        etAddress.setOnFocusChangeListener(this);
        etServiceUuid.setOnFocusChangeListener(this);
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
                .scanPeriod(8000) // scan period (scan timeout)
                .scanDeviceName(null);

        BleManager.ConnectionOptions connectionOptions = BleManager.ConnectionOptions
                .newInstance()
                .connectionPeriod(12000); // connection period (connection timeout)

        manager = BleManager
                .getInstance()
                .setScanOptions(scanOptions)
                .setConnectionOptions(connectionOptions)
                .setLog(true, "EasyBle")
                .init(getApplication());
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
        res.put(R.layout.item_rv_scan_devices, new int[]{R.id.tv_name, R.id.tv_address,
                R.id.tv_rssi, R.id.tv_connection_state});
        adapter = new ScanDeviceAdapter(this, deviceList, res);
        adapter.setOnItemClickListener((itemView, position) -> {
            BleDevice device = deviceList.get(position);
            new AlertDialog.Builder(MainActivity.this)
                    .setTitle(getString(R.string.advertise_data))
                    .setMessage(getAdvertisementInfo(device))
                    .setPositiveButton(getString(R.string.confirm), (dialog, which) -> dialog.dismiss())
                    .show();
        });
        adapter.setOnClickToOperationPageButtonListener(position -> {
            manager.stopScan();
            BleDevice device = deviceList.get(position);
            Intent intent = new Intent(MainActivity.this, OperateActivity.class);
            intent.putExtra(OperateActivity.KEY_DEVICE_INFO, device);
            startActivity(intent);
        });
        rv.setAdapter(adapter);
    }

    @Override
    public void onClick(View v) {
        if (v.getId() == R.id.tv_scan) {
            if (isScanFilterEditing) {
                Toast.makeText(this, getString(R.string.tips_save_scan_filter), Toast.LENGTH_SHORT).show();
                return;
            }
            if (BleManager.allBlePermissionsGranted(this)) {
                startScan();
                return;
            }
            List<String> list = BleManager.getBleRequiredPermissions();
            // Lower version devices may not require any permissions, so check it
            if (list.isEmpty()) {
                startScan();
                return;
            }
            EasyPermissions
                    .with(this)
                    .request(list.toArray(new String[0]))
                    .result((grantAll, results) -> {
                        if (grantAll) {

                            startScan();
                        } else {
                            Toast.makeText(MainActivity.this,
                                    getString(R.string.tips_user_reject_permissions), Toast.LENGTH_SHORT).show();
                        }
                    });
        } else if (v.getId() == R.id.tv_modify_filter) {
            setScanFilterEditMode(true);
        } else if (v.getId() == R.id.tv_confirm_filter) {
            // Disable scan filter editor
            setScanFilterEditMode(false);

            // Save scan options
            String strAddress = etAddress.getText().toString().trim();
            String strUuid = etServiceUuid.getText().toString().trim();
            String strName = etName.getText().toString().trim();
            UUID uuid = null;
            try {
                uuid = UUID.fromString(strUuid);
            } catch (Exception e) {
                if (!TextUtils.isEmpty(strUuid)) {
                    Logger.e("Invalid service uuid=" + strUuid);
                }
            }
            boolean filterName = !TextUtils.isEmpty(strName);
            boolean filterAddress = BleManager.isValidAddress(strAddress);
            boolean filterUuid = uuid != null;
            if (filterName || filterAddress || filterUuid) {
                BleManager.ScanOptions opts = BleManager.getInstance().getScanOptions();
                if (filterName) {
                    opts.scanDeviceName(strName, cbFuzzy.isChecked());
                }
                if (filterAddress) {
                    opts.scanDeviceAddress(strAddress);
                }
                if (filterUuid) {
                    opts.scanServiceUuids(new UUID[]{uuid});
                }
                BleManager.getInstance().setScanOptions(opts);
            }
        }

    }

    @Override
    public void onFocusChange(View v, boolean hasFocus) {
        if (v.getId() == R.id.et_address) {
            if (hasFocus) {
                tvInvalidAddress.setVisibility(View.INVISIBLE);
                return;
            }
            String strAddress = etAddress.getText().toString().trim();
            if (TextUtils.isEmpty(strAddress)) {
                tvInvalidAddress.setVisibility(View.INVISIBLE);
                return;
            }
            tvInvalidAddress.setVisibility(BleManager.isValidAddress(strAddress) ?
                    View.INVISIBLE : View.VISIBLE);

        } else if (v.getId() == R.id.et_uuid) {
            if (hasFocus) {
                tvInvalidUuid.setVisibility(View.INVISIBLE);
                return;
            }
            String strUuid = etServiceUuid.getText().toString().trim();
            if (TextUtils.isEmpty(strUuid)) {
                tvInvalidUuid.setVisibility(View.INVISIBLE);
                return;
            }
            UUID uuid = null;
            try {
                uuid = UUID.fromString(strUuid);
            } catch (Exception e) {
                // nothing to do
            }
            tvInvalidUuid.setVisibility(uuid == null ? View.VISIBLE : View.INVISIBLE);
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
        if (!BleManager.isBluetoothEnabled()) {
            Toast.makeText(MainActivity.this, getString(R.string.tips_enable_bluetooth),
                    Toast.LENGTH_SHORT).show();
            BleManager.enableBluetooth(this, REQUEST_CODE_ENABLE_BLUETOOTH);
            return;
        }
        //Android7 or higher, scanning may need GPS permission
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && !isGpsOn()) {
            Toast.makeText(this, getResources().getString(R.string.tips_turn_on_gps), Toast.LENGTH_LONG).show();
        }
        if (manager.isScanning()) {
            return;
        }
        manager.startScan(new BleScanCallback() {
            @Override
            public void onScanning(BleDevice device, int rssi, byte[] scanRecord) {
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
                extra.setBleScanRecord(BleManager.parseScanRecord(scanRecord));
                device.setParcelableExtra(extra);

                int pos = deviceList.size();
                deviceList.add(device);
                adapter.notifyItemInserted(pos);
            }

            @Override
            public void onScanStarted() {
                Logger.d("onScanStarted");
                deviceList.clear();
                adapter.notifyDataSetChanged();
            }

            @Override
            public void onScanFinished() {
                Logger.d("onScanFinished");
            }

            @Override
            public void onScanFailed(int code) {
                Logger.d("onScanFailed   code=" + code);
            }
        });
    }

    @SuppressLint("MissingPermission")
    private SpannableStringBuilder getAdvertisementInfo(BleDevice device) {
        Parcelable extra = device.getParcelableExtra();
        SpannableStringBuilder builder = new SpannableStringBuilder();
        // device type
        BluetoothDevice d = device.getBluetoothDevice();
        if (d != null) {
            String typeStr;
            switch (d.getType()) {
                case BluetoothDevice.DEVICE_TYPE_CLASSIC:
                    typeStr = getString(R.string.advertise_content_device_type_classic);
                    break;
                case BluetoothDevice.DEVICE_TYPE_LE:
                    typeStr = getString(R.string.advertise_content_device_type_le);
                    break;
                case BluetoothDevice.DEVICE_TYPE_DUAL:
                    typeStr = getString(R.string.advertise_content_device_type_dual);
                    break;
                case BluetoothDevice.DEVICE_TYPE_UNKNOWN:
                default:
                    typeStr = getString(R.string.advertise_content_device_type_unknown);
                    break;
            }
            appendAdvertisementItem(builder, getString(R.string.advertise_title_device_type), typeStr + "\n");
        }
        // other advertisement info
        if (extra instanceof ExtraInfo) {
            ExtraInfo info = (ExtraInfo) extra;
            BleScanRecord scanRecord = info.getBleScanRecord();
            // flags
            int flags = scanRecord.getAdvertiseFlags();
            List<String> flagStrings = new ArrayList<>();
            if (BleScanRecord.isLeLimitedDiscoverable(flags)) {
                flagStrings.add(getString(R.string.advertise_content_flag_le_limited_discoverable));
            }
            if (BleScanRecord.isLeGeneralDiscoverable(flags)) {
                flagStrings.add(getString(R.string.advertise_content_flag_le_general_discoverable));
            }
            if (BleScanRecord.isBrEdrNotSupported(flags)) {
                flagStrings.add(getString(R.string.advertise_content_flag_br_edr_not_supported));
            }
            if (BleScanRecord.isSimultaneousLeAndBrEdrToSameDeviceCapable(flags)) {
                flagStrings.add(getString(R.string.advertise_content_flag_simultaneous_le_and_bredr));
            }
            if (!flagStrings.isEmpty()) {
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < flagStrings.size(); i++) {
                    sb.append(flagStrings.get(i));
                    if (i != flagStrings.size() - 1) {
                        sb.append("; ");
                    }
                }
                appendAdvertisementItem(builder, getString(R.string.advertise_title_flag), sb.toString() + "\n");
            } else {
                appendAdvertisementItem(builder, getString(R.string.advertise_title_flag),
                        getString(R.string.advertise_content_unknown) + "\n");
            }
            // service UUID
            List<ParcelUuid> serviceUuids = scanRecord.getServiceUuids();
            if (serviceUuids != null && !serviceUuids.isEmpty()) {
                StringBuilder sb = new StringBuilder("\n");
                for (ParcelUuid uuid : serviceUuids) {
                    sb.append(uuid.toString()).append("\n");
                }
                appendAdvertisementItem(builder, getString(R.string.advertise_title_service_uuid), sb.toString());
            } else {
                appendAdvertisementItem(builder, getString(R.string.advertise_title_service_uuid), "null\n");
            }
            // manufacturer data
            SparseArray<byte[]> manufacturerData = scanRecord.getManufacturerSpecificData();
            if (manufacturerData != null && manufacturerData.size() > 0) {
                StringBuilder sb = new StringBuilder("\n");
                for (int i = 0; i < manufacturerData.size(); i++) {
                    byte[] data = manufacturerData.valueAt(i);
                    short manufacturerId = (short) manufacturerData.keyAt(i);
                    String curData = String.format(Locale.US, "%s<0x%04x>\n0x%s\n",
                            getString(R.string.advertise_content_manufacturer_id),
                            (manufacturerId & 0xFFFF), ByteUtils.bytes2HexStr(data));
                    sb.append(curData);
                }
                appendAdvertisementItem(builder, getString(R.string.advertise_title_manufacturer_data), sb.toString());
            } else {
                appendAdvertisementItem(builder, getString(R.string.advertise_title_manufacturer_data), "null\n");
            }
        }
        return builder;
    }

    private void appendAdvertisementItem(SpannableStringBuilder builder,
                                         String title,
                                         String content) {
        int start;
        // title
        start = builder.length();
        builder.append(title);
        builder.setSpan(new ForegroundColorSpan(Color.BLACK),
                start, builder.length(),
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        // content
        start = builder.length();
        builder.append(content);
        builder.setSpan(new ForegroundColorSpan(getResources().getColor(R.color.bright_blue)),
                start, builder.length(),
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    }

    private void setScanFilterEditMode(boolean editable) {
        setEditTextEditable(etName, editable);
        setEditTextEditable(etAddress, editable);
        setEditTextEditable(etServiceUuid, editable);
        if (editable) {
            etName.requestFocus();
            String content = etName.getText().toString().trim();
            etName.setSelection(TextUtils.isEmpty(content) ? 0 : content.length());
        } else {
            etName.clearFocus();
            etAddress.clearFocus();
            etServiceUuid.clearFocus();
        }
        cbFuzzy.setEnabled(editable);
        toggleSoftInput(editable);

        isScanFilterEditing = editable;
    }

    private void setEditTextEditable(EditText editText, boolean enable) {
        editText.setFocusable(enable);
        editText.setFocusableInTouchMode(enable);
        editText.setClickable(enable);
        editText.setCursorVisible(enable);
        editText.setLongClickable(enable);
    }

    private void toggleSoftInput(boolean show) {
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (show) {
            imm.showSoftInput(etName, InputMethodManager.SHOW_IMPLICIT);
        } else {
            imm.hideSoftInputFromWindow(getWindow().getDecorView().getWindowToken(), 0);
        }
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
